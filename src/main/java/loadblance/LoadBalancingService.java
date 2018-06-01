package loadblance;


import etcd.server.bean.ServerInstance;

/**
 * Created by wenlongren on 2018/04/08.
 * 负载均衡
 */
public interface LoadBalancingService {
    ServerInstance getOneAvailableServer();
}
