package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;

import de.mobile.zookeeper.AbstractNodeHandler;
import de.mobile.zookeeper.ZookeeperNode;
import de.mobile.zookeeper.ZookeeperService;

public class StatusHandler extends AbstractNodeHandler {

    public static final String STATUS_NODE_PREFIX = "/deployStatus/";
    
    private static Logger logger = Logger.getLogger(StatusHandler.class.getName());
    
    public static enum StatusType { SCRIPT_INFO, SCRIPT_ERROR, AGENT_ERROR };
    
    private final ZookeeperService zookeeperService;
    
    private final String nodeName;
    
    public StatusHandler(String node, ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
        this.nodeName = node;
    }
    
    public void onNodeDeleted(ZookeeperNode node) {
        // node was deleted, recreate
        zookeeperService.unregisterNode(node);
        zookeeperService.createNode(this);
    }

    public void onNodeData(ZookeeperNode node, Object data) {
        // we don't want to receive data on this node
        logger.info("Data received on this node? ('" + data + "'");
    }

    public String getNodeName() {
        return nodeName;
    }
    
    public synchronized void updateStatus(StatusType statusType, String message) {
        getNode().setData("{status:\"" + statusType.name() + "\", message:\"" + message + "\"}");
    }

}
