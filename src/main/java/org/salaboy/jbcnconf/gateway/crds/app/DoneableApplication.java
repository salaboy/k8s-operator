package org.salaboy.jbcnconf.gateway.crds.app;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class DoneableApplication extends CustomResourceDoneable<Application> {

    public DoneableApplication(Application resource, Function<Application, Application> function) {
        super(resource, function);
    }
}
