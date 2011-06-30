package de.mobile.siteops.executor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProcessService {

    private final String command;

    private final Map<String, Object> additionalDataMap;

    private String identifier;

    private ProcessNotifier notifier;

    private ProcessHandler handler;

    private List<String> arguments;

    public ProcessService(String command, List<String> arguments, String identifier, ProcessNotifier notifier) {
        this.command = command;
        this.arguments = arguments;
        this.notifier = notifier;
        this.identifier = identifier;
        this.additionalDataMap = new HashMap<String, Object>();
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

    public String getIdentifier() {
        return identifier;
    }

    public boolean isProcessing() {
        return handler != null && handler.isRunning();
    }

    public ProcessHandler getHandler() {
        return handler;
    }

    public ProcessService addAdditionalData(String key, Object data) {
        additionalDataMap.put(key, data);
        return this;
    }

    public ProcessService clearArguments() {
        arguments.clear();
        return this;
    }

    public ProcessService addArgument(String argument) {
        if (argument != null && argument.trim().length() > 0) {
            arguments.add(argument);
        }
        return this;
    }

    public ProcessService addArguments(List<String> arguments) {
        if (arguments != null && arguments.size() > 0) {
            this.arguments.addAll(arguments);
        }
        return this;
    }

    public ProcessHandler execute() throws IOException {
        List<String> args = new ArrayList<String>();
        args.add(command);
        args.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);

        Process process = processBuilder.start();
        handler = new ProcessHandler(process, additionalDataMap, identifier, notifier);

        return handler;
    }

}
