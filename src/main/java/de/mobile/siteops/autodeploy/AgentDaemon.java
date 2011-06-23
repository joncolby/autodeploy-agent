package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import de.mobile.siteops.autodeploy.config.Configuration;
import de.mobile.siteops.autodeploy.config.ConfigurationInvalidException;

public class AgentDaemon {

    private static Logger logger = Logger.getLogger(AgentDaemon.class.getName());

    private static final String VERSION = "0.5";
    
    private static AgentJob getAgentJob(String[] args) {
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
        final AgentJob agentJob = getAgentJob(args);
        logger.info("Autodeploy agent starting (version " + VERSION + ")");
        try {
            agentJob.start();
        } catch (ConfigurationInvalidException e) {
            logger.error("Configuration exception occured: " + e.getMessage(),e);
            System.exit(1);
        }
    }

}
