package com.amazonaws.breakglass.rds.demo;

public class GetMasterSecretValueRequest {

    String masterSecret;

    public String getMasterSecret() {
	return masterSecret;
    }

    public void setMasterSecret(String dataBaseName) {
	this.masterSecret = dataBaseName;
    }

    public GetMasterSecretValueRequest(String masterSecret) {
	super();
	this.masterSecret = masterSecret;
    }

    public GetMasterSecretValueRequest() {
    }

    @Override
    public String toString() {
	return "{\"masterSecret\" : \"" + masterSecret + "\"}";
    }

}
