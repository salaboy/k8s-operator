package org.salaboy.jbcnconf.gateway;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.jbcnconf.gateway.app.ApplicationService;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class OperatorAutoConfiguration {


    @Bean
    public RouteDefinitionLocator activitiCloudApplicationsRouteDefinitionLocator(ApplicationService applicationService,
                                                                                  KubernetesClient kubernetesClient) {
        return new OperatorRoutesLocator(applicationService, kubernetesClient);
    }
}
