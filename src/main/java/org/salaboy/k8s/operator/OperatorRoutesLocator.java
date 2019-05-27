package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.k8s.operator.app.AppService;
import org.salaboy.k8s.operator.crds.app.*;
import org.salaboy.k8s.operator.crds.serviceA.DoneableServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceAList;
import org.salaboy.k8s.operator.crds.serviceB.DoneableServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceBList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RefreshScope
public class OperatorRoutesLocator implements RouteDefinitionLocator {

    public static final String SERVICE_A_PATH = "a";
    public static final String SERVICE_B_PATH = "b";
    private Logger logger = LoggerFactory.getLogger(OperatorRoutesLocator.class);


    private AppsOperator appsOperator;

    private AppService appService;

    private KubernetesClient kubernetesClient;


    public OperatorRoutesLocator(AppsOperator appsOperator,
                                 AppService appService,
                                 KubernetesClient kubernetesClient) {
        this.appsOperator = appsOperator;
        this.appService = appService;
        this.kubernetesClient = kubernetesClient;

    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        try {
            List<RouteDefinition> allRouteDefinitions = new ArrayList<RouteDefinition>();
            if (appsOperator.getApplicationCRD() != null) {
                List<Application> applications = kubernetesClient.customResources(appsOperator.getApplicationCRD(), Application.class,
                        ApplicationList.class, DoneableApplication.class).list().getItems();

                applications.forEach(app -> {
                    //@TODO: read from virtual services from istio

                    List<RouteDefinition> appRouteDefinitions = new ArrayList<RouteDefinition>();
                    List<RouteDefinition> serviceARoutes = getServiceARoutesForApplication(app);
                    appRouteDefinitions.addAll(serviceARoutes);
                    List<RouteDefinition> serviceBRoutes = getServiceBRoutesForApplication(app);
                    appRouteDefinitions.addAll(serviceBRoutes);

                    if (areApplicationRoutesReady(app, appRouteDefinitions)) { // if all the routes for the app are available add to main routes
                        allRouteDefinitions.addAll(appRouteDefinitions);
                    }
                });
            }

            //@TODO: i need to remove routes old routes ?????
            return Flux.fromIterable(allRouteDefinitions);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }



    //@TODO: improve routes and modules validation
    private boolean areApplicationRoutesReady(Application app, List<RouteDefinition> appRouteDefinitions) {
        final AtomicInteger validated = new AtomicInteger();
        if (appService.isAppHealthy(app)) { // ALL the modules are present
            logger.info("> App: " + app.getMetadata().getName() + " validation!");
            Set<ModuleDescr> modules = app.getSpec().getModules();
            if (modules != null) {
                modules.forEach(md -> {
                    appRouteDefinitions.forEach(rd -> {
                        if (rd.getId().equals(app.getMetadata().getName() + ":" + md.getName())) {
                            validated.incrementAndGet();
                        }
                    });
                });
                logger.info("> Modules size: " + modules.size() + " and validated: " + validated);
                if (validated.get() == modules.size()) {
                    return true;
                }
            }
        }

        return false;

    }

    private List<RouteDefinition> createRouteForServices(Application app, List<CustomService> resources, String path) {
        List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
        resources.forEach(service -> {
            RouteDefinition routeDefinition = new RouteDefinition();
            routeDefinition.setId(app.getMetadata().getName() + ":" + service.getMetadata().getName());
            routeDefinition.setUri(URI.create("http://" + service.getSpec().getServiceName()));

            //@TODO: It will be nice to add into the HEADERS the application where the service belongs

            PredicateDefinition predicateDefinition = new PredicateDefinition();
            predicateDefinition.setName("Path");
            String pattern = "/" + app.getMetadata().getName() + "/" + app.getSpec().getVersion() + "/" + path + "/" + service.getMetadata().getName() + "/**";
            predicateDefinition.addArg("pattern", pattern);
            routeDefinition.getPredicates().add(predicateDefinition);
            routeDefinitions.add(routeDefinition);
            logger.info("Route (id=" + app.getMetadata().getName() + ":" + service.getMetadata().getName() + ") added: " + pattern);
        });
        return routeDefinitions;
    }

    private List<RouteDefinition> getServiceARoutesForApplication(Application app) {
        if (appsOperator.getServiceACRD() != null) {
            List<ServiceA> serviceAList = kubernetesClient.customResources(appsOperator.getServiceACRD(), ServiceA.class,
                    ServiceAList.class, DoneableServiceA.class).list().getItems();
            return createRouteForServices(app, new ArrayList<>(serviceAList), SERVICE_A_PATH);
        }
        return Collections.EMPTY_LIST;
    }

    private List<RouteDefinition> getServiceBRoutesForApplication(Application app) {

        List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
        if (appsOperator.getServiceBCRD() != null) {
            List<ServiceB> serviceBList = kubernetesClient.customResources(appsOperator.getServiceBCRD(), ServiceB.class,
                    ServiceBList.class, DoneableServiceB.class).list().getItems();
            return createRouteForServices(app, new ArrayList<>(serviceBList), SERVICE_B_PATH);
        }
        return Collections.EMPTY_LIST;
    }


}
