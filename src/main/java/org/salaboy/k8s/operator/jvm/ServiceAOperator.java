package org.salaboy.k8s.operator.jvm;

import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import org.salaboy.k8s.operator.jvm.kinds.ServiceA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operator(forKind = ServiceA.class, prefix = "beta.k8s.salaboy.org")
public class ServiceAOperator extends AbstractOperator<ServiceA> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    public ServiceAOperator() {
    }

    protected void onAdd(ServiceA serviceA) {
        log.info("new example has been created: {}", serviceA);
        // todo: implement the logic
        // KubernetesResourceList list = ???
        // client.resourceList(list).createOrReplace();
    }

    protected void onDelete(ServiceA serviceA) {
        log.info("existing example has been deleted: {}", serviceA);
    }

    protected void onModify(ServiceA serviceA) {
        log.info("existing example has been modified: {}", serviceA);
    }
}
