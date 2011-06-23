package de.mobile.siteops.autodeploy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import de.mobile.siteops.autodeploy.config.NodeConfig;
import de.mobile.siteops.executor.ProcessExitCode;
import de.mobile.siteops.executor.ProcessHandler;
import de.mobile.siteops.executor.ProcessNotifier;
import de.mobile.siteops.executor.ProcessService;
import de.mobile.siteops.zookeeper.ZookeeperNodeHandler;
import de.mobile.siteops.zookeeper.ZookeeperService;


public class DefaultDeploymentHandler implements ZookeeperNodeHandler {

    private static Logger logger = Logger.getLogger(DefaultDeploymentHandler.class.getName());

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    static final String BASE_DEPLOYMENT_NODE = "/deploymentQueue/";
    
    private final ZookeeperService zookeeperService;

    private final ProcessService processService;

    private final File dataDir;

    private final boolean keepData;

    private final List<String> scriptArguments;

    private final String node;

    private boolean processing = false;

    private File tempFile;

    public DefaultDeploymentHandler(NodeConfig nodeConfig, ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
        this.node = nodeConfig.getNode();
        this.dataDir = nodeConfig.getDataDirAsFile();
        this.keepData = nodeConfig.getKeepData();
        this.scriptArguments = nodeConfig.getScriptArguments();
        this.processService = new ProcessService(nodeConfig.getScript(), nodeConfig.getIdentifier(),
                new DefaultProcessNotifier());
    }

    public String getNodeName() {
        return node;
    }
    
    public void onNodeDeleted(String node) {
        if (processing) {
            if (processService.isProcessing()) {
                logger.info("Node '" + node + "' was removed but script '" + processService.getCommand()
                        + "' still running, terminating");
                processService.getHandler().killProcess();
            }
        }
    }

    public void onNodeData(String node, Object data) {
        if (processing) {
            if (processService.isProcessing()) {
                logger.warn("Script '" + processService.getCommand() + "' still running, terminating");
                processService.getHandler().killProcess();
            }
        }
        try {
            String fileName = "node_data_" + dateFormat.format(new Date()) + ".txt";
            if (dataDir != null) {
                tempFile = new File(dataDir, fileName);
            } else {
                File tempDir = new File(System.getProperty("java.io.tmpdir", "/tmp"));
                tempFile = new File(tempDir, fileName);
            }
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(((String) data).getBytes());
            fos.close();
        } catch (IOException e) {
            logger.error("Cannot write temporary node data, removing node");
            zookeeperService.deleteNode(node, false);
            return;
        }

        try {
            processService.clearArguments();
            processService.addArgument(tempFile.getAbsolutePath());
            processService.addArguments(scriptArguments);
            ProcessHandler processHandler = processService.execute();
            processHandler.waitAsync();
            processing = true;
        } catch (IOException e) {
            logger.error("Cannot execute script '" + processService.getCommand() + "'");
        }
    }

    private class DefaultProcessNotifier implements ProcessNotifier {

        public void processEnded(String identifier, int exitCode) {
            if (!keepData && tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            logger.info("Script '" + identifier + "' ended with exitCode " + exitCode + "("
                    + ProcessExitCode.getByCode(exitCode).getDescription() + ")");
            processing = false;
            zookeeperService.deleteNode(node, false);
        }

        public void processInterrupted(String identifier) {
            if (!keepData && tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            logger.error("Script '" + identifier + "' terminated");
            processing = false;
            zookeeperService.deleteNode(node, false);
        }

        public void onProcessOutput(String identifier, StreamType streamType, String line) {
            logger.info("Received from script '" + identifier + "' (on " + streamType + "): " + line);
        }

    }

}
