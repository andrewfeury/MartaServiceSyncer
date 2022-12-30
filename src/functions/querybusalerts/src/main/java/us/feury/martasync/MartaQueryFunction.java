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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Lambda function entry point.
 * 
 * @author Andrew Feury
 * @see <a href=https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html>Lambda Java Handler</a>
 * for more information
 */
public class MartaQueryFunction implements RequestHandler<MartaQueryInput, MartaQueryOutput> {
    
    // Constants
    private static final Region REGION = Region.US_EAST_1;
    private static final String DYNAMODB_TABLE_NAME = "ActiveAlerts";
    
    // Clients
    private final DynamoDbClient dynamoDbClient;

    // Logger
    private static final Logger log = LoggerFactory.getLogger(MartaQueryFunction.class);

    public MartaQueryFunction() {
        
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
    }

    @Override
    public MartaQueryOutput handleRequest(final MartaQueryInput input, final Context context) {
        
        Optional<String> route = Optional.ofNullable(input.getRoute());
        if (route.isPresent() && !route.get().isEmpty()) {
            return queryTweetsByRoute(route.get());
        } else {
            return queryTweetsAll();
        }
    }

    private MartaQueryOutput queryTweetsByRoute(String route) {

        // Build a query for the route with created time descending
        Map<String,AttributeValue> queryAttribute = new HashMap<>();
        queryAttribute.put(":route", AttributeValue.fromS(route));
        QueryRequest query = 
                QueryRequest.builder()
                            .tableName(DYNAMODB_TABLE_NAME)
                            .keyConditionExpression("Route = :route")
                            .expressionAttributeValues(queryAttribute)
                            .scanIndexForward(false)
                            .build();
        QueryResponse response = this.dynamoDbClient.query(query);

        // Parse response
        final MartaQueryOutput result = new MartaQueryOutput();
        response.items().forEach(m->parseToResult(m,result));

        // Ensure an empty route is added to the response if no alerts are present
        result.getTweetsByRoute().putIfAbsent(route, new MartaServiceTweet());

        return result;
    }

    private void parseToResult(Map<String, AttributeValue> itemData, MartaQueryOutput result) {
        
        String route =
                Optional.ofNullable(itemData.get("Route"))
                        .map(AttributeValue::s)
                        .orElse("Unknown");

        String created =
                Optional.ofNullable(itemData.get("Created"))
                        .map(AttributeValue::n)
                        .map(Long::decode)
                        .map(l->Instant.ofEpochSecond(l).toString())
                        .orElse("");

        String text =
                Optional.ofNullable(itemData.get("Text"))
                        .map(AttributeValue::s)
                        .orElse("");

        result.putTweet(
                    route,
                    created,
                    text
                );
    }

    private MartaQueryOutput queryTweetsAll() {
        
        // Build a scan (all-item query) with created time descending
        final ScanRequest scan =
                ScanRequest.builder()
                           .tableName(DYNAMODB_TABLE_NAME)
                           .build();
        ScanResponse response = this.dynamoDbClient.scan(scan);

        // Parse response
        final MartaQueryOutput result = new MartaQueryOutput();
        response.items().forEach(m->parseToResult(m,result));
        return result;          
    }
}
