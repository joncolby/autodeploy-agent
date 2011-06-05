package de.mobile.siteops.autodeploy.config;

public class ConfigurationInvalidException extends Exception {

    private static final long serialVersionUID = 1L;
    
    public ConfigurationInvalidException() { }
    
    public ConfigurationInvalidException(String message) {
        super(message);
    }
    
    public ConfigurationInvalidException(String message, Throwable e) {
        super(message, e);
    }
    
}
