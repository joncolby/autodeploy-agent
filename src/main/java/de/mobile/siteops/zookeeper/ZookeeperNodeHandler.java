package de.mobile.siteops.zookeeper;


public interface ZookeeperNodeHandler {

    void onNodeDeleted(String node);

    void onNodeData(String node, Object data);    
}
