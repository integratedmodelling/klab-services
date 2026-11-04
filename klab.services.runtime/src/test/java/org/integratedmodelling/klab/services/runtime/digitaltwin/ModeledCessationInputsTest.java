package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.knowledge.*;import org.integratedmodelling.klab.api.geometry.Geometry;import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;import org.junit.jupiter.api.Test;
class ModeledCessationInputsTest {
 static ObservationImpl observation(){var c=new ConceptImpl();c.setUrn("test:Tree");var o=new ObservableImpl();o.setUrn("test:Tree");o.setSemantics(c);var r=new ObservationImpl();r.setId(42);r.setParentId(10);r.setGeometry(Geometry.create("1"));r.setResolvedCoverage(1);r.setObservable(o);return r;}
 @Test void presentationIsNotAuthorityButSemanticSupportAndPlacementAreExact(){
  var raw=observation();var reported=observation();raw.setName("Tree");reported.setName("a.42");raw.setContextualizationData(new ObservationImpl.ContextualizationDataImpl());assertTrue(ModeledCessations.samePayload(raw,reported));
  reported.setId(43);assertFalse(ModeledCessations.samePayload(raw,reported));reported.setId(42);
  reported.setParentId(11);assertFalse(ModeledCessations.samePayload(raw,reported));reported.setParentId(10);
  reported.setResolvedCoverage(.5);assertFalse(ModeledCessations.samePayload(raw,reported));reported.setResolvedCoverage(1);
  reported.setGeometry(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));assertFalse(ModeledCessations.samePayload(raw,reported));reported.setGeometry(Geometry.create("1"));
  ((ObservableImpl)reported.getObservable()).setUrn("test:Different");assertFalse(ModeledCessations.samePayload(raw,reported));((ObservableImpl)reported.getObservable()).setUrn("test:Tree");
  var agent=new ConceptImpl();agent.setUrn("test:Observer");((ObservableImpl)reported.getObservable()).setObserverSemantics(agent);assertFalse(ModeledCessations.samePayload(raw,reported));
 }
}
