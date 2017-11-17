package uk.co.gavincornwell.cominghome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for helper methods in TriggerAlert lambda function.
 *
 * @author Gavin Cornwell
 */
public class TriggerAlertTest
{
    private TriggerAlert alert;
    
    @Before
    public void init()
    {
        alert = new TriggerAlert();
    }
    
    @Test
    public void testValidPhoneNumber() throws Exception
    {
        // test valid phone number does not throw an exception
        alert.validatePhoneNumber("+441234567890");
    }
    
    @Test
    public void testValidPhoneNumberLong() throws Exception
    {
        // test valid phone number does not throw an exception
        alert.validatePhoneNumber("+441234567890123");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPhoneNumberStart() throws Exception
    {
        // test phone number that doesn't start with +
        alert.validatePhoneNumber("01234567890");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPhoneNumberWithLetters() throws Exception
    {
        // test phone number that contains letters
        alert.validatePhoneNumber("+1234abcdef");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPhoneNumberShort() throws Exception
    {
        // test phone number that is too short
        alert.validatePhoneNumber("+1234");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPhoneNumberSpaces() throws Exception
    {
        // test phone number that is too short
        alert.validatePhoneNumber("+1234 567890");
    }
    
    @Test
    public void testExtractMessage() throws Exception
    {
        // test message is extracted successfully
        String body = "{ \"message\": \"Gav is leaving\"}";
        
        String message = alert.extractMessage(body);
        assertNotNull("Expected message property to be found", message);
        assertEquals("Expected message to be 'Gav is leaving' but was: " + message, "Gav is leaving", message);
    }
    
    @Test
    public void testExtractMessageMissing() throws Exception
    {
        // test missing message property
        String body = "{}";
        
        String message = alert.extractMessage(body);
        assertNull("Did not expected message property to be found", message);
    }
    
    @Test(expected=Exception.class)
    public void testExtractMessageInvalidBody() throws Exception
    {
        // test an invalid JSON body (missing end })
        String body = "{ \"message\": \"Gav is leaving\"";
        alert.extractMessage(body);
    }
}
