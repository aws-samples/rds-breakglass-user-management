package com.amazonaws.breakglass.rds.demo;

import java.util.Random;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class GetMasterSecretValue
	implements RequestHandler<GetMasterSecretValueRequest, String> {

    @Override
    public String handleRequest(GetMasterSecretValueRequest input,
	    Context context) {

	String masterSecret = input.getMasterSecret();

	SecretsManagerClient client = SecretsManagerClient.builder().build();
	GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest
		.builder().secretId(masterSecret).build();
	GetSecretValueResponse getSecretValueResponse = client
		.getSecretValue(getSecretValueRequest);
	invokeCreateUserFunction(getSecretValueResponse.secretString());

	return createUsername();
    }

    private InvokeResponse invokeCreateUserFunction(String secretString) {
	LambdaClient lambdaClient = LambdaClient.builder().build();
	SdkBytes payload = SdkBytes.fromUtf8String(secretString);
	InvokeRequest invokeRequest = InvokeRequest.builder()
		.invocationType(InvocationType.EVENT)
		.functionName("CreateUserFunction").payload(payload).build();
	InvokeResponse invokeResponse = lambdaClient.invoke(invokeRequest);
	return invokeResponse;
    }

    private String createUsername() {
	return "breakglass-"
		+ String.format("%06d", new Random().nextInt(999999));
    }
}
