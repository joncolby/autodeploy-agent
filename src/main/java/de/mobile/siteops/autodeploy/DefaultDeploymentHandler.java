package de.mobile.siteops.autodeploy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import de.mobile.siteops.autodeploy.config.NodeConfig;
import de.mobile.siteops.executor.ProcessExitCode;
import de.mobile.siteops.executor.ProcessHandler;
import de.mobile.siteops.executor.ProcessNotifier;
import de.mobile.siteops.executor.ProcessService;
import de.mobile.zookeeper.AbstractNodeHandler;
import de.mobile.zookeeper.ZookeeperNode;
import de.mobile.zookeeper.ZookeeperService;


public class DefaultDeploymentHandler extends AbstractNodeHandler {

    private static Logger logger = Logger.getLogger(DefaultDeploymentHandler.class.getName());

    public static enum StatusType {
        SCRIPT_INFO,
        SCRIPT_ERROR,
        AGENT_ERROR,
        AGENT_INFO
    };

    static final String BASE_DEPLOYMENT_NODE = "/deploymentQueue/";

    private static final String KEY_DEPLOYMENTPLAN_FILE = "deploymentPlanFile";

    private static final String KEY_SCRIPTFILE_OUTPUTSTREAM = "deployScriptOutput";

    private final ZookeeperService zookeeperService;

    private final ProcessService processService;
    
    private final String identifier;

    private final File dataDir;

    private final boolean keepData;

    private final boolean keepScriptOutput;

    private final List<String> scriptArguments;

    private final String nodeName;

    private boolean processing = false;

    public DefaultDeploymentHandler(NodeConfig nodeConfig, ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
        this.nodeName = DefaultDeploymentHandler.BASE_DEPLOYMENT_NODE + nodeConfig.getNode();
        this.dataDir = nodeConfig.getDataDirAsFile();
        this.keepData = nodeConfig.getKeepData();
        this.keepScriptOutput = nodeConfig.getKeepScriptOutput();
        this.scriptArguments = nodeConfig.getScriptArguments();
        this.identifier = nodeConfig.getIdentifier();
        this.processService = new ProcessService(nodeConfig.getScript(), nodeConfig.getIdentifier(),
                new DefaultProcessNotifier());
    }

    public String getNodeName() {
        return nodeName;
    }

    public void onNodeCreated(ZookeeperNode node) {
        // nothing
    }

    public void onNodeDeleted(ZookeeperNode node) {
        if (processing) {
            if (processService.isProcessing()) {
                logger.info("Node '" + node + "' was removed but script '" + processService.getCommand()
                        + "' still running, terminating");
                processService.getHandler().killProcess();
            }
        }
    }

    public void onNodeData(ZookeeperNode node, Object data) {

        if (logger.isDebugEnabled()) logger.debug("Received on node " + node + ": " + data);

        if (data instanceof String) {
            String dataStr = (String) data;
            if (dataStr.startsWith("action=")) {
                String action = dataStr.substring(7);
                processAction(action, node);
                return;
            }
        }
        
        if (processing) {
            if (processService.isProcessing()) {
                logger.warn("[" + identifier + "] [Node:" + node + "] Script '"
                        + processService.getCommand() + "' still running, terminating");
                processService.getHandler().killProcess();
            }
        }
        String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        String deploymentPlanFileName = "node_" + identifier + "_data_" + date + ".txt";
        File deploymentPlanFile = createFile(deploymentPlanFileName, identifier, (String) data,
            "Cannot write temporary node data, removing node");
        if (deploymentPlanFile == null) return;

        FileOutputStream outputStream = null;
        if (keepScriptOutput) {
            String deployScriptOutputFileName = "deployscript_" + identifier + "_output_" + date
                    + ".txt";
            File deployScriptOutputFile = createFile(deployScriptOutputFileName, identifier, null,
                "Cannot write temporary node data, removing node");
            if (deployScriptOutputFile == null) return;

            try {
                outputStream = new FileOutputStream(deployScriptOutputFile);
            } catch (FileNotFoundException e) {
                return;
            }
        }

        try {

            ProcessHandler processHandler = processService.clearArguments() //
                    .addArgument(deploymentPlanFile.getAbsolutePath()) //
                    .addArguments(scriptArguments) //
                    .addAdditionalData(KEY_DEPLOYMENTPLAN_FILE, deploymentPlanFile) //
                    .addAdditionalData(KEY_SCRIPTFILE_OUTPUTSTREAM, outputStream) //
                    .execute(); //

            logger.info("Spawned script '" + identifier + "', command '"
                    + processService.getCommand() + "' in background");

            processHandler.waitAsync();
            processing = true;
        } catch (IOException e) {
            String errorMessage = "Cannot execute script '" + processService.getCommand() + "'";
            updateStatus(StatusType.AGENT_ERROR, identifier, errorMessage);
            logger.error(errorMessage);
        }
    }

