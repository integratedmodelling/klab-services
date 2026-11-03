package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.services.reasoner.controllers.SemanticCycleRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SemanticCycleRequestFilterTest {
  @Test void productionComponentScanDiscoversTheGuard() {
    var packages=ReasonerServer.class.getAnnotation(org.springframework.context.annotation.ComponentScan.class).basePackages();
    var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(org.springframework.stereotype.Component.class));
    assertTrue(java.util.Arrays.stream(packages).flatMap(p->scanner.findCandidateComponents(p).stream())
        .anyMatch(bean->SemanticCycleRequestFilter.class.getName().equals(bean.getBeanClassName())));
  }
  @Test void cycleHeaderCannotSelectCurrentSemanticCapabilityAuthorityOrLegacyResolutionHandlers() throws Exception {
    for(var path:new String[]{ServicesAPI.REASONER.RESOLVE_CONCEPT,ServicesAPI.REASONER.RESOLVE_OBSERVABLE,
        ServicesAPI.REASONER.VALIDATE_DOCUMENT,ServicesAPI.REASONER.CONFIGURE_AUTHORITY,ServicesAPI.CAPABILITIES}) {
      var request=new MockHttpServletRequest("POST",path);request.addHeader(ServicesAPI.RESOLUTION_CYCLE_HEADER,"opaque cycle");
      var response=new MockHttpServletResponse();var calls=new AtomicInteger();
      new SemanticCycleRequestFilter().doFilter(request,response,(r,s)->calls.incrementAndGet());
      assertEquals(409,response.getStatus(),path);assertEquals(0,calls.get());
    }
  }
  @Test void protocolStillReachesItsAuthenticationChecksAndOrdinaryUnboundRequestsRemainUnchanged() throws Exception {
    for(var path:new String[]{ServicesAPI.REASONER.SEMANTIC_CYCLE_BEGIN,ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,
        ServicesAPI.REASONER.SEMANTIC_CYCLE_RELEASE,"/public/status"}) {
      var request=new MockHttpServletRequest("POST",path);request.addHeader(ServicesAPI.RESOLUTION_CYCLE_HEADER,"not authority");
      var calls=new AtomicInteger();new SemanticCycleRequestFilter().doFilter(request,new MockHttpServletResponse(),(r,s)->calls.incrementAndGet());
      assertEquals(1,calls.get(),path);
    }
    var request=new MockHttpServletRequest("POST",ServicesAPI.REASONER.RESOLVE_CONCEPT);
    var calls=new AtomicInteger();new SemanticCycleRequestFilter().doFilter(request,new MockHttpServletResponse(),(r,s)->calls.incrementAndGet());
    assertEquals(1,calls.get());
  }
}
