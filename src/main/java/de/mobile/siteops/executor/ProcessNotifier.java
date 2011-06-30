package de.mobile.siteops.executor;

import java.util.Map;

public interface ProcessNotifier {

    public static enum StreamType { STDOUT, STDERR };
    
    void processEnded(String identifier, Map<String, Object> additionalData, int exitCode);
    
    void processInterrupted(String identifier, Map<String, Object> additionalData);
    
    void onProcessOutput(String identifier, StreamType streamType, String line, Map<String, Object> additionalData);
    
}
