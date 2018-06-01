package etcd.client;

import etcd.server.bean.ServerInstance;

import java.util.List;

public interface DiscoverService {
    void startWatch();

    List<ServerInstance> getAvailableServers();
}
