package com.amazonaws.breakglass.rds.demo;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class GetMasterSecretName
	implements RequestHandler<CreateBreakglassUserRequest, String> {

    @Override
    public String handleRequest(CreateBreakglassUserRequest input,
	    Context context) {

	GetItemResult item = getLamTable(input);
	InvokeResponse response = invokeMasterSecretValueFunction(item);
	return response.logResult();
    }

    private GetItemResult getLamTable(CreateBreakglassUserRequest input) {
	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	Map<String, AttributeValue> key = new HashMap<>();
	key.put("DataBaseName", new AttributeValue(input.getDataBaseName()));
	GetItemResult item = client.getItem("LAM", key);
	return item;
    }

    private InvokeResponse invokeMasterSecretValueFunction(GetItemResult item) {
	LambdaClient lambdaClient = LambdaClient.builder().build();
	SdkBytes payload = SdkBytes.fromUtf8String("{\"masterSecret\" : \""
		+ item.getItem().get("MasterSecret").getS() + "\"}");
	InvokeRequest request = InvokeRequest.builder()
		.invocationType(InvocationType.EVENT)
		.functionName("GetMasterSecretValueFunction").payload(payload)
		.build();
	InvokeResponse response = lambdaClient.invoke(request);
	return response;
    }

}
