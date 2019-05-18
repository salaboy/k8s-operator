package org.salaboy.k8s.operator;

import org.salaboy.k8s.operator.app.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@EnableDiscoveryClient
@SpringBootApplication
@EnableScheduling
@RestController
public class OperatorApplication implements CommandLineRunner {
    Logger logger = LoggerFactory.getLogger(OperatorApplication.class);

    @Autowired
    private DiscoveryClient discoveryClient;


    @Autowired
    private ApplicationService appService;

    private boolean initDone = false;

    public static void main(String[] args) {
        SpringApplication.run(OperatorApplication.class,
                args);
    }


    @Override
    public void run(String... args) throws Exception {

        initDone = appService.init();
        logger.info("> App Service Init.");

    }

   // @TODO: manage CRD status for Application .. make sure that when an application is down it is removed from the app list

    @GetMapping("/")
    public Collection<String> appList() {
        return appService.getApps();
    }


    //        System.out.println(" -------------------------- ");
//
//        List<String> services = discoveryClient.getServices();
//        services.forEach((s) -> System.out.println("> Service: " + s));
//
//        System.out.println(" -------------------------- ");
//
//        CustomResourceDefinitionList crdList = kubernetesClient.customResourceDefinitions().list();
//        crdList.getItems().forEach((crd) -> System.out.println("> CRD: { name: " + crd.getMetadata().getName() + " ,kind: " + crd.getKind() + ", apiVersion:" + crd.getApiVersion() + " }"));
//
//        System.out.println(" -------------------------- ");
//
//        VirtualServiceList virtualServiceList = istioClient.virtualService().list();
//        virtualServiceList.getItems().forEach((vs) -> System.out.println("> VS: { name: " + vs.getMetadata().getName() + ", kind: " + vs.getKind() + " }"));
//
//
//        System.out.println(" -------------------------- ");
//
//        PolicyList policyList = istioClient.policy().list();
//        policyList.getItems().forEach((p) -> System.out.println(" Policy: { name: " + p.getMetadata().getName() + ", kind: " + p.getKind() + " }"));
//
//
//        System.out.println(" -------------------------- ");

//    List<String> services = discoveryClient.getServices();
//        for (String s : services) {
//        System.out.println("Calling: http://" + s + ".knativetutorial.svc.cluster.local");
//        WebClient webClient = WebClient.builder().baseUrl("http://" + s + ".knativetutorial.svc.cluster.local").build();
//        webClient.get().uri("/").retrieve()
//                .bodyToMono(String.class).subscribe(System.out::print);
//
//    }

    @Scheduled(fixedDelay = 10000)
    public void reconcileLoop() {
        //@TODO: if someone remove the CRDs we need to execute init again
        if (!initDone) {
            logger.info("> Performing Init ...");
            initDone = appService.init();
        }
        if (initDone) {
            logger.info("> Reconciling Apps ...");
            appService.reconcile();
        }
    }

}
