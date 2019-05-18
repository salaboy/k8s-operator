package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.k8s.operator.app.ApplicationService;
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
