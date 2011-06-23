package de.mobile.siteops.autodeploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import de.mobile.siteops.autodeploy.config.Configuration;
import de.mobile.siteops.autodeploy.config.ConfigurationInvalidException;
import de.mobile.siteops.autodeploy.config.NodeConfig;
import de.mobile.siteops.autodeploy.config.NodesConfig;
import de.mobile.siteops.zookeeper.ZookeeperService;
import de.mobile.siteops.zookeeper.ZookeeperService.ZookeeperState;
import de.mobile.siteops.zookeeper.ZookeeperStateMonitor;


public class AgentJob {

    private static Logger logger = Logger.getLogger(AgentJob.class.getName());

    private ZookeeperService zookeeperService;
    
    private NodesConfig nodesConfig;
    
    private final Configuration config;
    
    private WatchDog watcher = new WatchDog();
    
    private HeartbeatHandler heartbeatHandler;
    
    private ShutdownHook shutdownHook;
    
    public AgentJob(Configuration config) {
        this.config = config;

        NodesConfig nodesConfig;
        String defaultNodePrefix = AgentUtils.BASE_NODE;
        try {
            InputStream is;
            if (!AgentUtils.nodeConfigFileValid(config.getNodesConfigFile())) {
                if (config.getNodesConfigFile().getAbsolutePath().contains("classpath")) {
                    URL resource = getClass().getClassLoader().getResource(config.getNodesConfigFile().getName());
                    if (resource == null) {
                        logger.error("Could not read nodes configuration file '" +  config.getNodesConfigFile().getName() + "' from classpath");
                        return;
                    }
                    is = resource.openStream();
                } else {
                    logger.error("Could not read nodes configuration file (not exists or readable)");
                    return;
                }
            } else {
                is = new FileInputStream(config.getNodesConfigFile());
            }
            
            nodesConfig = getNodesConfig(is);
            defaultNodePrefix = DefaultDeploymentHandler.BASE_DEPLOYMENT_NODE + AgentUtils.getEnvironmentAndHost();
        } catch (IOException e) {
            logger.error("Cannot copen nodes config file");
            return;
        } catch (ConfigurationInvalidException e) {
            logger.error("Configuration error: " + e.getMessage());
            return;
        }

        for (NodeConfig nodeConfig : nodesConfig.getNodes()) {
            if (config.getArguments() != null && !config.getArguments().isEmpty()) {
                nodeConfig.setScriptArguments(config.getArguments());
            }
            if (nodeConfig.getPrefix() == null) {
                nodeConfig.setPrefix(defaultNodePrefix);
            }
            if (!AgentUtils.directoryExists(nodeConfig.getDataDir())) {
                logger.error("Datadir '" + nodeConfig.getDataDir() + "' does not exists or is a file");
                return;
            }
            if (nodeConfig.getScript() != null) {
                File scriptFile = new File(nodeConfig.getScript());
                if (!AgentUtils.scriptFileValid(scriptFile)) {
                    logger.error("Configuration error: script file not readable or executable");
                    return;
                }
            } else {
                logger.error("Configuration error: script is not specified");
                return;
            }
        }

        this.nodesConfig = nodesConfig;
        this.shutdownHook = new ShutdownHook(this);
    }

    public synchronized void start() throws ConfigurationInvalidException {
        try {
            zookeeperService = new ZookeeperService(config.getZooKeeperUrl(), config.getSerializeData(),  new SimpleStateMonitor());
            String nodeName = HeartbeatHandler.HEARTBEAT_NODE_PREFIX + AgentUtils.getEnvironmentAndHost();
            heartbeatHandler = new HeartbeatHandler(nodeName, zookeeperService);
        } catch (IOException e) {
            logger.error("Cannot create zookeeper for address " + config.getZooKeeperUrl());
            return;
        }

        for (NodeConfig nodeConfig : nodesConfig.getNodes()) {
            DefaultDeploymentHandler deploymentHandler = new DefaultDeploymentHandler(nodeConfig, zookeeperService);
            zookeeperService.registerNode(deploymentHandler);
        }
        String nodeName = RestartHandler.RESTART_NODE_PREFIX + AgentUtils.getEnvironmentAndHost();
        zookeeperService.registerNode(new RestartHandler(nodeName, zookeeperService));
        zookeeperService.connect();
        
        AgentUtils.sleep(2); // give zookeeper time to connect
        heartbeatHandler.refresh();
        
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        
        watcher.start();
    }
    
    private void registerHeartbeat() {
        if (zookeeperService.createNode(heartbeatHandler)) {
            heartbeatHandler.setActive(true);
            heartbeatHandler.heartbeat();
        }
    }

    public synchronized void stop(int errorCode) {
        watcher.stop();
        if (zookeeperService != null) {
            zookeeperService.shutdown();
            logger.info("Zookeper stopped, all nodes unregistered");
        }
        if (errorCode > 0) {
            System.exit(errorCode);
        }
    }
    
    private NodesConfig getNodesConfig(InputStream is) {
        NodesConfig nodesConfig = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(NodesConfig.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            nodesConfig = (NodesConfig) unmarshaller.unmarshal(is);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

        return nodesConfig;
    }

    static class ShutdownHook extends Thread {
        private final AgentJob agentJob;
        public ShutdownHook(AgentJob agentJob) {
            this.agentJob = agentJob;
        }

        public void run() {
            logger.info("Shutting down agent gracefully.");
            agentJob.stop(0);
        }
    }
    
    static class WatchDog {
        
        Thread thread;
        
        void start() {
            thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(HeartbeatHandler.HEARTBEAT_INTERVAL));
                            HeartbeatHandler.getInstance().heartbeat();
                        }
                    } catch (InterruptedException interrupt) {
                        return; // graceful return
                    }
                }
            });
            thread.setDaemon(false);
            thread.start();
        }
        
        void stop() {
            thread.interrupt();
            try {
                thread.join();
                logger.info("Heartbeat stopped");
            } catch (InterruptedException ie) {
                // 
            }
        }
        
    }
    
    private class SimpleStateMonitor implements ZookeeperStateMonitor {

        public void notity(ZookeeperState state) {
            switch (state) {
                case DISCONNECTED:
                    logger.info("Reconnecting because of server disconnected");
                    heartbeatHandler.setActive(false);
                    break;
                case EXPIRED:
                    logger.info("Reconnecting because of expired session");
                    heartbeatHandler.setActive(false);
                    zookeeperService.connect();
                    break;
                case CONNECTED:
                    logger.info("Successfully connected to zookeeper server");
                    if (!heartbeatHandler.isActive()) {
                        registerHeartbeat();
                    }
                    break;
                 default:
                     logger.warn("Unhandled state in StateMonitor: " + state);
            }
        }

    }

}
