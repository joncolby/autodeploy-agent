package de.mobile.siteops.zookeeper;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.data.Stat;

import com.google.common.base.Splitter;


class ZookeeperNodeManager {

    private static Logger logger = Logger.getLogger(ZookeeperNodeManager.class.getName());

    private ZooKeeper zookeeper;

    private Map<Node, ZookeeperNodeWorker> nodes;

    private final boolean serializeData;

    public ZookeeperNodeManager(boolean serializeData) {
        nodes = new HashMap<ZookeeperNodeManager.Node, ZookeeperNodeWorker>(1);
        this.serializeData = serializeData;
    }

    public void setZookeeper(ZooKeeper zookeeper) {
        this.zookeeper = zookeeper;
    }

    public boolean register(ZookeeperNodeHandler nodeHandler) {
        String path = nodeHandler.getNodeName();
        if (!nodeExists(path)) {
            Node node = new Node(path, serializeData);
            nodes.put(node, new ZookeeperNodeWorker(node, nodeHandler));
            logger.info("Registered new node '" + node + "', handler: " + nodeHandler.getClass().getName());
            return true;
        } else {
            logger.warn("Could not register node '" + path + "' since this node is already registered");
            return false;
        }
    }

    public boolean unregister(String path) {
        Node node = getNodeByPath(path);
        if (node != null) {
            nodes.get(node).shutdown();
            nodes.remove(node);
            logger.info("Unregistered node '" + node + "'");
            return true;
        } else {
            logger.warn("Could not unregister node '" + path + "' (node not registered)");
        }
        return false;
    }

    public boolean createNode(ZookeeperNodeHandler nodeHandler) {
        String path = nodeHandler.getNodeName();
        if (!nodeExists(path)) {
            Node node = new Node(path, serializeData);
            if (node.create(CreateMode.PERSISTENT)) {
                nodes.put(node, new ZookeeperNodeWorker(node, nodeHandler));
                logger.info("Created new node '" + node + "', handler: " + nodeHandler.getClass().getName());
                return true;
            }
        } else {
            logger.error("Could not create node '" + path + "' since this node already exists");
        }
        return false;
    }

    public void writeData(String path, Object data) {
        Node node = getNodeByPath(path);
        if (node != null && node.exists()) {
            node.setData(data);
        }
    }

    public boolean deleteNode(String path, boolean unregister) {
        Node node = getNodeByPath(path);
        if (node != null) {
            boolean result = true;
            if (node.exists()) {
                result = node.delete();
            }
            if (unregister) {
                unregister(path);
            }
            return result;
        }
        return false;
    }
    
    void handleEvent(WatchedEvent watchedEvent) {
        EventType eventType = watchedEvent.getType();
        String path = watchedEvent.getPath();
        logger.debug("Handling event '" + eventType + "' for node " + (path != null ? path : "(no path yet)"));
        if (eventType == EventType.NodeDeleted) {
            synchronized (this) {
                Node node = getNodeByPath(path);

                if (node != null) {
                    nodes.get(node).nodeDeleted();
                }
            }
        }
    }

