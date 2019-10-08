package com.amazonaws.breakglass.rds.demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.pinpointemail.AmazonPinpointEmail;
import com.amazonaws.services.pinpointemail.AmazonPinpointEmailClientBuilder;
import com.amazonaws.services.pinpointemail.model.Body;
import com.amazonaws.services.pinpointemail.model.Content;
import com.amazonaws.services.pinpointemail.model.Destination;
import com.amazonaws.services.pinpointemail.model.EmailContent;
import com.amazonaws.services.pinpointemail.model.Message;
import com.amazonaws.services.pinpointemail.model.SendEmailRequest;

public class SendEmail {
	//These emails need to be verified in Pinpoint.
    static final String FROM = "FROM_EMAIL";
    static final String TO = "TO_EMAIL";
    static final String charset = "UTF-8";
    static final int PORT = 465;

    static final String SUBJECT = "Your break glass password";

    static final String BODY = "You breakglass password is: ";

    public void sendEmail(String password) throws IOException {

	Collection<String> toAddresses = new ArrayList<String>();
	toAddresses.add(TO);
	try {
	    AmazonPinpointEmail client = AmazonPinpointEmailClientBuilder
		    .standard().withRegion("eu-west-1").build();
	    
	    SendEmailRequest request = new SendEmailRequest()
		    .withFromEmailAddress(FROM)
		    .withDestination(
			    new Destination().withToAddresses(toAddresses))
		    .withContent(new EmailContent().withSimple(new Message()
			    .withSubject(new Content().withCharset(charset)
				    .withData(SUBJECT))
			    .withBody(new Body()
				    .withHtml(new Content().withCharset(charset)
					    .withData(BODY + password)))));
	    client.sendEmail(request);
	    System.out.println("Email sent!");
	} catch (Exception ex) {
	    System.out.println(
		    "The email wasn't sent. Error message: " + ex.getMessage());
	}
    }
}