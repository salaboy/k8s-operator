package org.salaboy.k8s.operator.crds.serviceB;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import org.salaboy.k8s.operator.crds.app.CustomService;
import org.salaboy.k8s.operator.crds.app.ServiceSpec;

@JsonDeserialize(
        using = JsonDeserializer.None.class
)
@JsonIgnoreProperties(ignoreUnknown = true)

public class ServiceB extends CustomResource implements CustomService {
    private ServiceSpec spec;

    public ServiceSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }

    public String getKind() {
        return "ServiceB";
    }

    @Override
    public String toString() {
        return "ServiceB{" +
                "name='" + getMetadata().getName() + '\'' +
                ", spec=" + spec +
                '}';
    }
}