    void handleNodeData(String path, Stat stat) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handling data for node '" + path + "', datalength = " + stat.getDataLength());
        }
        Node node = getNodeByPath(path);
        if (node != null) {
            node.setStat(stat);
            handleDataInternal(node);
        } else {
            logger.warn("Should handle data for node '" + path + "', but no registered node found");
        }
    }

    void handleDataInternal(Node node) {
        if (logger.isDebugEnabled()) {
            logger.debug("Entered handle data internal for node '" + node + "'");
        }
        Object data = node.getData();
        if (data == null) {
            nodes.get(node).nodeDeleted();
        } else {
            nodes.get(node).nodeData(data);
        }
    }

    void refreshNodes() {
        Iterator<Node> iterator = nodes.keySet().iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node.refresh()) {
                if (node.hasData()) {
                    logger.info("Node '" + node + "' refreshed and watched, consuming data already existent data");
                    handleDataInternal(node);
                } else {
                    logger.info("Node '" + node + "' refreshed and watched");
                }
            }
        }
    }

    void shutdown() {
        Iterator<Node> iterator = nodes.keySet().iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            nodes.get(node).shutdown();
            logger.info("Unregistered node '" + node + "'");
            iterator.remove();
        }
    }

    public Node getNodeByPath(String path) {
        for (Node node : nodes.keySet()) {
            if (node.getPath().equals(path)) {
                return node;
            }
        }
        return null;
    }

    private boolean nodeExists(String path) {
        return getNodeByPath(path) != null ? true : false;
    }

    public class Node {

        private String path;

        private Stat stat;

        private final boolean serialize;

        public Node(String path, boolean serialize) {
            this.path = path;
            this.serialize = serialize;
            if (zookeeper != null && zookeeper.getState() == States.CONNECTED) {
                refresh();
            }
        }

        public boolean hasData() {
            return stat != null && stat.getDataLength() > 0;
        }

        public String getPath() {
            return path;
        }

        public void setStat(Stat stat) {
            this.stat = stat;
        }

        public boolean create(CreateMode createMode) {
            try {
                StringBuilder builder = new StringBuilder();
                for (String pathElement : Splitter.on("/").omitEmptyStrings().split(path.substring(1))) {
                    builder.append("/").append(pathElement);
                    String partialPath = builder.toString();
                    try {
                        if (zookeeper.exists(partialPath, false) == null) {
                            zookeeper.create(partialPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                createMode);
                        }
                    } catch (KeeperException.NodeExistsException e) {
                        // ignore, this part of the node already exists
                    }
                }
                refresh();
                return true;
            } catch (KeeperException e) {
                logger.error("Cannot create node '" + path + "', keeper exception occured: " + e.getMessage());
            } catch (InterruptedException e) {
                logger.error("Cannot create node '" + path + "', thread interrupted" ,e);
            }
            return false;
        }

        public void setData(Object data) {
            byte[] objectData = serialize ? ZookeeperUtils.serialize(data) : data.toString().getBytes();
            try {
                zookeeper.setData(path, objectData, stat.getVersion());
            } catch (KeeperException e) {
                logger.error("Cannot set data on node '" + this + "', keeper exception occured: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                logger.error("Cannot set data on node '" + this + "', thread interrupted", e);
            }
        }

        public Object getData() {
            Object result = null;
            try {
                byte[] data = zookeeper.getData(path, true, stat);
                result = serialize ? ZookeeperUtils.deserialize(data) : new String(data);
            } catch (InterruptedException e) {
                logger.error("Cannot read data for node '" + this + "', thread interrupted", e);
            } catch (KeeperException e) {
                logger.error(
                    "Cannot read data for node '" + this + ", zookeeper rexecption occured: " + e.getMessage(), e);
            }
            return result;
        }

        public boolean delete() {
            try {
                zookeeper.delete(path, -1);
                return true;
            } catch (InterruptedException e) {
                logger.error("Could not delete node " + this + ", thread interuppted.");
                return false;
            } catch (KeeperException e) {
                logger.warn("Could not delete node " + this + ", node is already deleted");
                return false;
            }
        }

        public List<String> getChildren() throws InterruptedException, KeeperException {
            return zookeeper.getChildren(path, true);
        }

        public boolean exists() {
            try {
                zookeeper.exists(path, false);
                return true;
            } catch (KeeperException e) {} catch (InterruptedException e) {}
            return false;
        }

        public boolean refresh() {
            if (zookeeper.getState() == States.CONNECTING) {
                logger.info("Cannot refresh node '" + this + ", currently trying to connect to zookeeper server");
//                return false;
            }

            try {
                stat = zookeeper.exists(path, true);
                if (logger.isDebugEnabled()) {
                    if (stat == null) {
                        logger.debug("refreshed and watching node '" + this + "'");
                    } else {
                        logger.debug("refreshed and watching node '" + this + "', created " + new Date(stat.getCtime())
                                + ", modified " + new Date(stat.getMtime()) + ", version " + stat.getVersion());
                    }
                }
                return true;
            } catch (KeeperException e) {
                logger.error("Could not refresh node '" + this + "', keeper execption occured (" + e.getMessage() + ")");
                return false;
            } catch (InterruptedException e) {
                logger.error("Could not refresh node '" + this + "', thread interrupted");
                return false;
            }
        }

        public boolean equals(Object other) {
            return this.path.equals(((Node) other).path);
        }

        public String toString() {
            return path;
        }
    }

}
