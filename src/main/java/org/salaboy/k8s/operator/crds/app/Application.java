package org.salaboy.k8s.operator.crds.app;

import io.fabric8.kubernetes.client.CustomResource;

public class Application extends CustomResource {

    private ApplicationSpec spec;

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ApplicationSpec getSpec() {
        return spec;
    }

    public void setSpec(ApplicationSpec spec) {
        this.spec = spec;
    }

    @Override
    public String toString() {
        return "Application{" +
                "spec=" + spec +
                ", status='" + status + '\'' +
                '}';
    }
}
