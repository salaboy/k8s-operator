package org.salaboy.k8s.operator.crds.serviceB;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class DoneableServiceB extends CustomResourceDoneable<ServiceB> {

    public DoneableServiceB(ServiceB resource, Function<ServiceB, ServiceB> function) {
        super(resource, function);
    }
}
