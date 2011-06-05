package de.mobile.siteops.executor;

public interface ProcessNotifier {

    public static enum StreamType { STDOUT, STDERR };
    
    void processEnded(String identifier, int exitCode);
    
    void processInterrupted(String identifier);
    
    void onProcessOutput(String identifier, StreamType streamType, String line);
    
}
