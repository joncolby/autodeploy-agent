package de.mobile.siteops.autodeploy.config;

import java.io.File;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;


public class Configuration {

    @Option(name = "-s", metaVar = "ADDR", aliases = "--server", usage = "The address of the zookeper server", required = true)
    private String zooKeeperSever;

    @Option(name = "-p", metaVar = "PORT", aliases = "--port", usage = "The port the zookeper server runs on", required = true)
    private String zooKeeperServerPort;

    @Option(name = "-n", metaVar = "NODECONFIG", aliases = "--nodeconfig", usage = "The xml node configuration file", required = true)
    private File nodesConfigFile;
    
    @Option(name = "--serialize", usage = "Data received and sent will be serialized or deserialized")
    private Boolean serializeData = false;

    @Argument
    private List<String> arguments;

    public String getZooKeeperSever() {
        return zooKeeperSever;
    }

    public void setZooKeeperSever(String zooKeeperSever) {
        this.zooKeeperSever = zooKeeperSever;
    }

    public String getZooKeeperServerPort() {
        return zooKeeperServerPort;
    }

    public void setZooKeeperServerPort(String zooKeeperServerPort) {
        this.zooKeeperServerPort = zooKeeperServerPort;
    }

    public File getNodesConfigFile() {
        return nodesConfigFile;
    }
    
    public void setNodesConfigFile(File nodesConfigFile) {
        this.nodesConfigFile = nodesConfigFile;
    }
    
    public List<String> getArguments() {
        return arguments;
    }
    
    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }
    
    public Boolean getSerializeData() {
        return serializeData;
    }
    
    public void setSerializeData(Boolean serializeData) {
        this.serializeData = serializeData;
    }
    
    public String getZooKeeperUrl() {
        return zooKeeperSever + ":" + zooKeeperServerPort;
    }

}
