package org.salaboy.jbcnconf.gateway.crds.query;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class DoneableServiceA extends CustomResourceDoneable<ServiceA> {

    public DoneableServiceA(ServiceA resource, Function<ServiceA, ServiceA> function) {
        super(resource, function);
    }
}
