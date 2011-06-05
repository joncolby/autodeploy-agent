package de.mobile.siteops.zookeeper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import de.mobile.siteops.zookeeper.ZookeeperNodeManager.Node;

class ZookeeperNodeWorker {

    protected static Logger logger = Logger.getLogger(ZookeeperNodeWorker.class.getName());

    private final Node node;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final ZookeeperNodeHandler nodeHandler;
    
    public ZookeeperNodeWorker(Node node, ZookeeperNodeHandler nodeHandler) {
        this.node = node;
        this.nodeHandler = nodeHandler;
    }
    
    void nodeDeleted() {
        if (logger.isDebugEnabled()) {
            logger.debug("Entered nodeDeleted for '" + this + "'");
        }
        nodeHandler.onNodeDeleted(node.getPath());
        node.refresh();
    }
    
    void nodeData(final Object data) {
        if (logger.isDebugEnabled()) {
            logger.debug("Entered nodeData for '" + this + "'");
        }
        Runnable command = new Runnable() {
            public void run() {
                nodeHandler.onNodeData(node.getPath(), data);
                if (logger.isDebugEnabled()) {
                    logger.debug("Finished processing data for node '" + this + "'");
                }
            }
        };
        executor.execute(command);
    }
    
    void shutdown() {
        executor.shutdownNow();
    }

}
