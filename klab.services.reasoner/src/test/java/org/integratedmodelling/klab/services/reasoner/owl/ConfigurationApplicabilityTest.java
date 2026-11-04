package org.integratedmodelling.klab.services.reasoner.owl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;
class ConfigurationApplicabilityTest {
 @Test void canonicalConfigurationDomainRetainsInheritedAndBooleanBounds()throws Exception {
  try(var f=new ClassifierNativeAdmissionTest.Fixture()) {
   f.declare("Network",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE);
   f.declare("NarrowNetwork",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE);
   f.sub("NarrowNetwork","Network");
   f.owl.setApplicableObservables(f.concept("Network"),List.of(f.concept("Region"),f.concept("Other")),f.ontology);
   f.owl.setApplicableObservables(f.concept("NarrowNetwork"),List.of(f.concept("Region")),f.ontology);f.ready();
   assertTrue(f.reasoner.consequenceApplicable(f.concept("Network"),f.concept("Other")));
   assertTrue(f.reasoner.consequenceApplicable(f.concept("NarrowNetwork"),f.concept("Region")));
   assertFalse(f.reasoner.consequenceApplicable(f.concept("NarrowNetwork"),f.concept("Other")));
   assertFalse(f.reasoner.consequenceApplicable(f.concept("NarrowNetwork"),null));
  }
 }
 @Test void sourceCanonicalLookupRejectsUnknownAndNonconfigurationNamesWithoutResourcesCallback()throws Exception {
  try(var f=new ClassifierNativeAdmissionTest.Fixture()) {
   f.declare("Network",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE);f.ready();clearInvocations(f.resources);
   var transported=new ConceptImpl();transported.setUrn(f.concept("Network").getUrn());transported.setType(EnumSet.of(SemanticType.SUBJECT));
   assertTrue(f.reasoner.consequenceApplicable(transported,null));
   var missing=new ConceptImpl();missing.setUrn("audit:Missing");missing.setType(EnumSet.of(SemanticType.CONFIGURATION));
   assertThrows(IllegalStateException.class,()->f.reasoner.consequenceApplicable(missing,null));
   assertThrows(IllegalStateException.class,()->f.reasoner.consequenceApplicable(transported,missing));
   assertThrows(IllegalArgumentException.class,()->f.reasoner.consequenceApplicable(f.concept("Region"),null));
   verifyNoInteractions(f.resources);
  }
 }
}
