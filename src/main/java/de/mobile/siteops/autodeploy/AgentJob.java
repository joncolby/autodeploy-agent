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
import de.mobile.zookeeper.ZookeeperService;
import de.mobile.zookeeper.ZookeeperService.ZookeeperState;
import de.mobile.zookeeper.ZookeeperStateMonitor;


public class AgentJob {

    private static Logger logger = Logger.getLogger(AgentJob.class.getName());

    private ZookeeperService zookeeperService;

    private NodesConfig nodesConfig;

    private final Configuration config;

    private WatchDog watcher = new WatchDog();

    private HeartbeatHandler heartbeatHandler;

    private ShutdownHook shutdownHook;

    private String environmentAndHost;

    public AgentJob(Configuration config) throws ConfigurationInvalidException {
        this.config = config;
        this.environmentAndHost = AgentUtils.getEnvironmentAndHost();

        NodesConfig nodesConfig = null;
        String defaultNodePrefix = AgentUtils.BASE_NODE;
        try {
            InputStream is;
            if (!AgentUtils.nodeConfigFileValid(config.getNodesConfigFile())) {
                if (config.getNodesConfigFile().getAbsolutePath().contains("classpath")) {
                    URL resource = getClass().getClassLoader().getResource(config.getNodesConfigFile().getName());
                    if (resource == null) {
                        throw new ConfigurationInvalidException("Could not read nodes configuration file '"
                                + config.getNodesConfigFile().getName() + "' from classpath");
                    }
                    is = resource.openStream();
                } else {
                    throw new ConfigurationInvalidException(
                            "Could not read nodes configuration file (not exists or readable)");
                }
            } else {
                is = new FileInputStream(config.getNodesConfigFile());
            }

            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(NodesConfig.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                nodesConfig = (NodesConfig) unmarshaller.unmarshal(is);
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }

            defaultNodePrefix = DefaultDeploymentHandler.BASE_DEPLOYMENT_NODE + environmentAndHost;
        } catch (IOException e) {
            throw new ConfigurationInvalidException("Cannot copen nodes config file");
        }

        for (NodeConfig nodeConfig : nodesConfig.getNodes()) {
            if (config.getArguments() != null && !config.getArguments().isEmpty()) {
                nodeConfig.setScriptArguments(config.getArguments());
            }
            if (nodeConfig.getPrefix() == null) {
                nodeConfig.setPrefix(defaultNodePrefix);
            }
            if (!AgentUtils.directoryExists(nodeConfig.getDataDir())) {
                throw new ConfigurationInvalidException("Datadir '" + nodeConfig.getDataDir()
                        + "' does not exists or is a file");
            }
            if (nodeConfig.getScript() != null) {
                File scriptFile = new File(nodeConfig.getScript());
                if (!AgentUtils.scriptFileValid(scriptFile)) {
                    throw new ConfigurationInvalidException(
                            "Configuration error: script file not readable or executable");
                }
            } else {
                throw new ConfigurationInvalidException("Configuration error: script is not specified");
            }
        }

        this.nodesConfig = nodesConfig;
        this.shutdownHook = new ShutdownHook(this);
    }

    public synchronized void start() throws ConfigurationInvalidException {
        try {
            zookeeperService = new ZookeeperService(config.getZooKeeperUrl(), config.getSerializeData(),
                    new SimpleStateMonitor());
        } catch (IOException e) {
            logger.error("Cannot create zookeeper for address " + config.getZooKeeperUrl());
            return;
        }
        zookeeperService.connect();

        // nodes will be initialized in initializeNodes() after connected, to ensure everthing is setup correctly after
        // reconnect

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        watcher.start();
    }

    private void initializeNodes() {
        String heartbeatNode = HeartbeatHandler.HEARTBEAT_NODE_PREFIX + environmentAndHost;
        heartbeatHandler = new HeartbeatHandler(heartbeatNode, zookeeperService);
        if (zookeeperService.createNode(heartbeatHandler)) {
            heartbeatHandler.setActive(true);
            heartbeatHandler.heartbeat();
        }

        for (NodeConfig nodeConfig : nodesConfig.getNodes()) {

            String statusNode = StatusHandler.STATUS_NODE_PREFIX + environmentAndHost;
            StatusHandler statusHandler = new StatusHandler(statusNode, zookeeperService);
            zookeeperService.createNode(statusHandler);

            DefaultDeploymentHandler deploymentHandler = new DefaultDeploymentHandler(nodeConfig, statusHandler,
                    zookeeperService);
            zookeeperService.registerNode(deploymentHandler);
        }
        String restartNode = RestartHandler.RESTART_NODE_PREFIX + environmentAndHost;
        zookeeperService.registerNode(new RestartHandler(restartNode, zookeeperService));
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
                    // try {
                    // TimeUnit.SECONDS.sleep(2);
                    // } catch (InterruptedException e) {}
                    logger.info("Successfully connected to zookeeper server");
                    initializeNodes();
                    break;
                default:
                    logger.warn("Unhandled state in StateMonitor: " + state);
            }
        }

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

}
