package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;
class ConfigurationPerspectiveTest {
 @Test void actualContextObserverAndFocusDetermineCanonicalIdentity() {
  var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
  assertEquals(new ConfigurationPerspective("context",0,0),ConfigurationPerspective.capture(scope));
  var agent=mock(Observation.class);var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
  when(agent.getId()).thenReturn(17L);when(agent.getObservable()).thenReturn(observable);when(observable.is(SemanticType.AGENT)).thenReturn(true);
  when(scope.getObserverId()).thenReturn(17L);when(scope.getObserver()).thenReturn(agent);
  var focus=mock(Observation.class);when(focus.getId()).thenReturn(21L);when(scope.getContextObservation()).thenReturn(focus);
  assertEquals(new ConfigurationPerspective("context",17,21),ConfigurationPerspective.capture(scope));
  assertNotEquals(ConfigurationPerspective.capture(scope),new ConfigurationPerspective("other",17,21));
  assertNotEquals(ConfigurationPerspective.capture(scope),new ConfigurationPerspective("context",18,21));
  assertNotEquals(ConfigurationPerspective.capture(scope),new ConfigurationPerspective("context",17,22));
  when(focus.getId()).thenReturn(-2L);assertThrows(IllegalStateException.class,()->ConfigurationPerspective.capture(scope));
  when(focus.getId()).thenReturn(21L);when(observable.is(SemanticType.AGENT)).thenReturn(false);
  assertThrows(IllegalStateException.class,()->ConfigurationPerspective.capture(scope));
 }
}
