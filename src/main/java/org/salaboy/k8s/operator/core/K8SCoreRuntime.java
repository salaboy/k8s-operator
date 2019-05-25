package org.salaboy.k8s.operator.core;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import me.snowdrop.istio.client.IstioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class K8SCoreRuntime {

    private Logger logger = LoggerFactory.getLogger(K8SCoreRuntime.class);

    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private IstioClient istioClient;

    private String externalIP = "N/A";

    public void registerCustomKind(String apiVersion, String kind, Class<? extends KubernetesResource> clazz) {
        KubernetesDeserializer.registerCustomKind(apiVersion, kind, clazz);
    }

    public CustomResourceDefinitionList getCustomResourceDefinitionList() {
        return kubernetesClient.customResourceDefinitions().list();
    }

    public <T extends HasMetadata, L extends KubernetesResourceList, D extends Doneable<T>> MixedOperation<T, L, D, Resource<T, D>> customResourcesClient(CustomResourceDefinition crd, Class<T> resourceType, Class<L> listClass, Class<D> doneClass) {
        return kubernetesClient.customResources(crd, resourceType, listClass, doneClass);
    }

    public String findGatewayExternalIP() {
        if (externalIP.equals("N/A")) {
            ServiceList list = kubernetesClient.services().inNamespace("istio-system").list();
            for (io.fabric8.kubernetes.api.model.Service s : list.getItems()) {
                if (s.getMetadata().getName().equals("istio-ingressgateway")) {
                    List<LoadBalancerIngress> ingress = s.getStatus().getLoadBalancer().getIngress();
                    if (ingress.size() == 1) {
                        externalIP = ingress.get(0).getIp();
                    }
                } else {
                    logger.error(">> Trying to resolve External IP from istio-ingressgateway failed. There will be no external IP for your apps.");
                }
            }
        }
        return externalIP;
    }

}
