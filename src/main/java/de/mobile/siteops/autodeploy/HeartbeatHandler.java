package de.mobile.siteops.autodeploy;

import org.apache.log4j.Logger;

import de.mobile.siteops.zookeeper.ZookeeperNodeHandler;
import de.mobile.siteops.zookeeper.ZookeeperService;

public class HeartbeatHandler implements ZookeeperNodeHandler {

    private static Logger logger = Logger.getLogger(HeartbeatHandler.class.getName());

    static final int HEARTBEAT_INTERVAL = 10; // in seconds

    static final String HEARTBEAT_NODE_PREFIX = "/heartbeat/";
    
    private final String node;
    
    private final  ZookeeperService zookeeperService;
    
    private boolean active = false;
    
    private static HeartbeatHandler instance;
    
    private volatile String lastData = "";
    
    public HeartbeatHandler(String nodeName, ZookeeperService zookeeperService) {
        this.node = nodeName;
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
        zookeeperService.writeData(node, lastData);
        logger.debug("Sent heartbeat to node '" + node + "'");
    }

    public void refresh() {
        zookeeperService.refresh(node);
    }
    
    public String getNodeName() {
        return node;
    }
    
    public void onNodeDeleted(String node) {
        // someone deleted our heartbeat node? recreate it
        zookeeperService.unregisterNode(node);
        zookeeperService.createNode(this);
        heartbeat();
    }

    public void onNodeData(String node, Object data) {
        String received = (String) data;
        if (!received.equals(lastData) && received.length() > 0) {
            logger.warn("Data received on heartbeat node?! : " + (String) data);
        }
    }

}
