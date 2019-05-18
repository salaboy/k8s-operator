package org.salaboy.k8s.operator.crds.serviceB;

import io.fabric8.kubernetes.client.CustomResource;

public class ServiceB extends CustomResource {
    private ServiceBSpec spec;

    public ServiceBSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceBSpec spec) {
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
