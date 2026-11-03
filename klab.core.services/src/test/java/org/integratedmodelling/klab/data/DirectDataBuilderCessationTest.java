package org.integratedmodelling.klab.data;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.data.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;

class DirectDataBuilderCessationTest {
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static ObservationImpl subject(long id) {var c=new ConceptImpl();c.setUrn("test:Tree");c.setType(EnumSet.of(SemanticType.SUBJECT));var o=new ObservationImpl();o.setId(id);o.setUrn("a."+id);o.setObservable(ObservableImpl.promote(c,null));o.setGeometry(Geometry.UNIVERSAL);return o;}
 @Test void reportsExactIntentWithoutMutatingTheGraphOrGrantingAuthority() {
  var scope=mock(ContextScope.class);var target=subject(41);var builder=new DirectDataBuilder("effect",null,subject(30),scope,null);
  assertSame(builder,builder.cease(target));builder.cease(target);assertEquals(1,builder.getCessations().size());assertSame(target,builder.getCessations().getFirst());
  assertThrows(UnsupportedOperationException.class,()->builder.getCessations().clear());assertTrue(builder.getObjects().isEmpty());verifyNoInteractions(scope);
 }
 @Test void childBuilderReportsIntoTheSameInvocationBatch() {
  var scope=mock(ContextScope.class);var child=subject(42);var creation=mock(Observation.Builder.class,RETURNS_SELF);
  when(scope.observation(child.getObservable())).thenReturn(creation);when(creation.register()).thenReturn(child);
  var builder=new DirectDataBuilder("effect",null,subject(30),scope,null);var target=subject(41);
  builder.object("child",child.getObservable(),child.getGeometry(),null).cease(target);
  assertSame(target,builder.getCessations().getFirst());assertEquals(1,builder.getObjects().size());
 }
 @Test void nonDurableCollectiveConfigurationAndDependentTargetsAreNotCessationOutputs() {
  var builder=new DirectDataBuilder("effect",null,subject(30),mock(ContextScope.class),null);
  assertThrows(IllegalArgumentException.class,()->builder.cease(null));assertThrows(IllegalArgumentException.class,()->builder.cease(subject(-7)));
  var target=subject(41);((ConceptImpl)target.getObservable().getSemantics()).setCollective(true);assertThrows(IllegalArgumentException.class,()->builder.cease(target));
  ((ConceptImpl)target.getObservable().getSemantics()).setCollective(false);((ConceptImpl)target.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.CONFIGURATION));assertThrows(IllegalArgumentException.class,()->builder.cease(target));
  ((ConceptImpl)target.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.QUALITY));assertThrows(IllegalArgumentException.class,()->builder.cease(target));assertTrue(builder.getCessations().isEmpty());
 }
 @Test void serializedMetadataCannotBecomeCessationAuthorityAndRemoteOperationFailsExplicitly() throws Exception {
  var target=subject(41);var source=new SerializingDataBuilder("effect",target.getObservable(),null,target.getGeometry(),null);
  source.metadata("im:cessations","[41]");assertThrows(UnsupportedOperationException.class,()->source.cease(target));
  var original=((BaseDataImpl)source.build()).asInstance();var wire=org.integratedmodelling.klab.common.data.Instance.fromByteBuffer(original.toByteBuffer());
  var data=BaseDataImpl.create(wire,List.of());var wrapping=new WrappingDataBuilder(data,target,null,null,"test",mock(ContextScope.class));
  assertEquals("[41]",data.metadata().get("im:cessations"));assertTrue(wrapping.getCessations().isEmpty());assertThrows(UnsupportedOperationException.class,()->wrapping.cease(target));
 }
 @Test void childErrorIsVisibleToTheWholeInvocationBeforeCessationTransfer() {
  var scope=mock(ContextScope.class);var child=subject(42);var creation=mock(Observation.Builder.class,RETURNS_SELF);when(scope.observation(child.getObservable())).thenReturn(creation);when(creation.register()).thenReturn(child);
  var root=new DirectDataBuilder("effect",null,subject(30),scope,null);
  root.object("child",child.getObservable(),child.getGeometry(),null).cease(subject(41)).notification(org.integratedmodelling.klab.api.services.runtime.Notification.error("child failed"));
  assertTrue(org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(root.getNotifications()));
 }
}