    private void processAction(String action, ZookeeperNode node) {
        logger.info("Processing action '" + action + "' for node '" + node + "'");
        if (action.equals("abort")) {
            if (processing && processService.isProcessing()) {
                updateStatus(StatusType.AGENT_INFO, identifier, "Deployscript terminated on request");
                processService.getHandler().killProcess();
            }
        }
        
    }

    private File createFile(String fileName, String identifier, String data, String errorMessage) {
        try {
            File file;
            if (dataDir != null) {
                file = new File(dataDir, fileName);
            } else {
                File tempDir = new File(System.getProperty("java.io.tmpdir", "/tmp"));
                file = new File(tempDir, fileName);
            }
            if (data != null) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data.getBytes());
                fos.close();
            }
            return file;
        } catch (IOException e) {
            logger.error(errorMessage);
            updateStatus(StatusType.AGENT_ERROR, identifier, errorMessage);
            sleep(500);
            zookeeperService.deleteNode(getNode(), false);
            return null;
        }
    }

    public void updateStatus(StatusType statusType, String identifier, String message) {
        String date = new SimpleDateFormat("yyyy-mm-dd HH:mm:ss").format(new Date());
        getNode().setData("{status:\"" + statusType.name() + "\",date:\"" + date + "\",identifier:\"" + identifier + "\",message:\"" + message + "\"}");
    }

    private class DefaultProcessNotifier implements ProcessNotifier {

        public void processEnded(String identifier, Map<String, Object> additionalData, int exitCode) {
            // sleep a second otherwise the last output from onProcessOutput is received after processEnded
            sleep(1000);

            File tempFile = (File) additionalData.get(KEY_DEPLOYMENTPLAN_FILE);

            if (!keepData && tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }

            FileOutputStream stream = (FileOutputStream) additionalData.get(KEY_SCRIPTFILE_OUTPUTSTREAM);
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {}
            }

            ProcessExitCode code = ProcessExitCode.getByCode(exitCode);
            logger.info("Script '" + identifier + "' ended with code " + exitCode + " (" + code.getDescription() + ")");

            StatusType statusType = code == ProcessExitCode.OK ? StatusType.SCRIPT_INFO : StatusType.SCRIPT_ERROR;
            String message = "Deployment ended with code " + exitCode + " (" + code.getDescription() + ")";

            updateStatus(statusType, identifier, message);

            processing = false;
            if (getNode().exists()) {
                sleep(500);
                zookeeperService.deleteNode(getNode(), false);
            }
        }

        public void processInterrupted(String identifier, Map<String, Object> additionalData) {
            // sleep a second otherwise the last output from onProcessOutput is received after processEnded
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e1) {}

            File tempFile = (File) additionalData.get(KEY_DEPLOYMENTPLAN_FILE);

            if (!keepData && tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }

            FileOutputStream stream = (FileOutputStream) additionalData.get(KEY_SCRIPTFILE_OUTPUTSTREAM);
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {}
            }

            logger.error("Script '" + identifier + "' terminated, removing node '" + getNode() + "'");
            updateStatus(StatusType.SCRIPT_ERROR, identifier, "Script was terminated by system or by keeper internal");
            
            sleep(500);
            processing = false;
            zookeeperService.deleteNode(getNode(), false);
        }

        public void onProcessOutput(String identifier, StreamType streamType, String line,
            Map<String, Object> additionalData) {
            FileOutputStream stream = (FileOutputStream) additionalData.get(KEY_SCRIPTFILE_OUTPUTSTREAM);

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String message = date + " - " + identifier + " - " + streamType.name() + " - " + line;
            StatusType statusType = (streamType == StreamType.STDOUT ? StatusType.SCRIPT_INFO : StatusType.SCRIPT_ERROR);
            updateStatus(statusType, identifier, line);

            if (stream != null) {
                try {
                    stream.write((message + "\n").getBytes());
                    if (logger.isDebugEnabled()) logger.debug("Received from script: " + message);
                } catch (IOException e) {
                    logger.info("Received from script: " + message);
                }
            } else {
                logger.info("Received from script: " + message);
            }

        }

    }
    
    private static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) { }
    }
    

}
