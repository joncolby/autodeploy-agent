package de.mobile.siteops.autodeploy;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import de.mobile.siteops.autodeploy.config.ConfigurationInvalidException;


public final class AgentUtils {

    private static Logger logger = Logger.getLogger(AgentUtils.class.getName());
    
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
        InetAddress address = null;

        // server may have multiple interfaces.  these should be checked first
        String[] interFaces = { "bond0", "eth0", "bnxe0", "aggr1", "bnx0", "e1000g0" };

        for (String i: interFaces) {
                address = getInetAddressForInterface(i);
                if ( address != null ) {
                    break;
                }
        }

        //address = getInetAddressForInterface("bond0");
        //if (address == null) {
        //    address = getInetAddressForInterface("eth0");
        //}

        if (address == null) {
            throw new ConfigurationInvalidException("Could not obtain IP address from interfaces");
        }
        String environment = mapEnvironmentFromIpAddress(address.getHostAddress());
        String hostName = address.getHostName();
        if (Pattern.matches("[0-9\\.]+", hostName)) {
            String fallbackHostname;
            try {
                fallbackHostname = InetAddress.getLocalHost().getHostName();
                logger.warn("Could not determine hostname for " + address.getHostAddress() + ", falling back to local hostname: " + fallbackHostname);
                hostName = fallbackHostname;
            } catch (UnknownHostException e) {
                logger.error("Could not get obtain local host? maybe not supported by OS?");
            }
        } else {
            if (address.getHostName().indexOf(".") > 0) {
                hostName = address.getHostName().substring(0, address.getHostName().indexOf("."));
            }
        }
        logger.info("stripped hostname is '" + hostName + "' and environment is '" + environment + "'");
        return environment + "/" + hostName;
    }
    
    private static String mapEnvironmentFromIpAddress(String ipAddress) throws ConfigurationInvalidException {
        String environment = System.getenv("environment");
        if (environment != null) {
            return environment;
        }
        
        String[] ipChunks = Iterables.toArray(Splitter.on(".").omitEmptyStrings().trimResults().split(ipAddress),
            String.class);
        Integer dataCenter = Integer.valueOf(ipChunks[1]);
        Integer subEnvironment = Integer.valueOf(ipChunks[2]);

        if (dataCenter.equals(45) || dataCenter.equals(46) || dataCenter.equals(47) || dataCenter.equals(38) ) {
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
            if (iface == null) {
                return null;
            }
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
