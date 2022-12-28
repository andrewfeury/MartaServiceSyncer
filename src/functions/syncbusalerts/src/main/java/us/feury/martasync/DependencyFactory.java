
package us.feury.martasync;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * The module containing all dependencies required by the {@link MartaSyncFunction}.
 */
public class DependencyFactory {

    // Constants
    private static final String PARMAMETER_BEARER_TOKEN = "/MartaServiceSyncer/TwitterAPI/BearerToken";
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
                    .name(PARMAMETER_BEARER_TOKEN)
                    .build();
    }

    public static HttpRequest twitterSearchRequest(String bearerToken) throws URISyntaxException {
        
        // API path
        // TODO: remember last tweet ID
        final URI uri = new URI(URL_SEARCH_TWEETS);
        final String authHeader = String.format("Bearer %s", bearerToken);
        return HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", authHeader)
                    .timeout(Duration.of(10, ChronoUnit.SECONDS))
                    .GET()
                    .build();
    }
}
