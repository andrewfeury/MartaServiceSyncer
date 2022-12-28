package us.feury.martasync;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.ZonedDateTime;
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

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import us.feury.martasync.api.TwitterSearchData;
import us.feury.martasync.api.TwitterSearchResponse;

/**
 * Lambda function entry point. You can change to use other pojo type or implement
 * a different RequestHandler.
 *
 * @see <a href=https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html>Lambda Java Handler</a> for more information
 */
public class MartaSyncFunction implements RequestHandler<Object, String> {
    
    // Constants
    private static final Pattern PATTERN_ROUTE = Pattern.compile("(?<=Route )\\w+(?=:)");

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
     * Initalize & warm-up the necessary SDKs
     */
    public MartaSyncFunction() {

        this.dynamoDbClient = DependencyFactory.dynamoDbClient();
        this.ssmClient = DependencyFactory.ssmClient();
        
        // Consider invoking a simple api here to pre-warm up the application, eg: dynamodb#listTables
        dynamoDbClient.listTables();

        // Fetch the API bearer token from the parameter store
        GetParameterResponse response = ssmClient.getParameter(DependencyFactory.tokenRequest());
        this.twitterToken = response.parameter().value();

        // Initialize HttpClient to call Twitter API
        this.httpClient = HttpClient.newHttpClient();

        // Initialize Jackson ObjectMapper
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String handleRequest(final Object input, final Context context) {
        
        log.info("Handler called");
        
        // Get latest tweet to avoid duplication
        Optional<String> latestTweetId;
        try {
            GetParameterResponse response = ssmClient.getParameter(DependencyFactory.lastTweetIdRequest());
            latestTweetId = Optional.of(response.parameter().value());
        } catch (ParameterNotFoundException e) {
            log.warn("Last tweet parameter not found", e);
            latestTweetId = Optional.empty();
        }
        
        TwitterSearchResponse tweets;
        try {
            tweets = callTwitterApi(latestTweetId);
        } catch (TwitterApiException e) {
            log.error("Twitter API failure", e);
            return "504 Bad Gateway";
        }

        log.info("Found tweets: {}", tweets);
        tweets.getData().forEach(this::PersistTweet);

        // Store latest tweet
        String latestTweetFound = tweets.getMeta().getNewestTweetId();
        log.info("Storing latest tweet: {}", latestTweetFound);
        ssmClient.putParameter(DependencyFactory.lastTweetIdUpdate(latestTweetFound));

        return "200 OK";
    }

    private void PersistTweet(TwitterSearchData tweetData) {
        Map<String, AttributeValue> attributesMap = new HashMap<>();

        String tweetContent = tweetData.getText();
        ZonedDateTime tweetCreated = tweetData.getCreatedAt();
        String tweetId = tweetData.getId();

        if (tweetContent==null || tweetCreated==null || tweetId==null) {
            log.warn("Skipping malformed tweet data: {}", tweetData);
            return;
        }

        // Route
        Matcher routeMatcher = PATTERN_ROUTE.matcher(tweetContent);
        if (!routeMatcher.find()) {
            log.warn("Skipping tweet of undiscernible route: {}", tweetData);
            return;
        }
        String route = routeMatcher.group();
        attributesMap.put("Route", AttributeValue.builder().s(route).build());

        // Text
        String text = String.join("", 
                            tweetContent.split(String.format("Route %s: ", route)))
                            .replace("\\n", " ");
        attributesMap.put("Text", AttributeValue.builder().s(text).build());
        
        // Created
        long unixEpochTime = tweetCreated.toEpochSecond();
        attributesMap.put("Created", AttributeValue.builder().n(String.valueOf(unixEpochTime)).build());

        // Expires
        long expirationTime = tweetCreated.plusDays(1).toEpochSecond();
        attributesMap.put("Expires", AttributeValue.builder().n(String.valueOf(expirationTime)).build());

        // Persist
        log.info("Putting item in DynamoDB: {}", attributesMap);
        PutItemResponse response = this.dynamoDbClient.putItem(DependencyFactory.dynamoPutRequest(attributesMap));
        log.info("PutItem result: {}", response);
    }

    private TwitterSearchResponse callTwitterApi(Optional<String> lastTweetId) throws TwitterApiException {
        HttpRequest request;
        try {
            request = DependencyFactory.twitterSearchRequest(this.twitterToken, lastTweetId);
        } catch (URISyntaxException e) {
            log.error("Bad API Url", e);
            throw new TwitterApiException("Bad API Url", e);
        }

        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new TwitterApiException("Failed to call Twitter API", e);
        }

        if (response.statusCode()!=200) {
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
