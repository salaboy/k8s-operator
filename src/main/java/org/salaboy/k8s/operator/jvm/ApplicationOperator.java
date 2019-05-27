package org.salaboy.k8s.operator.jvm;

import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import org.salaboy.k8s.operator.app.AppService;
import org.salaboy.k8s.operator.core.K8SCoreRuntime;
import org.salaboy.k8s.operator.crds.serviceA.ServiceA;
import org.salaboy.k8s.operator.crds.serviceB.ServiceB;
import org.salaboy.k8s.operator.jvm.kinds.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Operator(forKind = Application.class, prefix = "beta.k8s.salaboy.org")
public class ApplicationOperator extends AbstractOperator<Application> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractOperator.class.getName());

    @Autowired
    private AppService appService;

    @Autowired
    private K8SCoreRuntime k8SCoreRuntime;

    public ApplicationOperator() {
    }

    protected void onAdd(Application app) {
        log.info("new example has been created: {}", app);
        logger.info(">> Adding App: " + app.getName());
        appService.addApp(app.getName(), app);
        List<ServiceA> serviceAForAppList = serviceACRDClient.withLabel("app", app.getName()).list().getItems();
        if (serviceAForAppList != null && !serviceAForAppList.isEmpty()) {
            serviceAForAppList.forEach(serviceA -> {
                appService.addServiceToApp(serviceA);
            });
        }
        List<ServiceB> serviceBForAppList = serviceBCRDClient.withLabel("app", app.getName()).list().getItems();
        if (serviceBForAppList != null && !serviceBForAppList.isEmpty()) {
            serviceBForAppList.forEach(serviceB -> {
                appService.addServiceToApp(serviceB);
            });
        }
        // todo: implement the logic
        // KubernetesResourceList list = ???
        // client.resourceList(list).createOrReplace();
    }

    protected void onDelete(Application app) {
        log.info("existing example has been deleted: {}", app);
    }

    protected void onModify(Application app) {
        log.info("existing example has been modified: {}", app);
    }
}
