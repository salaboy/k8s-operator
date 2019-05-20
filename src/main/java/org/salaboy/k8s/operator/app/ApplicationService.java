package org.salaboy.k8s.operator.app;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import me.snowdrop.istio.client.IstioClient;
import org.salaboy.k8s.operator.crds.app.*;
import org.salaboy.k8s.operator.crds.serviceA.DoneableServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceA;
import org.salaboy.k8s.operator.crds.serviceA.ServiceAList;
import org.salaboy.k8s.operator.crds.serviceB.DoneableServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceB;
import org.salaboy.k8s.operator.crds.serviceB.ServiceBList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.salaboy.k8s.operator.app.ApplicationCRDs.*;

@Service
public class ApplicationService {

    private Logger logger = LoggerFactory.getLogger(ApplicationService.class);
    private CustomResourceDefinition serviceACRD = null;
    private CustomResourceDefinition serviceBCRD = null;
    private CustomResourceDefinition applicationCRD = null;
    private boolean serviceAWatchRegistered = false;
    private boolean serviceBWatchRegistered = false;
    private boolean applicationWatchRegistered = false;

    private Map<String, Application> apps = new ConcurrentHashMap<>();
    private String appsResourceVersion;
    private String serviceAsResourceVersion;
    private String serviceBsResourceVersion;

    private NonNamespaceOperation<Application, ApplicationList, DoneableApplication, Resource<Application, DoneableApplication>> appCRDClient;
    private NonNamespaceOperation<ServiceA, ServiceAList, DoneableServiceA, Resource<ServiceA, DoneableServiceA>> serviceACRDClient;
    private NonNamespaceOperation<ServiceB, ServiceBList, DoneableServiceB, Resource<ServiceB, DoneableServiceB>> serviceBCRDClient;

    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private IstioClient istioClient;

    public CustomResourceDefinition getServiceACRD() {
        return serviceACRD;
    }

    public CustomResourceDefinition getServiceBCRD() {
        return serviceBCRD;
    }

    public CustomResourceDefinition getApplicationCRD() {
        return applicationCRD;
    }

