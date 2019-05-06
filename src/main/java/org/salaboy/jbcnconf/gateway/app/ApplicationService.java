package org.salaboy.jbcnconf.gateway.app;

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
import org.salaboy.jbcnconf.gateway.crds.app.*;
import org.salaboy.jbcnconf.gateway.crds.query.DoneableServiceA;
import org.salaboy.jbcnconf.gateway.crds.query.ServiceA;
import org.salaboy.jbcnconf.gateway.crds.query.ServiceAList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.salaboy.jbcnconf.gateway.app.ApplicationCRDs.*;

@Service
public class ApplicationService {

    private Logger logger = LoggerFactory.getLogger(ApplicationService.class);
    private CustomResourceDefinition serviceACRD = null;
    private CustomResourceDefinition applicationCRD = null;
    private boolean serviceAWatchRegistered = false;
    private boolean applicationWatchRegistered = false;

    private Map<String, Application> apps = new ConcurrentHashMap<>();
    private String appsResourceVersion;
    private String serviceAsResourceVersion;

    private NonNamespaceOperation<Application, ApplicationList, DoneableApplication, Resource<Application, DoneableApplication>> appCRDClient;
    private NonNamespaceOperation<ServiceA, ServiceAList, DoneableServiceA, Resource<ServiceA, DoneableServiceA>> serviceACRDClient;

    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private IstioClient istioClient;

    public CustomResourceDefinition getServiceACRD() {
        return serviceACRD;
    }

    public CustomResourceDefinition getApplicationCRD() {
        return applicationCRD;
    }

    /*
     * Init by Looking for our CRDs
     *  Then Load existing instances
     *  Then Register watches for our CRDs
     */

    public boolean init() {
        try {
            KubernetesDeserializer.registerCustomKind(SERVICE_A_CRD_GROUP + "/v1", "AService", ServiceA.class);
            KubernetesDeserializer.registerCustomKind(APP_CRD_GROUP + "/v1", "Application", Application.class);

            CustomResourceDefinitionList crds = kubernetesClient.customResourceDefinitions().list();
            for (CustomResourceDefinition crd : crds.getItems()) {
                ObjectMeta metadata = crd.getMetadata();
                if (metadata != null) {
                    String name = metadata.getName();
                    if (SERVICE_A_CRD_NAME.equals(name)) {
                        serviceACRD = crd;
                    }
                    if (APP_CRD_NAME.equals(name)) {
                        applicationCRD = crd;
                    }
                }
            }
            if (!checkAllCRDsFound()) {
                logger.error("> Custom CRDs required to work not found please check your installation!");
                return false;
            }
            // Creating CRDs Clients
            appCRDClient = kubernetesClient.customResources(applicationCRD, Application.class, ApplicationList.class, DoneableApplication.class);
            serviceACRDClient = kubernetesClient.customResources(serviceACRD, ServiceA.class, ServiceAList.class, DoneableServiceA.class);

            if (loadExistingResources() && watchOurCRDs()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Init sequence not done");
        }
        return false;

    }

    /*
     * Check that all the CRDs are found for this operator to work
     */
    private boolean checkAllCRDsFound() {
        if (serviceACRD != null && applicationCRD != null) {
            return true;
        }
        return false;
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
        List<ServiceA> serviceAList = serviceACRDClient.list().getItems();
        if (!serviceAList.isEmpty()) {
            serviceAsResourceVersion = serviceAList.get(0).getMetadata().getResourceVersion();
            logger.info(">> ServiceA Resource Version: " + serviceAsResourceVersion);
            serviceAList.forEach(serviceA -> {
                String appName = serviceA.getMetadata().getLabels().get("app");
                if (appName != null && !appName.isEmpty()) {
                    Application application = apps.get(appName);
                    ApplicationSpec spec = application.getSpec();
                    Set<ModuleDescr> modules = spec.getModules();
                    if (modules == null) {
                        modules = new HashSet<>();
                    }
                    modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "AService"));
                    spec.setModules(modules);
                    application.setSpec(spec);
                    appCRDClient.createOrReplace(application);
                    logger.info("> Application: " + appName + " updated with ServiceA " + serviceA.getMetadata().getName());
                } else {
                    logger.error("> Orphan ServiceA: " + serviceA.getMetadata().getName());
                }
            });
        }

        return true;

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
                if (action.equals(Action.ADDED) || action.equals(Action.MODIFIED)) {
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
                            modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "AService"));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            appCRDClient.createOrReplace(application);
                            logger.info("> Application: " + application.getMetadata().getName() + " updated with ServiceA " + serviceA.getMetadata().getName());
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

                            modules.add(new ModuleDescr(serviceA.getMetadata().getName(), "AService"));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            appCRDClient.createOrReplace(application);
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
                            modules.removeIf(m -> m.getKind().equals("AService") && m.getName().equals(serviceA.getMetadata().getName()));
                            spec.setModules(modules);
                            application.setSpec(spec);
                            appCRDClient.createOrReplace(application);
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
     * Reconcile contains the logic that understand how services relates to applications and the application state
     *   matches the desired state with current state
     */
    public void reconcile() {
        if (apps.isEmpty()) {
            logger.info("> No Apps found.");
        }
        apps.forEach((s, application) -> {
            logger.info("> Scanning App: " + s);
            if (application.getSpec().getModules() == null || application.getSpec().getModules().isEmpty()) {
                logger.error("> App Name: " + s + " is down due it's serviceA is not present");
            } else {
                logger.info("> App Name: " + s + " is up and running");
                application.getSpec().getModules().forEach(m -> logger.info("\t> Module found: " + m));

            }
        });

    }
}
