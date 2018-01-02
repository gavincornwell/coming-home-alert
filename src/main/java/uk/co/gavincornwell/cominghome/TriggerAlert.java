
package uk.co.gavincornwell.cominghome;

import java.util.Map;

import org.alfresco.aws.lambda.model.LambdaProxyRequest;
import org.alfresco.aws.lambda.model.LambdaProxyResponse;
import org.alfresco.aws.lambda.utils.Logger;
import org.apache.http.HttpStatus;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lambda function to handle the triggering of an alert. Expects the
 * following JSON body: 
 * 
 * <pre>
 * {
 *   "message": "[message text to send]"
 * }
 * </pre>
 * 
 * @author Gavin Cornwell
 */
public class TriggerAlert implements RequestHandler<LambdaProxyRequest, LambdaProxyResponse>
{
    public static final String VALID_USER_ID = "VALID_USER_ID";
    public static final String TOPIC_ARN = "TOPIC_ARN";
    
    protected static final String PROPERTY_MESSAGE = "message";
    protected static final String PROPERTY_MESSAGE_ID = "messageId";
    
    @Override
    public LambdaProxyResponse handleRequest(LambdaProxyRequest request, Context context)
    {
        Logger.logDebug(String.format("received request: %s", request), context);
        
        LambdaProxyResponse response = new LambdaProxyResponse();

        try
        {
            String topicArn = System.getenv(TOPIC_ARN);
            Logger.logDebug(String.format("topicArn: %s", topicArn), context);
            if (topicArn == null || topicArn.isEmpty())
            {
                throw new IllegalArgumentException("TOPIC_ARN environment variable is required.");
            }
            
            String validUserId = System.getenv(VALID_USER_ID);
            Logger.logDebug(String.format("validUserId: %s", validUserId), context);
            if (validUserId == null || validUserId.isEmpty())
            {
                throw new IllegalArgumentException("VALID_USER_ID environment variable is required.");
            }
            
            // grab the provided userId from the path parameters
            Map<String, String> pathParams = request.getPathParameters();
            Logger.logDebug("pathParams: " + pathParams, context);
            String providedUserId = null;
            if (pathParams != null)
            {
                providedUserId = pathParams.get("userId");
                Logger.logDebug(String.format("providedUserId: %s", providedUserId), context);
            }
            
            // check provided and valid user id match
            if (providedUserId == null || !validUserId.equals(providedUserId))
            {
                throw new IllegalStateException("Provided user ID is not valid");
            }
            
            // extract the message from the request
            String bodyString = request.getBody();
            Logger.logDebug(String.format("bodyString: %s", bodyString), context);
            
            String message = extractMessage(bodyString);
            Logger.logDebug(String.format("message: %s", message), context);
            if (message == null || message.isEmpty())
            {
                throw new IllegalArgumentException("message property in body is required.");
            }

            // send the message to the topic
            String messageId = sendMessage(topicArn, message);
            Logger.logInfo(String.format("Message with id '%s' sent", messageId), context);

            response.setBody(String.format("{ \"%s\": \"%s\"}", PROPERTY_MESSAGE_ID, messageId));
            response.setStatusCode(HttpStatus.SC_ACCEPTED);
            Logger.logDebug(String.format("returning response: %s", response), context);
        }
        catch (IllegalArgumentException iae)
        {
            Logger.logError(iae, context);

            // return error response
            handleError(iae, HttpStatus.SC_BAD_REQUEST, response);
        }
        catch (IllegalStateException ise)
        {
            Logger.logError(ise, context);

            // return error response
            handleError(ise, HttpStatus.SC_UNAUTHORIZED, response);
        }
        catch (Exception e)
        {
            Logger.logError(e, context);

            // return error response
            handleError(e, HttpStatus.SC_INTERNAL_SERVER_ERROR, response);
        }

        return response;
    }
    
    /**
     * Extracts the message to send from the body string
     * 
     * @param body String representing the JSON body of the request
     * @return The message
     * @throws Exception if JSON parsing fails
     */
    protected String extractMessage(String body) throws Exception
    {
        String message = null;
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readValue(body, JsonNode.class);
        
        if (jsonNode.has(PROPERTY_MESSAGE))
        {
            message = jsonNode.get(PROPERTY_MESSAGE).asText();
        }
        
        return message;
    }
    
    /**
     * Sends the given message to the given phone number.
     * 
     * @param topicArn The ARN of the topic to publish the message to
     * @param message The message to send
     * @return The ID of the SMS sent
     * @throws Exception if the sending of the SMS fails
     */
    protected String sendMessage(String topicArn, String message) throws Exception
    {
        AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();
        
        // send the SMS message
        PublishResult result = snsClient.publish(topicArn, message);
        
        // return the generated message ID
        return result.getMessageId();
    }
    
    /**
     * Sets up the error response.
     * 
     * @param error The error code to return
     * @param errorCode The error
     * @param response The response object to setup
     */
    protected void handleError(Exception error, int errorCode, LambdaProxyResponse response)
    {
        response.setStatusCode(errorCode);
        response.setBody(String.format("{ \"error\": \"%s\"}", error.getMessage()));
    }
}
