package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;
class ConfigurationObservableTest {
 @Test void portableOwnerWithoutActivityUsesItsAcceptedCanonicalObservable() {
  var owner=DefaultConfigurationBackendTest.concept("test:Pair",SemanticType.CONFIGURATION);owner.setDescriptionType(null);
  var canonical=ObservableImpl.promote(owner,null);canonical.setDescriptionType(Contextualization.DETECTION);
  var reasoner=mock(Reasoner.class);when(reasoner.resolveObservable(owner.getUrn())).thenReturn(canonical);
  assertSame(canonical,DefaultConfigurationBackend.configurationObservable(owner,reasoner));assertNull(owner.getDescriptionType());
 }
 @Test void missingDifferentAndActivitylessCanonicalTargetsFailClosed() {
  var owner=DefaultConfigurationBackendTest.concept("test:Pair",SemanticType.CONFIGURATION);
  var reasoner=mock(Reasoner.class);
  assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.configurationObservable(owner,reasoner));
  var canonical=ObservableImpl.promote(owner,null);canonical.setDescriptionType(null);when(reasoner.resolveObservable(owner.getUrn())).thenReturn(canonical);
  assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.configurationObservable(owner,reasoner));
  canonical.setDescriptionType(Contextualization.DETECTION);canonical.setUrn("test:Other");
  assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.configurationObservable(owner,reasoner));
 }
}
