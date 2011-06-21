package de.mobile.siteops.autodeploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

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
            defaultNodePrefix = AgentUtils.getDefaultNodePrefix();
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
    }

    public synchronized void start() {
        try {
            zookeeperService = new ZookeeperService(config.getZooKeeperUrl(), config.getSerializeData(),  new SimpleStateMonitor());
        } catch (IOException e) {
            logger.error("Cannot create zookeeper for address " + config.getZooKeeperUrl());
            return;
        }

        for (NodeConfig nodeConfig : nodesConfig.getNodes()) {
            DefaultDeploymentHandler deploymentHandler = new DefaultDeploymentHandler(nodeConfig, zookeeperService);
            zookeeperService.registerNode(nodeConfig.getNode(), deploymentHandler);
            zookeeperService.connect();
        }
        
        while (true) {
            // infinite loop
        }
    }
    
    public synchronized void stop() {
        if (zookeeperService != null) {
            zookeeperService.shutdown();
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

    private class SimpleStateMonitor implements ZookeeperStateMonitor {

        public void notity(ZookeeperState state) {
            switch (state) {
                case DISCONNECTED:
                    logger.info("Reconnecting because of server disconnected");
                    zookeeperService.connect();
                    break;
                case EXPIRED:
                    logger.info("Reconnecting because of expired session");
                    zookeeperService.connect();
                    break;
                case CONNECTED:
                    logger.info("Successfully connected to zookeeper server");
                    break;
                 default:
                     logger.warn("Unhandled state in StateMonitor: " + state);
            }
        }

    }

}
