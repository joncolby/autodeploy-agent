package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;

import de.mobile.zookeeper.AbstractNodeHandler;
import de.mobile.zookeeper.ZookeeperNode;
import de.mobile.zookeeper.ZookeeperService;

public class HeartbeatHandler extends AbstractNodeHandler {

    private static Logger logger = Logger.getLogger(HeartbeatHandler.class.getName());

    static final int HEARTBEAT_INTERVAL = 10; // in seconds

    static final String HEARTBEAT_NODE_PREFIX = "/heartbeat/";
    
    private final String nodeName;
    
    private final  ZookeeperService zookeeperService;
    
    private boolean active = false;
    
    private static HeartbeatHandler instance;
    
    private volatile String lastData = "";
    
    public HeartbeatHandler(String nodeName, ZookeeperService zookeeperService) {
        this.nodeName = nodeName;
        this.zookeeperService = zookeeperService;
        instance = this;
        
    }

    public static HeartbeatHandler getInstance() {
        return instance;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void heartbeat() {
        lastData = String.valueOf(System.currentTimeMillis());
        getNode().setData(lastData);
        if (logger.isDebugEnabled()) logger.debug("Sent heartbeat to node '" + getNode() + "'");
    }

    public void refresh() {
        getNode().refresh();
    }
    
    public String getNodeName() {
        return nodeName;
    }
    
    public void onNodeDeleted(ZookeeperNode node) {
        // someone deleted our heartbeat node? recreate it
        zookeeperService.unregisterNode(node);
        zookeeperService.createNode(this);
        heartbeat();
    }

    public void onNodeData(ZookeeperNode node, Object data) {
        String received = (String) data;
        if (!received.equals(lastData) && received.length() > 0) {
            logger.warn("Data received on heartbeat node?! : " + (String) data);
        }
    }

}
