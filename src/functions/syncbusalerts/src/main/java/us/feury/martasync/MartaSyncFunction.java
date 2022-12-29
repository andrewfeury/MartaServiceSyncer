/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package us.feury.martasync;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import us.feury.martasync.api.TwitterApiException;
import us.feury.martasync.api.TwitterSearchData;
import us.feury.martasync.api.TwitterSearchResponse;

/**
 * Lambda function entry point. We don't care about the inputs & outputs since the only
 * action we need to do is update from the Twitter search API.
 * 
 * @author Andrew Feury
 * @see <a href=https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html>Lambda Java Handler</a>
 * for more information
 */
public class MartaSyncFunction implements RequestHandler<Object, Integer> {
    
    // Constants
    private static final Pattern PATTERN_ROUTE = Pattern.compile("(?<=Route )\\w+(?=:)");
    private static final String PARAMETER_BEARER_TOKEN = "/MartaServiceSyncer/TwitterAPI/BearerToken";
    private static final String PARAMETER_LAST_TWEET = "/MartaServiceSyncer/TwitterAPI/LastTweetId";
    private static final Region REGION = Region.US_EAST_1;
    private static final String DYNAMODB_TABLE_NAME = "ActiveAlerts";
    private static final String URL_SEARCH_TWEETS = "https://api.twitter.com/2/tweets/search/recent?query=from%3AMARTAservice+route&sort_order=recency&tweet.fields=created_at";

    // Clients
    private final DynamoDbClient dynamoDbClient;
    private final SsmClient ssmClient;
    private final HttpClient httpClient;

    // Parameters
    private final String twitterToken;

    // Logger
    private static final Logger log = LoggerFactory.getLogger(MartaSyncFunction.class);

    // Deserializer
    private final ObjectMapper mapper;

