//package org.integratedmodelling.klab.runtime.libraries;
//
//import org.integratedmodelling.klab.api.collections.Pair;
//import org.integratedmodelling.klab.api.collections.Parameters;
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
//import org.integratedmodelling.klab.api.services.resources.ResourceSet;
//import org.integratedmodelling.klab.components.ComponentRegistry;
//import org.integratedmodelling.klab.services.base.BaseService;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentMatchers;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//
//import java.io.File;
//
//import static org.mockito.ArgumentMatchers.any;
//
//class ComponentIOLibraryTest {
//
//  @Mock private BaseService service = Mockito.mock(BaseService.class);
//
//  @Mock private ComponentRegistry registry = Mockito.mock(ComponentRegistry.class);
//
//  @BeforeEach
//  void init() {
//    Mockito.when(service.getComponentRegistry()).thenReturn(registry);
//    Mockito.when(registry.installComponent(any(), ArgumentMatchers.anyString()))
//        .thenReturn(Pair.of(null, ResourceSet.of(new ResourceInfo(), Version.ANY_VERSION, null)));
//  }
//
////  @Test
////  void importComponentDirectKar() {
////    Parameters params = Parameters.create();
////    File karFile =
////        new File("src/test/resources/klab.component.geospatial-1.0-SNAPSHOT-component.kar");
////
////    var result = ComponentIOLibrary.importComponentDirect(params, karFile, service, null);
////
////    Assertions.assertFalse(result.isEmpty());
////  }
//
//  @Test
//  void importComponentMaven() {}
//}
