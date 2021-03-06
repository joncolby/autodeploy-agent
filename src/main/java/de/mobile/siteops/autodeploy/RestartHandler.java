package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;

import de.mobile.zookeeper.AbstractNodeHandler;
import de.mobile.zookeeper.ZookeeperNode;
import de.mobile.zookeeper.ZookeeperService;

public class RestartHandler extends AbstractNodeHandler {

    public static final String RESTART_NODE_PREFIX = "/control/restart/";

    private static Logger logger = Logger.getLogger(RestartHandler.class.getName());
    
    private final ZookeeperService zookeeperService;
    
    private final String nodeName;
    
    public RestartHandler(String node, ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
        this.nodeName = node;
    }

    public void onNodeInitialized(ZookeeperNode node) {
        // nothing to do here
    }
    
    public void onNodeCreated(ZookeeperNode node) {
        if (node != null) {
            node.setData(AgentDaemon.VERSION);
        }
    }
    
    public void onNodeUnregistered(ZookeeperNode node) {
        // nothing to do here
    }
    
    public void onNodeDeleted(ZookeeperNode node) {
        // not important in this usecase
        logger.debug("Node '" + node + "' has been deleted");
    }

    public void onNodeData(ZookeeperNode node, Object data) {
        logger.info("Restart requested, shutting down application");
        zookeeperService.deleteNode(node, false);
        zookeeperService.shutdown();
        System.exit(0);
    }

    public String getNodeName() {
        return nodeName;
    }

    /*
    private static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) {}
    }
    */
    
}
