package com.amazonaws.breakglass.rds.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Random;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

public class CreateUser implements RequestHandler<Secret, String> {

    private static final String CREATE_USER_QUERY = "CREATE USER ? IDENTIFIED BY ?;";

    @Override
    public String handleRequest(Secret secret, Context context) {

	String connectionString = getConnectionString(secret);
	SecretsManagerClient secretsManagerClient = SecretsManagerClient
		.builder().build();
	String password = createPassword(secretsManagerClient);
	String username = createUsername();
	createUserInDB(secret, context, connectionString, password, username);
	createSecretInSM(secret, secretsManagerClient, password, username);
	updateEntryInDynamo(secret, username);

	return username;
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

    private void createSecretInSM(Secret secret,
	    SecretsManagerClient secretsManagerClient, String password,
	    String username) {
	CreateSecretRequest request = CreateSecretRequest.builder()
		.name(secret.getDbname() + "-" + username)
		.secretString(getSecretString(secret, username, password))
		.build();
	secretsManagerClient.createSecret(request);
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
	    grantStatement = connection
		    .prepareStatement("GRANT ALL PRIVILEGES ON "+secret.getDbname()+".* TO ?;");
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
