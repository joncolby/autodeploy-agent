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

}
