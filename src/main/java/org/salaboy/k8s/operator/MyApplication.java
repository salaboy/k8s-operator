package org.salaboy.k8s.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@EnableDiscoveryClient
@SpringBootApplication
@EnableScheduling
public class MyApplication {
    Logger logger = LoggerFactory.getLogger(MyApplication.class);

    @Autowired
    private DiscoveryClient discoveryClient;

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class,
                args);
    }

    @Scheduled(fixedDelay = 10000)
    public void printDiscoveredServices() {
        logger.info("> Discovered Services. ");
        List<String> services = discoveryClient.getServices();
        services.forEach(s -> logger.info("\t > Discovered Service: " + s));
    }

}