    /*
     * Init by Looking for our CRDs
     *  Then Load existing instances
     *  Then Register watches for our CRDs
     */
    public boolean checkForRequiredCRDs() {
        try {
            KubernetesDeserializer.registerCustomKind(SERVICE_A_CRD_GROUP + "/v1", "ServiceA", ServiceA.class);
            KubernetesDeserializer.registerCustomKind(SERVICE_B_CRD_GROUP + "/v1", "ServiceB", ServiceB.class);
            KubernetesDeserializer.registerCustomKind(APP_CRD_GROUP + "/v1", "Application", Application.class);

            CustomResourceDefinitionList crds = kubernetesClient.customResourceDefinitions().list();
            for (CustomResourceDefinition crd : crds.getItems()) {
                ObjectMeta metadata = crd.getMetadata();
                if (metadata != null) {
                    String name = metadata.getName();
                    if (SERVICE_A_CRD_NAME.equals(name)) {
                        serviceACRD = crd;
                    }
                    if (SERVICE_B_CRD_NAME.equals(name)) {
                        serviceBCRD = crd;
                    }
                    if (APP_CRD_NAME.equals(name)) {
                        applicationCRD = crd;
                    }
                }
            }
            if (checkAllCRDsFound()) {
                return true;
            } else {
                logger.error("> Custom CRDs required to work not found please check your installation!");
                logger.error("\t > App CRD: " + applicationCRD);
                logger.error("\t > ServiceA CRD: " + serviceACRD);
                logger.error("\t > ServiceB CRD: " + serviceBCRD);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("> Init sequence not done");
        }
        return false;
    }

    // Init can only be called if all the required CRDs are present
    public boolean init() {
        // Creating CRDs Clients
        appCRDClient = kubernetesClient.customResources(applicationCRD, Application.class, ApplicationList.class, DoneableApplication.class);
        serviceACRDClient = kubernetesClient.customResources(serviceACRD, ServiceA.class, ServiceAList.class, DoneableServiceA.class);
        serviceBCRDClient = kubernetesClient.customResources(serviceBCRD, ServiceB.class, ServiceBList.class, DoneableServiceB.class);

        if (loadExistingResources() && watchOurCRDs()) {
            return true;
        }


        return false;

    }

    /*
     * Check that all the CRDs are found for this operator to work
     */
    private boolean checkAllCRDsFound() {
        if (serviceACRD == null || applicationCRD == null || serviceBCRD == null) {
            return false;
        }
        return true;
    }

    /*
     * Watch our CRDs
     *  Register watches if they were not registered yet
     */
    private boolean watchOurCRDs() {
        // Watch for our CRDs
        if (!serviceAWatchRegistered) {
            registerServiceAWatch();
        }
        if (!serviceBWatchRegistered) {
            registerServiceBWatch();
        }
        if (!applicationWatchRegistered) {
            registerApplicationWatch();
        }
        if (checkAllCRDsWatchRegistered()) {
            logger.info("> All CRDs Found, init complete");
            return true;
        } else {
            logger.error("> CRDs missing, check your installation and run init again");
            return false;
        }
    }

    /*
     * Load existing instances of our CRDs
     *  - This checks the existing resources and make sure that they are loaded correctly
     *  - This also performs the binding of a service to its app
     */
    private boolean loadExistingResources() {

        List<Application> applicationList = appCRDClient.list().getItems();
        if (!applicationList.isEmpty()) {
            appsResourceVersion = applicationList.get(0).getMetadata().getResourceVersion();
            logger.info(">> Applications Resource Version: " + appsResourceVersion);
            applicationList.forEach(app -> {
                apps.put(app.getMetadata().getName(), app);
                logger.info("> App " + app.getMetadata().getName() + " found.");
            });

        }
        //@TODO: remove duplication between services
        List<ServiceA> serviceAList = serviceACRDClient.list().getItems();
        if (!serviceAList.isEmpty()) {
            serviceAsResourceVersion = serviceAList.get(0).getMetadata().getResourceVersion();
            logger.info(">> ServiceA Resource Version: " + serviceAsResourceVersion);
            serviceAList.forEach(serviceA -> {
                String appName = serviceA.getMetadata().getLabels().get("app");
                if (appName != null && !appName.isEmpty()) {
                    Application application = apps.get(appName);
                    if (application != null) {
                        ApplicationSpec spec = application.getSpec();
                        Set<ModuleDescr> modules = spec.getModules();
                        if (modules == null) {
                            modules = new HashSet<>();
                        }
                        //@TODO: i should check that the k8s service exist before adding the module
                        //@TODO: i should check that the k8s deployment exist before adding the module
                        //@TODO: i should update the k8s deployment to make sure that services are configured for the app
                        modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "ServiceA", serviceA.getSpec().getServiceName()));
                        spec.setModules(modules);
                        application.setSpec(spec);
                        apps.put(application.getMetadata().getName(), application);
                        logger.info("> Application: " + appName + " updated with ServiceA " + serviceA.getMetadata().getName());
                    }
                } else {
                    logger.error("> Orphan ServiceA: " + serviceA.getMetadata().getName());
                }
            });
        }
        List<ServiceB> serviceBList = serviceBCRDClient.list().getItems();
        if (!serviceBList.isEmpty()) {
            serviceBsResourceVersion = serviceBList.get(0).getMetadata().getResourceVersion();
            logger.info(">> ServiceB Resource Version: " + serviceBsResourceVersion);
            serviceBList.forEach(serviceB -> {
                String appName = serviceB.getMetadata().getLabels().get("app");
                if (appName != null && !appName.isEmpty()) {
                    Application application = apps.get(appName);
                    if (application != null) {
                        ApplicationSpec spec = application.getSpec();
                        Set<ModuleDescr> modules = spec.getModules();
                        if (modules == null) {
                            modules = new HashSet<>();
                        }
                        //@TODO: i should check that the k8s service exist before adding the module
                        //@TODO: i should check that the k8s deployment exist before adding the module
                        //@TODO: i should update the k8s deployment to make sure that services are configured for the app
                        modules.add(new ModuleDescr(serviceB.getMetadata().getName(), "ServiceB", serviceB.getSpec().getServiceName()));
                        spec.setModules(modules);
                        application.setSpec(spec);
                        apps.put(application.getMetadata().getName(), application);
                        logger.info("> Application: " + appName + " updated with ServiceB " + serviceB.getMetadata().getName());
                    }
                } else {
                    logger.error("> Orphan ServiceB: " + serviceB.getMetadata().getName());
                }
            });

        }

