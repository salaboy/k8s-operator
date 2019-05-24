package org.salaboy.k8s.operator;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.salaboy.k8s.operator.app.AppService;
import org.salaboy.k8s.operator.core.K8SCoreRuntime;
import org.salaboy.k8s.operator.crds.app.Application;
import org.salaboy.k8s.operator.crds.app.ApplicationList;
import org.salaboy.k8s.operator.crds.app.DoneableApplication;
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

import java.util.List;

import static org.salaboy.k8s.operator.app.AppCRDs.*;

@Service
public class AppsOperator {

    private Logger logger = LoggerFactory.getLogger(AppsOperator.class);
    private CustomResourceDefinition serviceACRD = null;
    private CustomResourceDefinition serviceBCRD = null;
    private CustomResourceDefinition applicationCRD = null;
    private boolean serviceAWatchRegistered = false;
    private boolean serviceBWatchRegistered = false;
    private boolean applicationWatchRegistered = false;

    private String appsResourceVersion;
    private String serviceAsResourceVersion;
    private String serviceBsResourceVersion;

    private NonNamespaceOperation<Application, ApplicationList, DoneableApplication, Resource<Application, DoneableApplication>> appCRDClient;
    private NonNamespaceOperation<ServiceA, ServiceAList, DoneableServiceA, Resource<ServiceA, DoneableServiceA>> serviceACRDClient;
    private NonNamespaceOperation<ServiceB, ServiceBList, DoneableServiceB, Resource<ServiceB, DoneableServiceB>> serviceBCRDClient;


    @Autowired
    private AppService appService;

    @Autowired
    private K8SCoreRuntime k8SCoreRuntime;

