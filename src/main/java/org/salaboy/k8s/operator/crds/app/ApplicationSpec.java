package org.salaboy.k8s.operator.crds.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.Set;

@JsonDeserialize(
        using = JsonDeserializer.None.class
)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationSpec implements KubernetesResource {

    private String version;
    private String selector;

    private Set<ModuleDescr> modules;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public Set<ModuleDescr> getModules() {
        return modules;
    }

    public void setModules(Set<ModuleDescr> modules) {
        this.modules = modules;
    }

    @Override
    public String toString() {
        return "ApplicationSpec{" +
                "version='" + version + '\'' +
                ", selector='" + selector + '\'' +
                ", modules=" + modules +
                '}';
    }
}
