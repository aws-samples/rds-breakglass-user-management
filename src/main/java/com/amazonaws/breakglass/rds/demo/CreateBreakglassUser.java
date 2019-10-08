package com.amazonaws.breakglass.rds.demo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class CreateBreakglassUser
	implements RequestHandler<CreateBreakglassUserRequest, String> {

    private static final String CREATE_USER_QUERY = "CREATE USER ? IDENTIFIED BY ?;";

    @Override
    public String handleRequest(CreateBreakglassUserRequest input,
	    Context context) {
	context.getLogger().log("Input: " + input);
	GetItemResult item = getLamTable(input);
	String masterSecretName = item.getItem().get("MasterSecret").getS();
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
	    context.getLogger().log(e.getMessage());
	}
	String connectionString = getConnectionString(masterSecret);
	String password = createPassword(secretsManagerClient);
	String username = createUsername();
	createUserInDB(masterSecret, context, connectionString, password,
		username);
	context.getLogger().log("created user in DB");
	createSecretInSM(masterSecret, secretsManagerClient, password,
		username);
	updateEntryInDynamo(masterSecret, username);
	context.getLogger().log("Finished all processing");
	try {
	    new SendEmail().sendEmail(password);
	} catch (IOException e) {
	    return "Error emailing password : "+ e.getMessage();
	}
	return "username: "+ username;
    }

    private GetItemResult getLamTable(CreateBreakglassUserRequest input) {
	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	Map<String, AttributeValue> key = new HashMap<>();
	key.put("DataBaseName", new AttributeValue(input.getDataBaseName()));
	GetItemResult item = client.getItem("LAM", key);
	return item;
    }

    private String getConnectionString(Secret secret) {
	return "jdbc:mysql://" + secret.getHost() + ":" + secret.getPort() + "/"
		+ secret.getDbname();
    }

    private String createUsername() {
	return "breakglass-"
		+ String.format("%06d", new Random().nextInt(999999));
    }

    private String createPassword(SecretsManagerClient secretsManagerClient) {
	return secretsManagerClient.getRandomPassword().randomPassword();
    }

    private void createUserInDB(Secret secret, Context context,
	    String connectionString, String password, String username) {
	Connection connection = null;
	PreparedStatement createUserStatement = null;
	PreparedStatement grantStatement = null;
	try {
	    Class.forName("com.mysql.jdbc.Driver");
	    connection = DriverManager.getConnection(connectionString,
		    secret.getUsername(), secret.getPassword());
	    createUserStatement = connection
		    .prepareStatement(CREATE_USER_QUERY);

	    createUserStatement.setString(1, username);
	    createUserStatement.setString(2, password);
	    createUserStatement.execute();
	    //This should be modified according to the privileges that need to be granted
	    grantStatement = connection
		    .prepareStatement("GRANT ALL PRIVILEGES ON "
			    + secret.getDbname() + ".* TO ?;");
	    grantStatement.setString(1, username);
	    grantStatement.execute();

	} catch (SQLException | ClassNotFoundException e) {
	    context.getLogger().log(e.getMessage());
	} finally {
	    try {
		grantStatement.close();
		createUserStatement.close();
		connection.close();
	    } catch (SQLException e) {
		context.getLogger().log(e.getMessage());
	    }
	}
    }

    private void createSecretInSM(Secret secret,
	    SecretsManagerClient secretsManagerClient, String password,
	    String username) {
	CreateSecretRequest request = CreateSecretRequest.builder()
		.name(secret.getDbname() + "-" + username)
		.secretString(getSecretString(secret, username, password))
		.build();
	secretsManagerClient.createSecret(request);
    }

    private void updateEntryInDynamo(Secret secret, String username) {
	AmazonDynamoDB dynamoClient = AmazonDynamoDBClientBuilder.standard()
		.build();
	DynamoDB dynamoDB = new DynamoDB(dynamoClient);
	Table lam = dynamoDB.getTable("LAM");
	Map<String, Boolean> childrenSecretMap = lam.getItem(new GetItemSpec()
		.withPrimaryKey("DataBaseName", secret.getDbname()))
		.getMap("ChildrenSecret");
	childrenSecretMap.put(secret.getDbname() + "-" + username, true);
	UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		.withPrimaryKey("DataBaseName", secret.getDbname())
		.withUpdateExpression("set ChildrenSecret = :ChildrenSecret")
		.withValueMap(new ValueMap().withMap(":ChildrenSecret",
			childrenSecretMap))
		.withReturnValues(ReturnValue.UPDATED_NEW);
	lam.updateItem(updateItemSpec);
    }

    private String getSecretString(Secret secret, String username,
	    String password) {
	return "{\"username\": \"" + username + "\", \"engine\": \""
		+ secret.getEngine() + "\", \"dbname\": \"" + secret.getDbname()
		+ "\", \"host\": \"" + secret.getHost() + "\", \"password\": \""
		+ password + "\", \"port\": " + secret.getPort()
		+ ", \"dbInstanceIdentifier\": \""
		+ secret.getDbInstanceIdentifier() + "\"}";
    }
}
