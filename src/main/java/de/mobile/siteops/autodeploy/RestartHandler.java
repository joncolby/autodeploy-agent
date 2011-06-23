package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;

import de.mobile.siteops.zookeeper.ZookeeperNodeHandler;
import de.mobile.siteops.zookeeper.ZookeeperService;

public class RestartHandler implements ZookeeperNodeHandler {

    public static final String RESTART_NODE_PREFIX = "/control/restart/";

    private static Logger logger = Logger.getLogger(RestartHandler.class.getName());
    
    private final ZookeeperService zookeeperService;
    
    private final String node;
    
    public RestartHandler(String node, ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
        this.node = node;
    }
    
    public void onNodeDeleted(String node) {
        // not important in this usecase
        logger.debug("Node '" + node + "' has been deleted");
    }

    public void onNodeData(String node, Object data) {
        logger.info("Restart requested, shutting down application");
        zookeeperService.deleteNode(node, false);
        zookeeperService.shutdown();
        System.exit(0);
    }

    public String getNodeName() {
        return node;
    }

}
