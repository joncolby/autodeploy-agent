package de.mobile.siteops.zookeeper;

import de.mobile.siteops.zookeeper.ZookeeperService.ZookeeperState;

public interface ZookeeperStateMonitor {

    void notity(ZookeeperState state);
    
}
