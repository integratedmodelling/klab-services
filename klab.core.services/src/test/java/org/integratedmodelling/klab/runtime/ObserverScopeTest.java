package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.scope.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;

class ObserverScopeTest {
  private ObservationImpl observer() {
    var concept = new ConceptImpl(); concept.setUrn("test:Agent"); concept.setName("Agent");
    concept.setType(EnumSet.of(SemanticType.AGENT));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn("test:Agent");
    var agent = new ObservationImpl(); agent.setObservable(observable); agent.setId(12);
    agent.setGeometry(Geometry.UNIVERSAL);
    agent.setPerceivedGeometry(Geometry.create("T0(1){tstart=0,tend=100,ttype=PHYSICAL}"));
    return agent;
  }

  @Test void perceivedAndOccupiedGeometrySurviveTransportSeparately() {
    var agent = observer();
    var portable = Observation.forTransport(agent);
    var restored = Utils.Json.parseObject(Utils.Json.asString(portable), Observation.class);
    assertTrue(restored.getGeometry().isUniversal());
    assertEquals(agent.getPerceivedGeometry().encode(), restored.geometry(Observation.GeometryRelationship.PERCEIVES).encode());
  }

  @Test void configurationAndDerivedClientScopesPreserveIndependentSelections() throws Exception {
    var user = mock(UserIdentity.class); when(user.getId()).thenReturn("alice"); when(user.getUsername()).thenReturn("alice");
    when(user.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
    var runtime = mock(RuntimeService.class); when(runtime.serviceId()).thenReturn("runtime");
    when(runtime.getUrl()).thenReturn(URI.create("http://localhost:8183").toURL());
    var parent = new ClientSessionScope(new ClientUserScope(user, null), "session", runtime).withId("session");
    var configuration = (ConfigurationImpl) org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration.builder()
        .id("session.twin").name("Twin").build();
    configuration.setObserver(observer());
    var root = new ClientContextScope(parent, runtime, configuration); root.setId("session.twin");
    var context = new ObservationImpl(); context.setId(23); context.setGeometry(Geometry.create("T0(1){tstart=10,tend=20,ttype=PHYSICAL}"));
    var focused = root.within(context);
    assertEquals(12L, focused.getConstraint(ResolutionConstraint.Type.Observer, Long.class));
    assertSame(root.getObserver(), focused.getObserver());
    assertSame(context, focused.withObserver(null).getContextObservation());
    assertNull(focused.withObserver(null).getConstraint(ResolutionConstraint.Type.Observer, Long.class));
    assertSame(root.getObserver(), focused.within(null).getObserver());
    assertSame(context.getGeometry(), ContextScope.getResolutionGeometry(focused));
    assertSame(root.getObserver().geometry(Observation.GeometryRelationship.PERCEIVES), ContextScope.getResolutionGeometry(focused.within(null)));
    assertTrue(ContextScope.getScopeId(focused).endsWith("#12"));
    var another = new ObservationImpl(); another.setId(24);
    var replaced = ((ClientContextScope) focused).withCurrentContext(another);
    assertEquals("session.twin.24#12", ContextScope.getScopeId(replaced));
  }

  @Test void missingPerceptionDoesNotFallBackToOccupation() {
    var scope = mock(ContextScope.class); var agent = observer(); agent.setPerceivedGeometry(null);
    when(scope.getObserver()).thenReturn(agent);
    assertNull(ContextScope.getResolutionGeometry(scope));
  }

  @Test void serverConnectionsDoNotMutateSharedObserverOrConfiguration() {
    var user = mock(UserIdentity.class); when(user.getId()).thenReturn("alice");
    var runtime = mock(RuntimeService.class);
    var session = new org.integratedmodelling.klab.services.scopes.ServiceSessionScope(
        new org.integratedmodelling.klab.services.scopes.ServiceUserScope(user, runtime));
    var config = org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration.builder()
        .id("session.twin").name("Twin").build();
    var root = new org.integratedmodelling.klab.services.scopes.ServiceContextScope(session, config, user);
    var selected = root.forObserverConnection(observer());
    assertNull(root.getObserver());
    assertNull(root.getConfiguration().getObserver());
    assertNotSame(root.getConfiguration(), selected.getConfiguration());
    assertEquals(12L, selected.getObserver().getId());
    assertEquals(12L, selected.getConfiguration().getObserver().getId());
  }
}
