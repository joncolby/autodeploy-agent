package de.mobile.siteops.autodeploy;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

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

    public static String getEnvironmentAndHost() throws ConfigurationInvalidException {
        InetAddress address = getInetAddressForInterface("eth0");
        String environment = mapFromIpAddress(address.getHostAddress());
        String hostName = address.getHostName();
        if (address.getHostName().indexOf(".") > 0) {
            hostName = address.getHostName().substring(0, address.getHostName().indexOf("."));
        }
        return environment + "/" + hostName;
    }
    
    private static String mapFromIpAddress(String ipAddress) throws ConfigurationInvalidException {
        String[] ipChunks = Iterables.toArray(Splitter.on(".").omitEmptyStrings().trimResults().split(ipAddress),
            String.class);
        Integer dataCenter = Integer.valueOf(ipChunks[1]);
        Integer subEnvironment = Integer.valueOf(ipChunks[2]);

        String environment = null;

        if (dataCenter.equals(45) || dataCenter.equals(46) || dataCenter.equals(47)) {
            environment = "Production";
        } else if (dataCenter.equals(44)) {
            if (subEnvironment.equals(230)) {
                environment = "Staging";
            } else if (subEnvironment > 200 && subEnvironment < 230) {
                environment = "Integra" + subEnvironment;
            } else {
                environment = "VPS";
            }
        } else if (dataCenter.equals(250)) {
            environment = "local";
        }

        if (environment == null) {
            throw new ConfigurationInvalidException("Could not determine environment for ip address " + ipAddress);
        }

        return environment;
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

   
    public static void sleep(int seconds) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
        } catch (InterruptedException e) {
        }
    }

}
