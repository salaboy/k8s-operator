package org.salaboy.jbcnconf.gateway.crds.query;

import io.fabric8.kubernetes.client.CustomResource;

public class ServiceA extends CustomResource {
    private ServiceASpec spec;

    public ServiceASpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceASpec spec) {
        this.spec = spec;
    }

    @Override
    public String toString() {
        return "ServiceA{" +
                "name='" + getMetadata().getName() + '\'' +
                ", spec=" + spec +
                '}';
    }
}