    /**
     * Constructor handles necessary initialization & warm-up actions
     */
    public MartaSyncFunction() {

        // Init DynamoDB client & verify table exists
        this.dynamoDbClient = 
                DynamoDbClient.builder()
                              .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                              .region(REGION)
                              .httpClientBuilder(UrlConnectionHttpClient.builder())
                              .build();
        TableDescription table = 
                this.dynamoDbClient.describeTable(
                                        DescribeTableRequest.builder()
                                                            .tableName(DYNAMODB_TABLE_NAME)
                                                            .build()
                                    ).table();
        if (log.isDebugEnabled()) log.debug("Table found: {}", table.tableId());
        
        // Init SSM Client & connect to Parameter Store to fetch the bearer token
        this.ssmClient = 
                SsmClient.builder()
                         .region(REGION)
                         .httpClientBuilder(UrlConnectionHttpClient.builder())
                         .build();
        this.twitterToken = 
                this.ssmClient.getParameter(
                                        GetParameterRequest.builder()
                                                           .name(PARAMETER_BEARER_TOKEN)
                                                           .build()
                                ).parameter().value();
        if (log.isDebugEnabled()) log.debug("Token found: {}...", this.twitterToken.substring(0, 8));

        // Initialize HttpClient to call Twitter API
        this.httpClient = HttpClient.newHttpClient();

        // Initialize Jackson ObjectMapper w/ Jaya 8+ time support
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public Integer handleRequest(final Object input, final Context context) {
        
        // Get latest tweet to avoid duplication
        Optional<String> latestTweetId;
        try {
            GetParameterResponse response = 
                    ssmClient.getParameter(GetParameterRequest.builder()
                                                              .name(PARAMETER_LAST_TWEET)
                                                              .build());
            latestTweetId = Optional.of(response.parameter().value());
        } catch (ParameterNotFoundException e) {
            // This is ok, we'll create it later
            log.info("Last tweet parameter not found", e);
            latestTweetId = Optional.empty();
        }
        
        // Call the Twitter API to search for latest Marta alerts
        TwitterSearchResponse tweets;
        try {
            tweets = callTwitterApi(latestTweetId);
        } catch (TwitterApiException e) {
            log.error("Twitter API failure", e);
            return HttpStatusCode.BAD_GATEWAY;
        }
        log.info("Found {} tweets to process", tweets.getMeta().getResultCount());

        // Iterate and persist every Tweet
        tweets.getData().forEach(this::PersistTweet);

        // Store latest tweet to avoid duplication the next time we run
        if (tweets.getMeta().getResultCount()>0) {
            String latestTweetFound = tweets.getMeta().getNewestTweetId();
            if (log.isDebugEnabled()) log.debug("Storing latest tweet: {}", latestTweetFound);

            ssmClient.putParameter(PutParameterRequest.builder()
                                                      .name(PARAMETER_LAST_TWEET)
                                                      .value(latestTweetFound)
                                                      .overwrite(true)
                                                      .type(ParameterType.STRING)
                                                      .build()
                                    );
        }

        return HttpStatusCode.OK;
    }

    private void PersistTweet(TwitterSearchData tweetData) {
        Map<String, AttributeValue> attributesMap = new HashMap<>();        
        
        // Verify expected attributes are present in the response
        if (!tweetData.validate()) {
            log.warn("Skipping malformed tweet data: {}", tweetData);
            return;
        }

        // Route
        Matcher routeMatcher = PATTERN_ROUTE.matcher(tweetData.getText());
        if (!routeMatcher.find()) {
            log.warn("Skipping tweet of undiscernible route: {}", tweetData);
            return;
        }
        String route = routeMatcher.group();
        attributesMap.put("Route", AttributeValue.builder().s(route).build());

        // Text
        String text = String.join("", 
                                tweetData.getText().split(
                                            String.format("Route %s: ", route))
                            ).replace("\\n", " ")
                             .trim();
        attributesMap.put("Text", AttributeValue.builder().s(text).build());
        
        // Created
        long unixEpochTime = tweetData.getCreatedAt().toEpochSecond();
        attributesMap.put("Created", AttributeValue.builder().n(String.valueOf(unixEpochTime)).build());

        // Expires
        long expirationTime = tweetData.getCreatedAt().plusDays(1).toEpochSecond();
        attributesMap.put("Expires", AttributeValue.builder().n(String.valueOf(expirationTime)).build());

        // Persist
        log.info("Sending to DynamoDB: {}", attributesMap);
        PutItemResponse response = 
                this.dynamoDbClient.putItem(PutItemRequest.builder()
                                                          .tableName(DYNAMODB_TABLE_NAME)
                                                          .item(attributesMap)
                                                          .build());
        if (log.isDebugEnabled()) log.debug("PutItem result: {}", response);
    }

    private TwitterSearchResponse callTwitterApi(Optional<String> lastTweetId) throws TwitterApiException {
        
        // API path
        URI uri;
        try {
            String apiPath;
            if (lastTweetId.isPresent()) {
                apiPath = String.format("%s&since_id=%s", URL_SEARCH_TWEETS, lastTweetId.get());
            } else {
                apiPath = URL_SEARCH_TWEETS;
            }
            uri = new URI(apiPath);
        } catch (URISyntaxException e) {
            throw new TwitterApiException("Bad API Url", e);
        }

        // Bearer token
        final String authHeader = String.format("Bearer %s", this.twitterToken);

        // Call API
        HttpRequest request = 
                HttpRequest.newBuilder()
                           .uri(uri)
                           .header("Authorization", authHeader)
                           .timeout(Duration.of(10, ChronoUnit.SECONDS))
                           .GET()
                           .build();

        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new TwitterApiException("Failed to call Twitter API", e);
        }

        if (response.statusCode()!=HttpStatusCode.OK) {
            throw new TwitterApiException("Something went wrong: " + response.body());
        }

        // Parse the result
        TwitterSearchResponse parsed;
        try {
            parsed = this.mapper.readValue(response.body(), TwitterSearchResponse.class);
        } catch (JsonProcessingException e) {
            throw new TwitterApiException("Bad API response", e);
        }

        return parsed;
    }
}
