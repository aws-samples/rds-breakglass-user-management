package com.amazonaws.breakglass.rds.demo;

public class CreateBreakglassUserRequest {

    String dataBaseName;

    public String getDataBaseName() {
	return dataBaseName;
    }

    public void setDataBaseName(String dataBaseName) {
	this.dataBaseName = dataBaseName;
    }

    public CreateBreakglassUserRequest(String dataBaseName) {
	super();
	this.dataBaseName = dataBaseName;
    }

    public CreateBreakglassUserRequest() {
    }

    @Override
    public String toString() {
	return "{\"dataBaseName\" : \"" + dataBaseName + "\"}";
    }

}
