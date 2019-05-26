package org.salaboy.k8s.operator.app;

import org.salaboy.k8s.operator.core.K8SCoreRuntime;
import org.salaboy.k8s.operator.crds.app.Application;
import org.salaboy.k8s.operator.crds.app.ApplicationSpec;
import org.salaboy.k8s.operator.crds.app.CustomService;
import org.salaboy.k8s.operator.crds.app.ModuleDescr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AppService {
    private Logger logger = LoggerFactory.getLogger(AppService.class);
    private Map<String, Application> apps = new ConcurrentHashMap<>();
    private Map<String, String> appsUrls = new HashMap<>();

    @Autowired
    private K8SCoreRuntime k8SCoreRuntime;

    /*
     * Add the logic to define what are the rules for your application to be UP or DOWN
     */
    public boolean isAppHealthy(Application app) {
        Set<ModuleDescr> modules = app.getSpec().getModules();
        if (modules != null) {
            ModuleDescr serviceAModuleDescr = modules.stream().filter(m -> m.getKind().equals("ServiceA")).findAny().orElse(null);
            ModuleDescr serviceBModuleDescr = modules.stream().filter(m -> m.getKind().equals("ServiceB")).findAny().orElse(null);
            if (serviceAModuleDescr != null && serviceBModuleDescr != null) {
                return true;
            }
        }
        return false;
    }

    public void addServiceToApp(CustomService service) {
        String appName = service.getMetadata().getLabels().get("app");
        if (appName != null && !appName.isEmpty()) {
            Application application = apps.get(appName);
            if (application != null) {
                ApplicationSpec spec = application.getSpec();
                Set<ModuleDescr> modules = spec.getModules();
                if (modules == null) {
                    modules = new HashSet<>();
                }
                modules.add(new ModuleDescr(service.getMetadata().getName(), service.getKind(), service.getSpec().getServiceName()));
                spec.setModules(modules);
                application.setSpec(spec);
                apps.put(application.getMetadata().getName(), application);
                logger.info("> Application: " + appName + " updated with Service " + service.getMetadata().getName());

            }
        } else {
            logger.error("> Orphan Service: " + service.getMetadata().getName());
        }
    }

    public void removeServiceFromApp(CustomService service) {
        String appName = service.getMetadata().getLabels().get("app");
        if (appName != null && !appName.isEmpty()) {
            Application application = apps.get(appName);
            if (application != null) {
                ApplicationSpec spec = application.getSpec();
                Set<ModuleDescr> modules = spec.getModules();
                if (modules == null) {
                    modules = new HashSet<>();
                }
                modules.removeIf(m -> m.getKind().equals(service.getKind()) && m.getName().equals(service.getMetadata().getName()));
                spec.setModules(modules);
                application.setSpec(spec);
                apps.put(application.getMetadata().getName(), application);
                logger.info(">> Deleted Service " + service.getMetadata().getName() + " from app " + appName);
            }
        }
    }


    public List<String> getApps() {
        return apps.values().stream()
                .filter(app -> isAppHealthy(app))
                .map(a -> a.getMetadata().getName())
                .collect(Collectors.toList());
    }

    public void addApp(String appName, Application app) {
        apps.put(appName, app);
    }

    public Application removeApp(String appName) {
        return apps.remove(appName);
    }

    public Application getApp(String appName) {
        return apps.get(appName);
    }

    public String getAppUrl(String appName) {
        return appsUrls.get(appName);
    }

    public void addAppUrl(String appName, String url) {
        appsUrls.put(appName, url);
    }

    public Map<String, Application> getAppsMap() {
        return apps;
    }

    public Map<String, String> getAppsUrls() {
        return appsUrls;
    }
}
