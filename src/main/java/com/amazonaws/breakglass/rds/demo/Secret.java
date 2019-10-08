package com.amazonaws.breakglass.rds.demo;

public class Secret {

    private String username, password, engine, port, host, dbname, dbInstanceIdentifier;

    public Secret(String username, String password, String engine, String port,
	    String host, String dbname, String dbInstanceIdentifier) {
	super();
	this.username = username;
	this.password = password;
	this.engine = engine;
	this.port = port;
	this.host = host;
	this.dbname = dbname;
	this.dbInstanceIdentifier = dbInstanceIdentifier;
	
    }

    public Secret() {
    }

    @Override
    public String toString() {
	return "{\"username\" : \"" + username + "\",\"password\" : \""
		+ password + "\",\"engine\" : \"" + engine + "\",\"port\" : \""
		+ port + "\",\"host\" : \"" + host + "\"," + "\"dbname\" : \""
		+ dbname + "\".\"dbInstanceIdentifier\" : \"" + dbInstanceIdentifier+"\"}";
    }

    public String getUsername() {
	return username;
    }

    public void setUsername(String username) {
	this.username = username;
    }

    public String getPassword() {
	return password;
    }

    public void setPassword(String password) {
	this.password = password;
    }

    public String getEngine() {
	return engine;
    }

    public void setEngine(String engine) {
	this.engine = engine;
    }

    public String getPort() {
	return port;
    }

    public void setPort(String port) {
	this.port = port;
    }

    public String getHost() {
	return host;
    }

    public void setHost(String host) {
	this.host = host;
    }

    public String getDbname() {
	return dbname;
    }

    public void setDbname(String dbname) {
	this.dbname = dbname;
    }
    
    public String getDbInstanceIdentifier() {
	return dbInstanceIdentifier;
    }

    public void setDbInstanceIdentifier(String dbInstanceIdentifier) {
	this.dbInstanceIdentifier = dbInstanceIdentifier;
    }
}
