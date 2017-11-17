
package uk.co.gavincornwell.cominghome;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.aws.lambda.model.LambdaProxyRequest;
import org.alfresco.aws.lambda.model.LambdaProxyResponse;
import org.alfresco.aws.lambda.utils.Logger;
import org.apache.http.HttpStatus;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lambda function to handle the triggering of coming home alerts. Expects the
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
    public static final String PHONE_NUMBER = "PHONE_NUMBER";
    public static final String TABLE_NAME = "TABLE_NAME";
    
    protected static final String PROPERTY_MESSAGE = "message";
    protected static final String PROPERTY_MESSAGE_ID = "messageId";
    
    @Override
    public LambdaProxyResponse handleRequest(LambdaProxyRequest request, Context context)
    {
        // TODO: set log level i.e. add Logger.setLogLevel();
        
        // TODO: dump the full request we received (add a toString to request/response objects in library)
        
        LambdaProxyResponse response = new LambdaProxyResponse();

        try
        {
            String phoneNumber = System.getenv(PHONE_NUMBER);
            Logger.logDebug(String.format("phoneNumber: %s", phoneNumber), context); 
            if (phoneNumber == null || phoneNumber.isEmpty())
            {
                throw new IllegalArgumentException("PHONE_NUMBER environment variable is required.");
            }
            
            // validate the phone number
            validatePhoneNumber(phoneNumber);
            
            String tableName = System.getenv(TABLE_NAME);
            Logger.logDebug(String.format("tableName: %s", tableName), context);
            if (tableName == null || tableName.isEmpty())
            {
                throw new IllegalArgumentException("TABLE_NAME environment variable is required.");
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

            // send the message via SMS
            String messageId = sendMessage(phoneNumber, message);
            Logger.logInfo(String.format("Sent '%s' to '%s' with id: %s", message, phoneNumber, messageId), context);

            // save the trigger to dynamo table
            persistTrigger(tableName, message, messageId);

            response.setBody(String.format("{ \"%s\": \"%s\"}", PROPERTY_MESSAGE_ID, messageId));
            response.setStatusCode(HttpStatus.SC_ACCEPTED);
        }
        catch (IllegalArgumentException iae)
        {
            Logger.logError(iae, context);

            // return error response
            response.setStatusCode(400);
            response.setBody(String.format("{ \"error\": \"%s\"}", iae.getMessage()));
        }
        catch (Exception e)
        {
            // TODO: consider using DLQ to capture unexpected errors

            Logger.logError(e, context);

            // return error response
            response.setStatusCode(500);
            response.setBody(String.format("{ \"error\": \"%s\"}", e.getMessage()));
        }

        return response;
    }
    
    /**
     * Validates the given phone number passes the following set of rules:
     * 
     * <li>Starts with a +</li>
     * <li>Contains at least 12 digits</li>
     * <br/>
     * For example: +441234567890
     * <br/><br/>
     * 
     * @param phoneNumber The phone number to validate
     * @throws IllegalArgumentException if any of the rule checks fail
     */
    protected void validatePhoneNumber(String phoneNumber) throws IllegalArgumentException
    {
        // validate phone number using regular expression
        String pattern = "^\\+\\d{12,}";
        
        if (!phoneNumber.matches(pattern))
        {
            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        }
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
     * @param phoneNumber The phone number to send the message to in international format i.e. +44
     * @param message The message to send
     * @return The ID of the SMS sent
     * @throws Exception if the sending of the SMS fails
     */
    protected String sendMessage(String phoneNumber, String message) throws Exception
    {
        AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();
        
        Map<String, MessageAttributeValue> smsAttributes = new HashMap<String, MessageAttributeValue>();
        // set he sender ID shown on the device
        smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                    .withStringValue("ComingHome").withDataType("String"));
        // sets the max price to 0.50 USD
        smsAttributes.put("AWS.SNS.SMS.MaxPrice",
                    new MessageAttributeValue().withStringValue("0.25").withDataType("Number"));
        // sets the type to promotional
        smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                    .withStringValue("Promotional").withDataType("String"));

        // send the SMS message
        PublishResult result = snsClient.publish(new PublishRequest().withMessage(message)
                    .withPhoneNumber(phoneNumber).withMessageAttributes(smsAttributes));
        
        // return the generated message ID
        return result.getMessageId();
    }
    
    protected void persistTrigger(String tableName, String message, String messageId) throws Exception
    {
        // TODO: save the data to the dynamo table
    }
}
