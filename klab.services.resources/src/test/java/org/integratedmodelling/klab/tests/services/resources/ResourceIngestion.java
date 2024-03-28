//package org.integratedmodelling.klab.tests.services.resources;
//
//import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
//import org.integratedmodelling.klab.api.lang.kim.KimObservable;
//import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
//import org.integratedmodelling.klab.api.services.KlabService;
//import org.integratedmodelling.klab.services.ServiceStartupOptions;
//import org.integratedmodelling.klab.services.resources.ResourcesProvider;
//import org.integratedmodelling.klab.services.resources.embedded.ResourcesServiceInstance;
//import org.integratedmodelling.klab.utilities.Utils;
//import org.junit.jupiter.api.AfterAll;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//
//class ResourceIngestion {
//
//    private static ResourcesServiceInstance service;
//
//    //
//    //    private static ResourcesProvider service = null;
//    //
//    //    /*
//    //     * TODO substitute with online service filled in with as many observable use cases as possible
//    //     */
//    private static String[] testObservables = {"geography:Elevation in m", "geography:Elevation optional",
//                                               "geography:Elevation in m optional", "any geography" +
//                                                       ":Elevation in m", "geography:Elevation in m > 100",
//                                               "geography:Elevation in m by landcover:LandCoverType"};
//
//    //
//    @BeforeAll
//    static void setUp() throws Exception {
//
//        service = new ResourcesServiceInstance();
//        service.start(ServiceStartupOptions.testOptions(KlabService.Type.RESOURCES));
//        if (!service.waitOnline(10)) {
//            throw new KlabResourceAccessException("Cannot start server within 10 seconds");
//        }
//
//        ////        ((ResourcesProvider)service).loadWorkspaces();
//        //        service.addProject("worldview", "https://bitbucket.org/integratedmodelling/im.git#develop",
//        //        false);
//        //        service.addProject("tests", "https://bitbucket.org/integratedmodelling/im.testsuite.resolution",
//        //        false);
//    }
//
//    @AfterAll
//    static void tearDown() throws Exception {
//        service.stop();
//    }
//
//    @Test
//    void doNothing() {
//    }
//
//    //
//    //    @Test
//    //    void parseObservables() {
//    //
//    //        for (String observable : testObservables) {
//    //            KimObservable obs = service.resolveObservable(observable);
//    //            System.out.println(obs);
//    //            assert (obs != null);
//    //        }
//    //    }
//    //
//    //    @Test
//    void serializeAndDeserializeObservables() {
//
//        for (String observable : testObservables) {
//            KimObservable obs = service.klabService().resolveObservable(observable);
//            String serialized = Utils.Json.asString(obs);
//            KlabStatement kimObject = Utils.Json.parseObject(serialized, KlabStatement.class);
//            assert (kimObject instanceof KimObservable);
//        }
//    }
//    //
//}