        return true;

    }

    private boolean validateK8sService(String serviceName) {
        // kubernetesClient.services().withLabel()
        return false;
    }

    /*
     * Check that all the CRDs are being watched for changes
     */
    private boolean checkAllCRDsWatchRegistered() {
        if (applicationWatchRegistered && serviceAWatchRegistered) {
            return true;
        }
        return false;
    }

    /*
     * Register Application Watch
     *  - This watch is in charge of adding and removing apps from the In memory cache
     */
    private void registerApplicationWatch() {
        logger.info("> Registering Application CRD Watch");
        appCRDClient.withResourceVersion(appsResourceVersion).watch(new Watcher<Application>() {
            @Override
            public void eventReceived(Watcher.Action action, Application application) {
                System.out.println("==> " + action + " for " + application);
                if (action.equals(Action.ADDED)) {
                    System.out.println(">> Adding a new App");
                    apps.put(application.getMetadata().getName(), application);
                    List<ServiceA> serviceAForAppList = serviceACRDClient.withLabel("app", application.getMetadata().getName()).list().getItems();
                    if (serviceAForAppList != null && !serviceAForAppList.isEmpty()) {
                        serviceAForAppList.forEach(serviceA -> {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }
                            modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "ServiceA", serviceA.getSpec().getServiceName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info("> Application: " + application.getMetadata().getName() + " updated with ServiceA " + serviceA.getMetadata().getName());
                        });
                    }
                    List<ServiceB> serviceBForAppList = serviceBCRDClient.withLabel("app", application.getMetadata().getName()).list().getItems();
                    if (serviceBForAppList != null && !serviceBForAppList.isEmpty()) {
                        serviceBForAppList.forEach(serviceB -> {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }
                            modules.add(new ModuleDescr(serviceB.getMetadata().getName(), "ServiceB", serviceB.getSpec().getServiceName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info("> Application: " + application.getMetadata().getName() + " updated with ServiceB " + serviceB.getMetadata().getName());
                        });
                    }
                }
                if (action.equals(Action.DELETED)) {
                    System.out.println(">> Delete a new App");
                    apps.remove(application.getMetadata().getName());
                }

                if (application.getSpec() == null) {
                    System.out.println("No Spec for resource " + application);
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
            }
        });
        applicationWatchRegistered = true;

    }

    /*
     * Register Service A Watch
     */

    private void registerServiceAWatch() {
        logger.info("> Registering Service A CRD Watch");
        serviceACRDClient.withResourceVersion(serviceAsResourceVersion).watch(new Watcher<ServiceA>() {
            @Override
            public void eventReceived(Watcher.Action action, ServiceA serviceA) {
                System.out.println("==> " + action + " for " + serviceA);
                if (action.equals(Action.ADDED)) {
                    String appName = serviceA.getMetadata().getLabels().get("app");
                    if (appName != null && !appName.isEmpty()) {
                        Application application = apps.get(appName);
                        if (application != null) {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }

                            modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "ServiceA", serviceA.getSpec().getServiceName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info(">> Added ServiceA " + serviceA.getMetadata().getName() + " to app " + appName);
                        }
                    }

                }
                if (action.equals(Action.DELETED)) {
                    String appName = serviceA.getMetadata().getLabels().get("app");
                    if (appName != null && !appName.isEmpty()) {
                        Application application = apps.get(appName);
                        if (application != null) {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }
                            modules.removeIf(m -> m.getKind().equals("ServiceA") && m.getName().equals(serviceA.getMetadata().getName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info(">> Deleted ServiceA " + serviceA.getMetadata().getName() + " from app " + appName);
                        }
                    }

                }
                if (serviceA.getSpec() == null) {
                    logger.error("No Spec for resource " + serviceA);
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
            }
        });
        serviceAWatchRegistered = true;
    }

    /*
     * Register Service A Watch
     */

    private void registerServiceBWatch() {
        logger.info("> Registering Service B CRD Watch");
        serviceBCRDClient.withResourceVersion(serviceBsResourceVersion).watch(new Watcher<ServiceB>() {
            @Override
            public void eventReceived(Watcher.Action action, ServiceB serviceB) {
                System.out.println("==> " + action + " for " + serviceB);
                if (action.equals(Action.ADDED)) {
                    String appName = serviceB.getMetadata().getLabels().get("app");
                    if (appName != null && !appName.isEmpty()) {
                        Application application = apps.get(appName);
                        if (application != null) {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }

                            modules.add(new ModuleDescr(serviceB.getMetadata().getName(), "ServiceB", serviceB.getSpec().getServiceName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info(">> Added ServiceB " + serviceB.getMetadata().getName() + " to app " + appName);
                        }
                    }

                }
                if (action.equals(Action.DELETED)) {
                    String appName = serviceB.getMetadata().getLabels().get("app");
                    if (appName != null && !appName.isEmpty()) {
                        Application application = apps.get(appName);
                        if (application != null) {
                            ApplicationSpec spec = application.getSpec();
                            Set<ModuleDescr> modules = spec.getModules();
                            if (modules == null) {
                                modules = new HashSet<>();
                            }
                            modules.removeIf(m -> m.getKind().equals("ServiceB") && m.getName().equals(serviceB.getMetadata().getName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            apps.put(application.getMetadata().getName(), application);
                            logger.info(">> Deleted ServiceB " + serviceB.getMetadata().getName() + " from app " + appName);
                        }
                    }

                }
                if (serviceB.getSpec() == null) {
                    logger.error("No Spec for resource " + serviceB);
                }
            }

            @Override
            public void onClose(KubernetesClientException cause) {
            }
        });
        serviceAWatchRegistered = true;
    }


    /*
     * Reconcile contains the logic that understand how services relates to applications and the application state
     *   matches the desired state with current state
     */
    public void reconcile() {
        if (apps.isEmpty()) {
            logger.info("> No Apps found.");
        }
        apps.keySet().forEach(appName ->
                {
                    Application application = apps.get(appName);
                    if (checkApplicationStatus(application)) {
                        logger.info("> App Name: " + appName + " is up and running");
                        application.getSpec().getModules().forEach(m -> logger.info("\t> Module found: " + m));
                        application.setStatus("UP");
                    } else {
                        logger.error("> App Name: " + appName + " is down due missing services");
                        if (application.getSpec().getModules() == null || application.getSpec().getModules().isEmpty()) {
                            logger.info("App: " + appName + ": No Modules found. ");
                        } else {
                            application.getSpec().getModules().forEach(m -> logger.info("\t> Module found: " + m));
                        }
                        application.setStatus("DOWN");
                    }
                    appCRDClient.createOrReplace(application);
                }
        );

    }

    private boolean checkApplicationStatus(Application app) {
        Set<ModuleDescr> modules = app.getSpec().getModules();
        if (modules != null) {
            ModuleDescr serviceAModuleDescr = modules.stream().filter(m -> m.getKind().equals("ServiceA")).findAny().orElse(null);
            ModuleDescr serviceBModuleDescr = modules.stream().filter(m -> m.getKind().equals("ServiceB")).findAny().orElse(null);
            if (serviceAModuleDescr != null && serviceBModuleDescr != null) {
                return true;
            }
        }
        return false;
    }

    public List<String> getApps() {
        return apps.values().stream()
                .filter(app -> checkApplicationStatus(app))
                .map(a -> a.getMetadata().getName())
                .collect(Collectors.toList());
    }
}
