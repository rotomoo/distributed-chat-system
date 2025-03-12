package com.distributed.chat.system.service.discovery.base.listener;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.InstanceRegistry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.netflix.eureka.server.event.EurekaInstanceCanceledEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EurekaInstanceCanceledListener implements ApplicationListener<EurekaInstanceCanceledEvent> {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void onApplicationEvent(EurekaInstanceCanceledEvent event) {
        String appName = event.getAppName();
        String instanceId = event.getServerId();

        InstanceRegistry registry = EurekaServerContextHolder.getInstance().getServerContext().getRegistry();
        InstanceInfo instanceInfo = registry.getInstanceByAppAndId(appName, instanceId);

        if (instanceInfo != null) {
            int port = instanceInfo.getPort();
            String serverKey = instanceInfo.getIPAddr() + ":" + port;

            log.info("Canceled serverKey -> {}", serverKey);

            Set<String> userIds = redisTemplate.opsForSet().members("chatServerUri:" + serverKey);
            if (userIds != null) {
                for (String userId : userIds) {
                    userId = userId.replaceAll("^\"|\"$", "");
                    redisTemplate.delete(userId);
                }
            }
            redisTemplate.delete("chatServerUri:" + serverKey);
        } else {
            log.warn("Instance not found in Eureka Registry: {}", instanceId);
        }
    }
}