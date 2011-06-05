package de.mobile.siteops.executor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mobile.util.StringUtils;


public class ProcessService {

    private final String command;
    
    private String identifier;
    
    private ProcessNotifier notifier;
    
    private ProcessHandler handler;
    
    private List<String> arguments;
    
    public ProcessService(String command, List<String> arguments, String identifier, ProcessNotifier notifier) {
        this.command = command;
        this.arguments = arguments;
        this.notifier = notifier;
        this.identifier = identifier;
    }

    public ProcessService(String command, ProcessNotifier notifier) {
        this(command, new ArrayList<String>(0), command, notifier);
    }

    public ProcessService(String command, String identifier, ProcessNotifier notifier) {
        this(command, new ArrayList<String>(0), identifier, notifier);
    }
    
    public String getCommand() {
        return command;
    }

    public boolean isProcessing() {
        return handler != null && handler.isRunning();
    }
    
    public ProcessHandler getHandler() {
        return handler;
    }
    
    public void clearArguments() {
        arguments.clear();
    }
    
    public void addArgument(String argument) {
        if (StringUtils.hasText(argument)) {
            arguments.add(argument);
        }
    }
    
    public void addArguments(List<String> arguments) {
        if (arguments != null && arguments.size() >0) {
            this.arguments.addAll(arguments);
        }
    }
    
    public ProcessHandler execute() throws IOException {
        List<String> args = new ArrayList<String>();
        args.add(command);
        args.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);
        
        Process process = processBuilder.start();
        handler = new ProcessHandler(process, identifier, notifier);
        
        return handler;
    }
    
}
