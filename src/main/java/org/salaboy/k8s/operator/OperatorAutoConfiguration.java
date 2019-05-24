package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.k8s.operator.app.AppService;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class OperatorAutoConfiguration {


    @Bean
    public RouteDefinitionLocator applicationsRouteDefinitionLocator(AppsOperator appsOperator,
                                                                     AppService appService,
                                                                     KubernetesClient kubernetesClient) {
        return new OperatorRoutesLocator(appsOperator, appService, kubernetesClient);
    }
}
