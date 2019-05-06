package org.salaboy.jbcnconf.gateway.crds.app;

import io.fabric8.kubernetes.client.CustomResource;

public class Application extends CustomResource {

    private ApplicationSpec spec;

    public ApplicationSpec getSpec() {
        return spec;
    }

    public void setSpec(ApplicationSpec spec) {
        this.spec = spec;
    }

    @Override
    public String toString() {
        return "Application{" +
                "name='" + getMetadata().getName() + '\'' +
                ", spec=" + spec +
                '}';
    }
}
