package de.mobile.siteops.zookeeper;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import de.mobile.util.Reject;


public class ZookeeperService {

    private static Logger logger = Logger.getLogger(ZookeeperService.class.getName());

    public static enum ZookeeperState {
        CONNECTED,
        EXPIRED,
        DISCONNECTED
    };

    private static final int DEFAULT_TIMEOUT = 3000;

    private final String zookeeperUrl;
    
    private final boolean serializeData;
    
    private ZooKeeper zookeeper;

    private ZookeeperState state = ZookeeperState.DISCONNECTED;

    private ZookeeperStateMonitor stateMonitor;

    private ZookeeperNodeManager nodeManager;

    public ZookeeperService(String zookeeperUrl, boolean serializeData) throws IOException {
        Reject.ifNullOrEmptyOrWhitespace(zookeeperUrl);
        this.zookeeperUrl = zookeeperUrl;
        this.serializeData = serializeData;
        nodeManager = new ZookeeperNodeManager(serializeData);
    }

    public ZookeeperService(String zookeeperUrl, boolean serializeData, ZookeeperStateMonitor stateMonitor) throws IOException {
        this(zookeeperUrl, serializeData);
        this.stateMonitor = stateMonitor;
    }

    public void registerNode(ZookeeperNodeHandler nodeHandler) {
        Reject.ifNull(nodeHandler);
        nodeManager.register(nodeHandler);
    }
    
    public boolean unregisterNode(String path) {
        return nodeManager.unregister(path);
    }
    
    public boolean deleteNode(String path, boolean unregister) {
        return nodeManager.deleteNode(path, unregister);
    }
    
    public boolean createNode(ZookeeperNodeHandler nodeHandler) {
        return nodeManager.createNode(nodeHandler);
    }
    
    public void writeData(String path, Object data) {
        Reject.ifNullOrEmptyOrWhitespace(path);
        Reject.ifNull(data);
        nodeManager.writeData(path, data);
    }
    
    public boolean connect() {
        return connect(DEFAULT_TIMEOUT);
    }

    public boolean connect(int timeout) {
        try {
            zookeeper = new ZooKeeper(zookeeperUrl, timeout, new ClientWatcher());
        } catch (IOException e) {
            return false;
        }
        nodeManager.setZookeeper(zookeeper);
        if (state == ZookeeperState.CONNECTED) {
            nodeManager.refreshNodes();
        }
        return true;
    }

    public boolean connected() {
        return state == ZookeeperState.CONNECTED;
    }
    
    public void shutdown() {
        nodeManager.shutdown();
        try {
            zookeeper.close();
        } catch (InterruptedException e) {
            logger.error("Could not close zookeeper connection, thread interrupted: " + e.getMessage());
        }
    }
    
    public void setStateMonitor(ZookeeperStateMonitor stateMonitor) {
        this.stateMonitor = stateMonitor;
    }

    private class ClientWatcher implements Watcher {

        public void process(WatchedEvent watchedEvent) {
            String path = watchedEvent.getPath();
            switch (watchedEvent.getType()) {
                case NodeChildrenChanged:
                case NodeCreated:
                case NodeDeleted:
                case NodeDataChanged:
                    nodeManager.handleEvent(watchedEvent);
                    break;
                case None:
                    handleState(watchedEvent.getState());
                    break;
                default:
                    logger.error("Unhandled WatchedEvent: " + watchedEvent.getType());
            }
            
            // we have some data in path to handle
            if (path != null) {
                try {
                    Stat stat = zookeeper.exists(path, false);
                    if (stat != null) {
                        nodeManager.handleNodeData(path, stat);
                    }
                } catch (KeeperException e) {
                    logger.error("Keeper exception (" + e.getMessage() + ") occured for node '" + path + "'" ,e);
                } catch (InterruptedException e) {
                    logger.error("Keeper thread interrupted for node '" + path + "'" ,e);
                }
            }
        }

        private void handleState(KeeperState keeperState) {
            switch (keeperState) {
                case Disconnected:
                    state = ZookeeperState.DISCONNECTED;
                    logger.info("Disconnected from Zookeeper server");
                    break;
                case SyncConnected:
                    state = ZookeeperState.CONNECTED;
                    logger.info("Connected to Zookeeper server." + (serializeData ? " (data will be serialized and deserialized)"  : ""));
                    nodeManager.refreshNodes();
                    break;
                case Expired:
                    state = ZookeeperState.EXPIRED;
                    logger.warn("Zookeeper session has expired");
                    break;
            }
            if (stateMonitor != null) {
                stateMonitor.notity(state);
            }
        }
    }

}
