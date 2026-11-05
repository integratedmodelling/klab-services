package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.ContextualizationScopeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InstantiationPolicyCompilationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  @Test void selectedModelPolicyIsFrozenPerComputationAndRetainedThroughInterfaceTransport() throws Exception {
    var f=new CollectiveReductionPlanningTest();
    when(f.model.getAnnotations()).thenReturn(List.of(Annotation.of("resolution","value","none")));
    var flow=new DataflowCompiler(f.request,f.compiler.resolve(f.request,f.scope),f.scope).compile();
    var producer=flow.getComputation().getFirst().getChildren().getFirst();
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var restored=mapper.readValue(mapper.writerFor(Actuator.class).writeValueAsString(producer),Actuator.class);
    var encoded=restored.getData().get(InstantiationResolution.DATA_PREFIX+0);
    assertNotNull(encoded);
    var policy=org.integratedmodelling.common.utils.Utils.Json.parseObject(encoded.toString(),InstantiationResolution.Declaration.class);
    assertEquals(InstantiationResolution.Mode.NONE,policy.mode()); assertEquals(f.model.getUrn(),policy.model());
    var outputs=new ContextualizationScopeImpl(f.discovery,null);
    outputs.getOutcomes().add(f.request);
    outputs.bindResolution(0,0,policy);
    assertEquals(policy,outputs.getInstantiationResolution(f.request).declaration());
    var support=org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.create(
        org.integratedmodelling.klab.api.geometry.Geometry.create(outputs.getInstantiationResolution(f.request).support()));
    assertTrue(org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(support,f.discovery.getGeometry()).isComplete());
  }
}
