
package us.feury.martasync;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

/**
 * The module containing all dependencies required by the {@link MartaSyncFunction}.
 */
public class DependencyFactory {

    // Constants
    private static final String DYNAMODB_TABLE_NAME = "ActiveAlerts";
    private static final String PARAMETER_BEARER_TOKEN = "/MartaServiceSyncer/TwitterAPI/BearerToken";
    private static final String PARAMETER_LAST_TWEET = "/MartaServiceSyncer/TwitterAPI/LastTweetId";
    private static final String URL_SEARCH_TWEETS = "https://api.twitter.com/2/tweets/search/recent?query=from%3AMARTAservice+route&sort_order=recency&tweet.fields=created_at";
    private static final Region REGION = Region.US_EAST_1;

    private DependencyFactory() {}

    public static DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(REGION)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();
}

    public static SsmClient ssmClient() {
        return SsmClient.builder()
                    .region(REGION)
                    .build();
    }

    public static GetParameterRequest tokenRequest() {
        return GetParameterRequest.builder()
                    .name(PARAMETER_BEARER_TOKEN)
                    .build();
    }

    public static GetParameterRequest lastTweetIdRequest() {
        return GetParameterRequest.builder()
                    .name(PARAMETER_LAST_TWEET)
                    .build();
    }

    public static PutParameterRequest lastTweetIdUpdate(String lastTweetId) {
        return PutParameterRequest.builder()
                    .name(PARAMETER_LAST_TWEET)
                    .value(lastTweetId)
                    .overwrite(true)
                    .type(ParameterType.STRING)
                    .build();
    }

    public static HttpRequest twitterSearchRequest(String bearerToken, Optional<String> lastTweetId) throws URISyntaxException {
        
        // API path
        String apiPath;
        if (lastTweetId.isPresent()) {
            apiPath = String.format("%s&since_id=%s", URL_SEARCH_TWEETS, lastTweetId.get());
        } else {
            apiPath = URL_SEARCH_TWEETS;
        }
        final URI uri = new URI(apiPath);

        // Bearer token
        final String authHeader = String.format("Bearer %s", bearerToken);

        // Build request object
        return HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", authHeader)
                    .timeout(Duration.of(10, ChronoUnit.SECONDS))
                    .GET()
                    .build();
    }

    public static PutItemRequest dynamoPutRequest(Map<String,AttributeValue> itemData) {
        return PutItemRequest.builder()
                    .tableName(DYNAMODB_TABLE_NAME)
                    .item(itemData)
                    .build();
    }
}
