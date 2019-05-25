package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class MyApplication implements CommandLineRunner {
    private Logger logger = LoggerFactory.getLogger(MyApplication.class);

    @Autowired
    private KubernetesClient kubernetesClient;

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class,
                args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("> Registering Service Watch.");
        kubernetesClient.services().watch(new Watcher<Service>() {
            @Override
            public void eventReceived(Action action, Service resource) {
                if (action.equals(Action.DELETED)) {
                    logger.error("> Admin Notification: Service " + resource.getMetadata().getName() + " Deleted.");
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {

            }
        });
    }


}
