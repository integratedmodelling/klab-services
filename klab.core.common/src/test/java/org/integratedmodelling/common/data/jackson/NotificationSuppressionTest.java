package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.*;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.junit.jupiter.api.Test;

class NotificationSuppressionTest {
  @Test void defineAnnotationsReachObservationAndSurviveJsonWithUnnamedSelectors() throws Exception {
    var concept=new ConceptImpl(); concept.setUrn("test:Quality"); concept.setName("Quality"); concept.getType().add(SemanticType.QUALITY);
    var reasoner=mock(Reasoner.class); when(reasoner.resolveObservable(anyString())).thenReturn(ObservableImpl.promote(concept,null));
    var scope=mock(ContextScope.class); when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var define=new KimSymbolDefinitionImpl(); define.setDefineClass("observation"); define.setNamespace("test"); define.setName("quality"); define.setUrn("test.quality");
    define.setValue(Map.of("semantics","test:Quality"));
    var suppress=Annotation.of("suppress"); suppress.putUnnamed("warnings"); define.getAnnotations().add(suppress);
    var observation=new Observation.NaiveBuilder(define,scope).build();
    var mapper=JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsString(Observation.forTransport(observation)),Observation.class);
    assertTrue(NotificationSuppression.suppresses(transported.getAnnotations(),Notification.Level.Warning));
    assertEquals(Notification.Mode.Silent,Notification.warning("intentional",transported).getMode());
    assertEquals(Notification.Mode.Normal,Notification.error("invalid",transported).getMode());
    assertEquals(Notification.Mode.Normal,Notification.warning(new IllegalStateException("invalid"),transported).getMode());
  }
  @Test void bareSuppressionAndExplicitSelectorsNeverSilenceErrorsOrOtherLevels() {
    for(var annotation:List.of(Annotation.of("suppress"),Annotation.of("suppress","warnings",true),Annotation.of("suppress","value","warnings"))) {
      assertTrue(NotificationSuppression.suppresses(List.of(annotation),Notification.Level.Warning));
      for(var level:List.of(Notification.Level.Error,Notification.Level.SystemError,Notification.Level.Info,Notification.Level.Debug))
        assertFalse(NotificationSuppression.suppresses(List.of(annotation),level));
    }
    for(var annotation:List.of(Annotation.of("suppress","value","errors"),Annotation.of("suppress","warnings",false),Annotation.of("suppress","value","typo")))
      assertFalse(NotificationSuppression.suppresses(List.of(annotation),Notification.Level.Warning));
  }
  @Test void warningSuppressionStopsChannelDeliveryAndLeavesErrorDeliveryIntact() {
    var observation=new ObservationImpl(); observation.getAnnotations().add(Annotation.of("suppress"));
    var channel=new ChannelImpl((org.integratedmodelling.klab.api.identities.Identity)null) { public String getDispatchId() { return "test"; } };
    var received=new ArrayList<Message>();
    channel.onMessage((source,message)->received.add(message),Message.Queue.Warnings,Message.Queue.Errors);
    channel.warn("intentional",observation); assertTrue(received.isEmpty());
    channel.error("invalid",observation); assertEquals(1,received.size());
    assertEquals(Notification.Level.Error,received.getFirst().getPayload(Notification.class).getLevel());
    channel.warn(new IllegalStateException("invalid"),observation);
    assertEquals(2,received.size());
    assertEquals(Notification.Level.Error,received.getLast().getPayload(Notification.class).getLevel());
  }
}
