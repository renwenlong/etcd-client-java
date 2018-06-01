package loadblance.Impl;

import etcd.client.DiscoverService;
import etcd.server.bean.ServerInstance;
import loadblance.LoadBalancingService;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Created by wenlongren on 2018/04/08.
 */
public class LoadBalancingServiceImpl implements LoadBalancingService {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancingServiceImpl.class);
    private DiscoverService discoverService;

    public void setDiscoverService(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @Override
    public ServerInstance getOneAvailableServer() {
        try {
            List<ServerInstance> availableServers = discoverService.getAvailableServers();
            //随机返回一个可用服务实例
            Integer availableNum = availableServers.size();
            if (availableNum == null) {
                return null;
            }
            Random random = new Random();
            Integer randomPos = random.nextInt(availableNum);
            logger.debug("select instance is [{}]", availableServers.get(randomPos).toString());
            return availableServers.get(randomPos);
        }catch (Exception e){
            logger.error("select instance error {}", ExceptionUtils.getFullStackTrace(e));
            return null;
        }
    }
}
