package org.salaboy.k8s.operator.app;

public class ApplicationCRDs {
    public static String APP_CRD_GROUP = "beta.k8s.salaboy.org";
    public static String APP_CRD_NAME = "applications." + APP_CRD_GROUP;
    public static String SERVICE_A_CRD_GROUP = "beta.k8s.salaboy.org";
    public static String SERVICE_A_CRD_NAME = "service-as." + SERVICE_A_CRD_GROUP;
    public static String SERVICE_B_CRD_GROUP = "beta.k8s.salaboy.org";
    public static String SERVICE_B_CRD_NAME = "service-bs." + SERVICE_B_CRD_GROUP;

}
