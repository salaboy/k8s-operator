package org.salaboy.jbcnconf.gateway;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.salaboy.jbcnconf.gateway.app.ApplicationService;
import org.salaboy.jbcnconf.gateway.crds.app.Application;
import org.salaboy.jbcnconf.gateway.crds.app.ApplicationList;
import org.salaboy.jbcnconf.gateway.crds.app.DoneableApplication;
import org.salaboy.jbcnconf.gateway.crds.app.ModuleDescr;
import org.salaboy.jbcnconf.gateway.crds.query.DoneableServiceA;
import org.salaboy.jbcnconf.gateway.crds.query.ServiceA;
import org.salaboy.jbcnconf.gateway.crds.query.ServiceAList;
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

    public static final String APPS_PATH = "apps";
    public static final String SERVICE_A_PATH = "service-a";
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
                    List<RouteDefinition> runtimeBundleRoutes = getServiceARoutesForApplication(app);
                    appRouteDefinitions.addAll(runtimeBundleRoutes);


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

    private boolean isApplicationReady(Application app, List<RouteDefinition> appRouteDefinitions) {
        Set<ModuleDescr> modules = app.getSpec().getModules();
        final AtomicInteger validated = new AtomicInteger();
        System.out.println("> App: " + app.getMetadata().getName() + " validation!");
        modules.forEach(md -> {
            appRouteDefinitions.forEach(rd -> {
                logger.info("\t > Checking : " + rd.getId() + " === " + app.getMetadata().getName() + ":" + md.getName() + "-> " + rd.getId().equals(app.getMetadata().getName() + ":" + md.getName()));
                if (rd.getId().equals(app.getMetadata().getName() + ":" + md.getName())) {
                    validated.incrementAndGet();
                }
            });
        });
        System.out.println("> Modules size: " + modules.size() + " and validated: " + validated);
        if (validated.get() == modules.size()) {
            return true;
        }

        return false;

    }

    private List<RouteDefinition> getServiceARoutesForApplication(Application app) {

        List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
        if (applicationService.getServiceACRD() != null) {
            List<ServiceA> serviceAList = kubernetesClient.customResources(applicationService.getServiceACRD(), ServiceA.class,
                    ServiceAList.class, DoneableServiceA.class).list().getItems();
            serviceAList.forEach(serviceA -> {
                RouteDefinition routeDefinition = new RouteDefinition();
                routeDefinition.setId(app.getMetadata().getName() + ":" + serviceA.getMetadata().getName());
                routeDefinition.setUri(URI.create("http://" + serviceA.getSpec().getServiceName()));
                PredicateDefinition predicateDefinition = new PredicateDefinition();
                predicateDefinition.setName("Path");
                predicateDefinition.addArg("pattern", "/" + APPS_PATH + "/" + app.getMetadata().getName() + "/" + app.getSpec().getVersion() + "/" + SERVICE_A_PATH + "/" + serviceA.getMetadata().getName() + "/**");
                routeDefinition.getPredicates().add(predicateDefinition);
                routeDefinitions.add(routeDefinition);
            });
        }
        return routeDefinitions;
    }


}
