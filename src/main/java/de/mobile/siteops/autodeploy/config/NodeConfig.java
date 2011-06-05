package de.mobile.siteops.autodeploy.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class NodeConfig {

    @XmlElement(name = "identifier")
    private String identifier;

    @XmlElement(name = "prefix")
    private String prefix;
    
    @XmlElement(name = "name")
    private String name;
    
    @XmlElement(name = "script")
    private String script;
    
    @XmlElement(name = "datadir")
    private String dataDir;
    
    @XmlElement(name = "keepdata")
    private Boolean keepData;

    private List<String> scriptArguments = new ArrayList<String>();
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getDataDir() {
        return dataDir;
    }
    
    public File getDataDirAsFile() {
        return dataDir != null ? new File(dataDir) : null;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Boolean getKeepData() {
        return keepData != null ? keepData : false;
    }

    public void setKeepData(Boolean keepData) {
        this.keepData = keepData;
    }

    public String getNode() {
        return prefix + (name != null ? "/" + name : "");
    }
    
    public List<String> getScriptArguments() {
        return scriptArguments;
    }
    
    public void setScriptArguments(List<String> scriptArguments) {
        this.scriptArguments = scriptArguments;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("identifier: ").append(nullSafe(identifier)).append(", ");
        sb.append("prefix: ").append(nullSafe(prefix)).append(", ");
        sb.append("name: ").append(nullSafe(name)).append(", ");
        sb.append("script: ").append(nullSafe(script)).append(", ");
        sb.append("dataDir: ").append(nullSafe(dataDir)).append(", ");
        sb.append("keepData: ").append(nullSafe(keepData)).append(", ");
        sb.append("node: ").append(nullSafe(getNode()));
        return sb.toString();
    }

    private String nullSafe(Object obj) {
        return obj != null ? obj.toString() : "(null)";
    }

    
}
