package com.amazonaws.breakglass.rds.demo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class CleanUpBreakglass
	implements RequestHandler<CreateBreakglassUserRequest, String> {

    private static final String DROP_USER_QUERY = "DROP USER IF EXISTS ?";

    @Override
    public String handleRequest(CreateBreakglassUserRequest input,
	    Context context) {
	LambdaLogger logger = context.getLogger();
	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	DynamoDB dynamoDB = new DynamoDB(client);
	Table table = dynamoDB.getTable("LAM");
	PrimaryKey key = new PrimaryKey().addComponent("DataBaseName",
		input.getDataBaseName());
	Item item = table.getItem(key);
	Map<String, Object> lamDataBase = item.asMap();
	String masterSecretName = (String) lamDataBase.get("MasterSecret");
	@SuppressWarnings("unchecked")
	Map<String, Boolean> childrenSecret = (Map<String, Boolean>) lamDataBase
		.get("ChildrenSecret");

	List<String> breakglassUsers = new ArrayList<>();

	childrenSecret.entrySet().stream().forEach(entry -> {
	    if (entry.getValue()) {
		breakglassUsers.add(entry.getKey());
	    }
	});

	SecretsManagerClient secretsManagerClient = SecretsManagerClient
		.builder().build();
	GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest
		.builder().secretId(masterSecretName).build();
	GetSecretValueResponse getSecretValueResponse = secretsManagerClient
		.getSecretValue(getSecretValueRequest);
	String masterSecretValue = getSecretValueResponse.secretString();
	Secret masterSecret = null;
	try {
	    masterSecret = new ObjectMapper().readValue(masterSecretValue,
		    Secret.class);
	} catch (IOException e) {
	    logger.log(e.getMessage());
	}

	String connectionString = getConnectionString(masterSecret);
	deleteUserInDB(masterSecret, logger, connectionString,
		breakglassUsers);

	breakglassUsers.stream().forEach(
		secret -> deleteSecretInSM(secretsManagerClient, secret));

	updateEntryInDynamo(masterSecret, table, breakglassUsers);
	
	logger.log("List of users deleted: " + breakglassUsers);

	return "List of users deleted: " + breakglassUsers;
    }

    private String getConnectionString(Secret secret) {
	return "jdbc:mysql://" + secret.getHost() + ":" + secret.getPort() + "/"
		+ secret.getDbname();
    }

    private void deleteUserInDB(Secret secret, LambdaLogger logger,
	    String connectionString, List<String> breakglassUsers) {
	Connection connection = null;
	PreparedStatement deleteUserStatement = null;
	try {
	    Class.forName("com.mysql.jdbc.Driver");
	    connection = DriverManager.getConnection(connectionString,
		    secret.getUsername(), secret.getPassword());
	    deleteUserStatement = connection.prepareStatement(DROP_USER_QUERY);

	    for (String user : breakglassUsers) {
		try {
		    deleteUserStatement.setString(1,
			    user.substring(secret.getDbname().length() + 1));
		    deleteUserStatement.execute();
		} catch (SQLException e) {
		    logger.log(e.getMessage());
		}
	    }

	} catch (SQLException | ClassNotFoundException e) {
	    logger.log(e.getMessage());
	} finally {
	    try {
		deleteUserStatement.close();
		connection.close();
	    } catch (SQLException e) {
		logger.log(e.getMessage());
	    }
	}
    }

    private void deleteSecretInSM(SecretsManagerClient secretsManagerClient,
	    String secret) {
	DeleteSecretRequest request = DeleteSecretRequest.builder()
		.forceDeleteWithoutRecovery(true).secretId(secret).build();
	secretsManagerClient.deleteSecret(request);
    }

    private void updateEntryInDynamo(Secret secret, Table lam,
	    List<String> breakglassUsers) {
	Map<String, Boolean> childrenSecretMap = lam.getItem(new GetItemSpec()
		.withPrimaryKey("DataBaseName", secret.getDbname()))
		.getMap("ChildrenSecret");
	breakglassUsers.stream()
		.forEach(user -> childrenSecretMap.remove(user));
	UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		.withPrimaryKey("DataBaseName", secret.getDbname())
		.withUpdateExpression("set ChildrenSecret = :ChildrenSecret")
		.withValueMap(new ValueMap().withMap(":ChildrenSecret",
			childrenSecretMap))
		.withReturnValues(ReturnValue.UPDATED_NEW);
	lam.updateItem(updateItemSpec);
    }
}
