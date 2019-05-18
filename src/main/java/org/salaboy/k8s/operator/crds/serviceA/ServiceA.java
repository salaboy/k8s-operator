package org.salaboy.k8s.operator.crds.serviceA;

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
        return "ServiceB{" +
                "name='" + getMetadata().getName() + '\'' +
                ", spec=" + spec +
                '}';
    }
}
