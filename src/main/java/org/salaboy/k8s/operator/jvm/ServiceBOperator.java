package org.salaboy.k8s.operator.jvm;

import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import org.salaboy.k8s.operator.jvm.kinds.ServiceA;
import org.salaboy.k8s.operator.jvm.kinds.ServiceB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operator(forKind = ServiceA.class, prefix = "beta.k8s.salaboy.org")
public class ServiceBOperator extends AbstractOperator<ServiceB> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    public ServiceBOperator() {
    }

    protected void onAdd(ServiceB serviceB) {
        log.info("new example has been created: {}", serviceB);
        // todo: implement the logic
        // KubernetesResourceList list = ???
        // client.resourceList(list).createOrReplace();
    }

    protected void onDelete(ServiceB serviceB) {
        log.info("existing example has been deleted: {}", serviceB);
    }

    protected void onModify(ServiceB serviceB) {
        log.info("existing example has been modified: {}", serviceB);
    }
}
