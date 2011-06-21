package de.mobile.siteops.autodeploy;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import de.mobile.siteops.autodeploy.config.ConfigurationInvalidException;


public final class AgentUtils {

    static final String BASE_NODE = "/deploymentQueue/";

    public static boolean nodeConfigFileValid(File nodesConfigFile) {
        return nodesConfigFile != null && nodesConfigFile.exists() && nodesConfigFile.canRead();
    }

    public static boolean scriptFileValid(File scriptFile) {
        return scriptFile.exists() && !scriptFile.isDirectory() && scriptFile.canExecute();
    }

    public static boolean directoryExists(String dir) {
        if (dir == null) {
            return false;
        }
        File file = new File(dir);
        return file.exists() && file.isDirectory();
    }

    public static String getDefaultNodePrefix() throws ConfigurationInvalidException {
        InetAddress address = getInetAddressForInterface("eth0");
        String enviroment = mapFromIpAddress(address.getHostAddress());
        String hostName = address.getHostName();
        if (address.getHostName().indexOf(".") > 0) {
            hostName = address.getHostName().substring(0, address.getHostName().indexOf("."));
        }
        return BASE_NODE + enviroment + "/" + hostName;
    }

    private static String mapFromIpAddress(String ipAddress) throws ConfigurationInvalidException {
        String[] ipChunks = Iterables.toArray(Splitter.on(".").omitEmptyStrings().trimResults().split(ipAddress),
            String.class);
        Integer dataCenter = Integer.valueOf(ipChunks[1]);
        Integer subEnviroment = Integer.valueOf(ipChunks[2]);

        String enviroment = null;

        if (dataCenter.equals(45) || dataCenter.equals(46) || dataCenter.equals(47)) {
            enviroment = "Production";
        } else if (dataCenter.equals(44)) {
            if (subEnviroment.equals(230)) {
                enviroment = "Staging";
            } else if (subEnviroment > 200 && subEnviroment < 230) {
                enviroment = "Integra" + subEnviroment;
            } else {
                enviroment = "VPS";
            }
        } else if (dataCenter.equals(250)) {
            enviroment = "local";
        }

        if (enviroment == null) {
            throw new ConfigurationInvalidException("Could not determine enviroment for ip address " + ipAddress);
        }

        return enviroment;
    }

    private static InetAddress getInetAddressForInterface(String interfaceName) throws ConfigurationInvalidException {
        try {
            NetworkInterface iface = NetworkInterface.getByName(interfaceName);
            for (InterfaceAddress interfaceAddress : iface.getInterfaceAddresses()) {
                if (interfaceAddress.getAddress().isLinkLocalAddress()) continue;
                return interfaceAddress.getAddress();
            }
        } catch (SocketException e) {
            throw new ConfigurationInvalidException("Could not get ip address for interface " + interfaceName, e);
        }
        return null;
    }

}
