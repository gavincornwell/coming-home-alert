package uk.co.gavincornwell.cominghome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.aws.lambda.model.LambdaProxyRequest;
import org.alfresco.aws.lambda.model.LambdaProxyResponse;
import org.alfresco.aws.lambda.utils.OfflineLambdaContext;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration test that executes the Lambda handler.
 *
 * @author Gavin Cornwell
 */
public class TriggerAlertIT
{
    @BeforeClass
    public static void setup() throws Exception
    {
        // create dynamo table
    }
    
    @Test
    public void testTriggerHandler() throws Exception
    {
        // define the message to send
        String requestBody = "{ \"message\": \"JUnit Test\"}";
        
        // call the lambda function
        LambdaProxyRequest request = new LambdaProxyRequest();
        request.setBody(requestBody);
        Map<String, String> pathParams = new HashMap<String, String>();
        pathParams.put("userId", "userId");
        request.setPathParameters(pathParams);
        TriggerAlert alert = new TriggerAlert();
        LambdaProxyResponse response = alert.handleRequest(request, new OfflineLambdaContext());
        
        // make sure the function returned a status code of 202
        Assert.assertEquals("Expected status code of 202 but was: " + response.getStatusCode(), 
                    202, response.getStatusCode());
        
        // extract the message id from the response and check it's not null
        String responseBody = response.getBody();
        String messageId = null;
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readValue(responseBody, JsonNode.class);
        assertTrue("Expected messageId property to be present", jsonNode.has(TriggerAlert.PROPERTY_MESSAGE_ID));
        messageId = jsonNode.get(TriggerAlert.PROPERTY_MESSAGE_ID).asText();
        assertFalse("Expected messageId property to be populated", messageId.isEmpty());
        
        // TOOD: make sure the corresponding entry is in the dynamo table
    }
    
    @AfterClass
    public static void tearDown() throws Exception
    {
        // delete dynamo table
    }
}