    /*
     * Check for Required CRDs
     */
    public boolean areRequiredCRDsPresent() {
        try {

            k8SCoreRuntime.registerCustomKind(SERVICE_A_CRD_GROUP + "/v1", "ServiceA", ServiceA.class);
            k8SCoreRuntime.registerCustomKind(SERVICE_B_CRD_GROUP + "/v1", "ServiceB", ServiceB.class);
            k8SCoreRuntime.registerCustomKind(APP_CRD_GROUP + "/v1", "Application", Application.class);

            CustomResourceDefinitionList crds = k8SCoreRuntime.getCustomResourceDefinitionList();
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
            if (allCRDsFound()) {
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

    /*
     * Init can only be called if all the required CRDs are present
     *  - It creates the CRD clients to be able to watch and execute operations
     *  - It loads the existing resources (current state in the cluster)
     *  - It register the watches for our CRDs
     */
    public boolean init() {
        // Creating CRDs Clients
        appCRDClient = k8SCoreRuntime.customResourcesClient(applicationCRD, Application.class, ApplicationList.class, DoneableApplication.class);
        serviceACRDClient = k8SCoreRuntime.customResourcesClient(serviceACRD, ServiceA.class, ServiceAList.class, DoneableServiceA.class);
        serviceBCRDClient = k8SCoreRuntime.customResourcesClient(serviceBCRD, ServiceB.class, ServiceBList.class, DoneableServiceB.class);

        if (loadExistingResources() && watchOurCRDs()) {
            return true;
        }

        return false;

    }

    /*
     * Check that all the CRDs are found for this operator to work
     */
    private boolean allCRDsFound() {
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
        // Load Existing Applications
        List<Application> applicationList = appCRDClient.list().getItems();
        if (!applicationList.isEmpty()) {
            appsResourceVersion = applicationList.get(0).getMetadata().getResourceVersion();
            logger.info(">> Applications Resource Version: " + appsResourceVersion);
            applicationList.forEach(app -> {
                appService.addApp(app.getMetadata().getName(), app);
                logger.info("> App " + app.getMetadata().getName() + " found.");
            });

        }
        // Load Existing Service As
        List<ServiceA> serviceAList = serviceACRDClient.list().getItems();
        if (!serviceAList.isEmpty()) {
            serviceAsResourceVersion = serviceAList.get(0).getMetadata().getResourceVersion();
            logger.info(">> ServiceA Resource Version: " + serviceAsResourceVersion);
            serviceAList.forEach(serviceA -> {
                appService.addServiceToApp(serviceA);
            });
        }
        // Load Existing Service Bs
        List<ServiceB> serviceBList = serviceBCRDClient.list().getItems();
        if (!serviceBList.isEmpty()) {
            serviceBsResourceVersion = serviceBList.get(0).getMetadata().getResourceVersion();
            logger.info(">> ServiceB Resource Version: " + serviceBsResourceVersion);
            serviceBList.forEach(serviceB -> {
                appService.addServiceToApp(serviceB);
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
     *  - This watch is in charge of adding and removing apps to/from the In memory desired state
     */
    private void registerApplicationWatch() {
        logger.info("> Registering Application CRD Watch");
        appCRDClient.withResourceVersion(appsResourceVersion).watch(new Watcher<Application>() {
            @Override
            public void eventReceived(Watcher.Action action, Application application) {
                System.out.println("==> " + action + " for " + application);
                if (action.equals(Action.ADDED)) {
                    System.out.println(">> Adding a new App: " + application.getMetadata().getName());
                    appService.addApp(application.getMetadata().getName(), application);
                    List<ServiceA> serviceAForAppList = serviceACRDClient.withLabel("app", application.getMetadata().getName()).list().getItems();
                    if (serviceAForAppList != null && !serviceAForAppList.isEmpty()) {
                        serviceAForAppList.forEach(serviceA -> {
                            appService.addServiceToApp(serviceA);
                        });
                    }
                    List<ServiceB> serviceBForAppList = serviceBCRDClient.withLabel("app", application.getMetadata().getName()).list().getItems();
                    if (serviceBForAppList != null && !serviceBForAppList.isEmpty()) {
                        serviceBForAppList.forEach(serviceB -> {
                            appService.addServiceToApp(serviceB);
                        });
                    }
                }
                if (action.equals(Action.DELETED)) {
                    System.out.println(">> Delete a new App");
                    appService.removeApp(application.getMetadata().getName());
                    //application.getMetadata().setOwnerReferences();
                    //@TODO: when creating services add OWNERReferences for GC
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
                if (action.equals(Action.ADDED)) {
                    appService.addServiceToApp(serviceA);
                }
                if (action.equals(Action.DELETED)) {
                    appService.removeServiceFromApp(serviceA);
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
                if (action.equals(Action.ADDED)) {
                    appService.addServiceToApp(serviceB);

                }
                if (action.equals(Action.DELETED)) {
                    appService.removeServiceFromApp(serviceB);

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
     *   matches the desired state with current state in K8s
     */
    public void reconcile() {
        if (appService.getApps().isEmpty()) {
            logger.info("> No Apps found.");
        }
        // For each App Desired State
        appService.getAppsMap().keySet().forEach(appName ->
                {
                    Application application = appService.getApp(appName);
                    if (appService.isAppUp(application)) {
                        logger.info("> App Name: " + appName + " is up and running");
                        application.getSpec().getModules().forEach(m -> logger.info("\t> Module found: " + m));
                        application.getSpec().setStatus("HEALTHY");
                        application.getSpec().setUrl(appService.getAppUrl(appName));
                        logger.info("> App Name: " + appName + " status is UP. \n" + application);
                    } else {
                        logger.error("> App Name: " + appName + " is down due missing services");
                        if (application.getSpec().getModules() == null || application.getSpec().getModules().isEmpty()) {
                            logger.info("App: " + appName + ": No Modules found. " + application);
                        } else {
                            application.getSpec().getModules().forEach(m -> logger.info("\t> Module found: " + m));
                        }
                        application.getSpec().setStatus("UNHEALTHY");
                        application.getSpec().setUrl("N/A");
                        logger.info("> App Name: " + appName + " status is DOWN. \n " + application);
                    }

                    appCRDClient.createOrReplace(application);
                }
        );

    }


    public CustomResourceDefinition getServiceACRD() {
        return serviceACRD;
    }

    public CustomResourceDefinition getServiceBCRD() {
        return serviceBCRD;
    }

    public CustomResourceDefinition getApplicationCRD() {
        return applicationCRD;
    }
}
