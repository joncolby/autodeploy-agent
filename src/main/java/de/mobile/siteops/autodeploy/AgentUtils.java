package de.mobile.siteops.autodeploy;

import java.io.File;
import java.net.*;
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

        if ( address == null ) {
            try {
                address = InetAddress.getLocalHost();
                logger.warn("Could not determine InetAddress for known interfaces, falling back to ip address set for localhost: " + address);
            } catch (UnknownHostException e) {
                logger.error("Could not determine ip address");
            }
        }

        String environment = mapEnvironmentFromIpAddress(address.getHostAddress());
        String fullyQualifiedHostName = System.getenv("USE_FQDN");
        String hostName = null;

        if (fullyQualifiedHostName != null && fullyQualifiedHostName.toUpperCase().equals("TRUE")) {
            // always use localhost hostname since some ips have double or fucked up dns entries.
            //hostName = address.getCanonicalHostName();
            try {
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
                logger.info("fully qualified hostname is '" + hostName + "' and environment is '" + environment + "'");
            } catch (UnknownHostException e) {
                logger.error("Could not obtain hostname via localHost? maybe not supported by OS?");
            }
        } else {
            try {
              hostName = InetAddress.getLocalHost().getHostName();
              if (hostName.indexOf(".") > 0) {
                    hostName = hostName.substring(0, hostName.indexOf("."));
              }
            } catch(UnknownHostException e) {
                logger.error("Could not obtain hostname via localHost? maybe not supported by OS?");
            }

            /*
            hostName = address.getHostName();
            if (Pattern.matches("[0-9\\.]+", hostName)) {
                String fallbackHostname;
                try {
                    fallbackHostname = InetAddress.getLocalHost().getHostName();
                    logger.warn("Could not determine hostname for " + address.getHostAddress() + ", falling back to local hostname: " + fallbackHostname);
                    hostName = fallbackHostname;
                } catch (UnknownHostException e) {
                    logger.error("Could not obtain hostname via localHost?  maybe not supported by OS?");
                }
            } else {
                if (address.getHostName().indexOf(".") > 0) {
                    hostName = address.getHostName().substring(0, address.getHostName().indexOf("."));
                }
            }
            logger.info("stripped hostname is '" + hostName + "' and environment is '" + environment + "'");
            */

        }

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

                if (interfaceAddress.getAddress() instanceof Inet4Address)
                    return interfaceAddress.getAddress();

            }
        } catch (SocketException e) {
            throw new ConfigurationInvalidException("Could not get ipv4 address for interface " + interfaceName, e);
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
