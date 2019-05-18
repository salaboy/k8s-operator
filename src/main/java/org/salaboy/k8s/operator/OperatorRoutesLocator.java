package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.k8s.operator.app.ApplicationService;
import org.salaboy.k8s.operator.crds.app.Application;
import org.salaboy.k8s.operator.crds.app.ApplicationList;
import org.salaboy.k8s.operator.crds.app.DoneableApplication;
import org.salaboy.k8s.operator.crds.app.ModuleDescr;
import org.salaboy.k8s.operator.crds.serviceA.DoneableServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceAList;
import org.salaboy.k8s.operator.crds.serviceB.DoneableServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceBList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class OperatorRoutesLocator implements RouteDefinitionLocator {

    public static final String SERVICE_A_PATH = "a";
    public static final String SERVICE_B_PATH = "b";
    private Logger logger = LoggerFactory.getLogger(OperatorRoutesLocator.class);


    private ApplicationService applicationService;

    private KubernetesClient kubernetesClient;

    public OperatorRoutesLocator(ApplicationService applicationService, KubernetesClient kubernetesClient) {
        this.applicationService = applicationService;
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        try {
            List<RouteDefinition> allRouteDefinitions = new ArrayList<RouteDefinition>();
            if (applicationService.getApplicationCRD() != null) {
                List<Application> applications = kubernetesClient.customResources(applicationService.getApplicationCRD(), Application.class,
                        ApplicationList.class, DoneableApplication.class).list().getItems();


                applications.forEach(app -> {

                    List<RouteDefinition> appRouteDefinitions = new ArrayList<RouteDefinition>();
                    List<RouteDefinition> serviceARoutes = getServiceARoutesForApplication(app);
                    appRouteDefinitions.addAll(serviceARoutes);
                    List<RouteDefinition> serviceBRoutes = getServiceBRoutesForApplication(app);
                    appRouteDefinitions.addAll(serviceBRoutes);

                    if (isApplicationReady(app, appRouteDefinitions)) { // if all the routes for the app are available add to main routes
                        allRouteDefinitions.addAll(appRouteDefinitions);
                    }
                });
            }


            return Flux.fromIterable(allRouteDefinitions);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }

    //@TODO: improve routes and modules validation
    private boolean isApplicationReady(Application app, List<RouteDefinition> appRouteDefinitions) {
        Set<ModuleDescr> modules = app.getSpec().getModules();
        final AtomicInteger validated = new AtomicInteger();
        logger.info("> App: " + app.getMetadata().getName() + " validation!");
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

        return false;

    }
    //@TODO: remove duplication
    private List<RouteDefinition> getServiceARoutesForApplication(Application app) {

        List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
        if (applicationService.getServiceACRD() != null) {
            List<ServiceA> serviceAList = kubernetesClient.customResources(applicationService.getServiceACRD(), ServiceA.class,
                    ServiceAList.class, DoneableServiceA.class).list().getItems();
            serviceAList.forEach(serviceA -> {
                RouteDefinition routeDefinition = new RouteDefinition();
                routeDefinition.setId(app.getMetadata().getName() + ":" + serviceA.getMetadata().getName());
                routeDefinition.setUri(URI.create("http://" + serviceA.getSpec().getServiceName()));

                //@TODO: It will be nice to add into the HEADERS the application where the service belongs

                PredicateDefinition predicateDefinition = new PredicateDefinition();
                predicateDefinition.setName("Path");
                String pattern = "/" + app.getMetadata().getName() + "/" + app.getSpec().getVersion() + "/" + SERVICE_A_PATH + "/" + serviceA.getMetadata().getName() + "/**";
                predicateDefinition.addArg("pattern", pattern);
                routeDefinition.getPredicates().add(predicateDefinition);
                routeDefinitions.add(routeDefinition);
                logger.info("Route (id="+app.getMetadata().getName() + ":" + serviceA.getMetadata().getName()+") added: " + pattern);
            });
        }
        return routeDefinitions;
    }

    private List<RouteDefinition> getServiceBRoutesForApplication(Application app) {

        List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
        if (applicationService.getServiceBCRD() != null) {
            List<ServiceB> serviceBList = kubernetesClient.customResources(applicationService.getServiceBCRD(), ServiceB.class,
                    ServiceBList.class, DoneableServiceB.class).list().getItems();
            serviceBList.forEach(serviceB -> {
                RouteDefinition routeDefinition = new RouteDefinition();
                routeDefinition.setId(app.getMetadata().getName() + ":" + serviceB.getMetadata().getName());
                routeDefinition.setUri(URI.create("http://" + serviceB.getSpec().getServiceName()));

                //@TODO: It will be nice to add into the HEADERS the application where the service belongs

                PredicateDefinition predicateDefinition = new PredicateDefinition();
                predicateDefinition.setName("Path");
                String pattern = "/" + app.getMetadata().getName() + "/" + app.getSpec().getVersion() + "/" + SERVICE_B_PATH + "/" + serviceB.getMetadata().getName() + "/**";
                predicateDefinition.addArg("pattern", pattern);
                routeDefinition.getPredicates().add(predicateDefinition);
                routeDefinitions.add(routeDefinition);
                logger.info("Route (id="+app.getMetadata().getName() + ":" + serviceB.getMetadata().getName()+") added: " + pattern);
            });
        }
        return routeDefinitions;
    }


}
