package de.mobile.siteops.autodeploy;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

import de.mobile.siteops.autodeploy.config.Configuration;


public class AgentDaemon implements WrapperListener {

    private AgentJob job;

    @Override
    public void controlEvent(int event) {
        if (!WrapperManager.isControlledByNativeWrapper()) {
            // We are not being controlled by the Wrapper, so
            // handle the event ourselves.
            if ((event == WrapperManager.WRAPPER_CTRL_C_EVENT) || (event == WrapperManager.WRAPPER_CTRL_CLOSE_EVENT)
                    || (event == WrapperManager.WRAPPER_CTRL_SHUTDOWN_EVENT)) {
                WrapperManager.stop(0);
            }
        }
    }

    @Override
    public Integer start(String[] args) {
        job = getAgentJob(args);
        if (job != null) {
            job.start();
        }
        return null;
    }

    @Override
    public int stop(int stopSignal) {
        if (job != null) {
            job.stop();
        }
        return stopSignal;
    }

    private AgentJob getAgentJob(String[] args) {
        Configuration config = new Configuration();
        CmdLineParser parser = new CmdLineParser(config);
        parser.setUsageWidth(120);

        try {
            parser.parseArgument(args);
            return new AgentJob(config);

        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
        return null;
    }

    public static void main(String[] args) {
        Configuration config = new Configuration();
        CmdLineParser parser = new CmdLineParser(config);
        parser.setUsageWidth(120);
        AgentJob agentJob = null;
        try {
            parser.parseArgument(args);
            agentJob = new AgentJob(config);

        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
        
        if (agentJob != null) {
            agentJob.start();
        }
    }

}
