package us.feury.martasync;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Lambda function entry point. You can change to use other pojo type or implement
 * a different RequestHandler.
 *
 * @see <a href=https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html>Lambda Java Handler</a> for more information
 */
public class MartaSyncFunction implements RequestHandler<Object, String> {
    
    // Clients
    private final DynamoDbClient dynamoDbClient;
    private final SsmClient ssmClient;
    private final HttpClient httpClient;

    // Parameters
    private final String twitterToken;

    // Logger
    private static final Logger log = LoggerFactory.getLogger(MartaSyncFunction.class);

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
    }

    @Override
    public String handleRequest(final Object input, final Context context) {
        
        log.info("Handler called");
        log.info("Token: ***{}", twitterToken.substring(twitterToken.length()-6));
        
        return callTwitterApi();
    }

    private String callTwitterApi() {
        HttpRequest request;
        try {
            request = DependencyFactory.twitterSearchRequest(this.twitterToken);
        } catch (URISyntaxException e) {
            log.error("Bad API Url", e);
            return "500 Internal Server Error";
        }

        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            log.error("Failed to call Twitter API", e);
            return "504 Bad Gateway";
        }

        if (response.statusCode()==200) {
            log.info("Twitter returned tweets: {}", response.body());
            return "200 OK";
        } else {
            log.error("Something went wrong: {}", response.body());
            return "500 Internal Server Error";
        }
    }
}
