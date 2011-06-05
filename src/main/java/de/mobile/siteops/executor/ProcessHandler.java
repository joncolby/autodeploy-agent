package de.mobile.siteops.executor;

import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.mobile.siteops.executor.ProcessNotifier.StreamType;


public class ProcessHandler {

    private final Process process;
    
    private final String identifier;

    private final ProcessNotifier notifier;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private boolean running = true;
    
    public ProcessHandler(Process process, String identifier, ProcessNotifier notifier) {
        this.process = process;
        this.identifier = identifier;
        this.notifier = notifier;

        executor.execute(new StreamHandler(process.getInputStream(), StreamType.STDOUT));
        executor.execute(new StreamHandler(process.getErrorStream(), StreamType.STDERR));

    }

    public void waitForProcess(boolean triggerNotifier) {
        try {
            process.waitFor();
            if (triggerNotifier) {
                notifier.processEnded(identifier, process.exitValue());
            }
            executor.shutdownNow();
            running = false;
        } catch (InterruptedException e) {}
    }
    
    public void waitAsync() {
        Runnable command = new Runnable() {
            public void run() {
                try {
                    process.waitFor();
                    notifier.processEnded(identifier, process.exitValue());
                    executor.shutdownNow();
                    running = false;
                } catch (InterruptedException e) {
                    notifier.processInterrupted(identifier);
                }
                
            }
        };
        executor.execute(command);
    }

    public boolean isRunning() {
        return running;
    }
    
    // TODO better handle with timeout
    public void killProcess() {
        process.destroy();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
        }
        executor.shutdownNow();
    }
    
    private class StreamHandler implements Runnable {

        private final InputStream inputStream;

        private final StreamType streamType;

        StreamHandler(InputStream inputStream, StreamType streamType) {
            this.inputStream = inputStream;
            this.streamType = streamType;
        }

        public void run() {
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNext()) {
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    notifier.onProcessOutput(identifier, streamType, line);
                } else if (scanner.hasNext()) {
                    String line = scanner.next();
                    notifier.onProcessOutput(identifier, streamType, line);
                }
            }
            scanner.close();
        }

    }

}
