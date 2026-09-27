package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassificationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimLookupTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimValueMappingValidator;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.junit.jupiter.api.Test;

/** Transport contract based on staging.vxii.test.lut.basic.RECREATION_OPPORTUNITY_TABLE. */
class LookupTableTransportTest {

  @Test
  void conceptCodelistSurvivesServiceCallAndDataflowTransport() throws Exception {
    var corine = new KimConceptImpl();
    corine.setName("Pastures");
    corine.setUrn("landcover:Pastures");
    var other = new KimConceptImpl();
    other.setName("OtherPastures");
    other.setUrn("landcover:OtherPastures");
    var codelist = new CodelistImpl();
    codelist.setUrn("landcover:LandCoverType");
    codelist.setRootConceptId("landcover:LandCoverType");
    codelist.setServiceId("resources-one");
    codelist.getEntries().add(new CodelistImpl.Entry("corine", 231L, corine, "Pastures", true));
    codelist.getEntries().add(new CodelistImpl.Entry("alternate", 7L, other, "Other", true));

    ServiceCall call =
        new ServiceCallImpl(
            RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
            "accordingTo", "corine", "codelist", codelist);
    var actuator = new ActuatorImpl();
    actuator.setName("land-cover");
    actuator.getComputation().add(call);
    var dataflow = new DataflowImpl();
    dataflow.getComputation().add(actuator);

    var mapper = JacksonConfiguration.newObjectMapper();
    var copy = mapper.readValue(mapper.writeValueAsString(dataflow), Dataflow.class);
    var transported =
        (Codelist)
            copy.getComputation()
                .getFirst()
                .getComputation()
                .getFirst()
                .getParameters()
                .get("codelist");
    assertEquals(List.of("corine", "alternate"), transported.getAuthorityIds().stream().toList());
    assertEquals("landcover:Pastures", ((org.integratedmodelling.klab.api.lang.kim.KimConcept)
        transported.value("corine", 231.0)).getUrn());
    assertEquals("Pastures", transported.getDescription("corine", 231));
    assertEquals("resources-one", transported.getServiceId());
  }

  @Test
  void classificationAndAccordingToSurviveJsonTransport() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var concept = new KimConceptImpl();
    concept.setName("LowRecreationOpportunity");
    concept.setUrn("test:LowRecreationOpportunity");
    var classification = new KimClassificationImpl();
    classification.setClassifiers(
        new ArrayList<>(List.of(new PairImpl<>(concept, interval(null, 0.25, true, true)))));

    var json = mapper.writerFor(KimClassification.class).writeValueAsString(classification);
    var copy = mapper.readValue(json, KimClassification.class);
    assertEquals("test:LowRecreationOpportunity", copy.getClassifiers().getFirst().getFirst().getUrn());
    assertEquals(
        0.25, copy.getClassifiers().getFirst().getSecond().getIntervalMatch().getUpperBound());

    var contextualizable = new ContextualizableImpl();
    contextualizable.setAccordingTo("corine");
    var transported =
        mapper.readValue(mapper.writeValueAsString(contextualizable), Contextualizable.class);
    assertEquals("corine", transported.getAccordingTo());
  }

  @Test
  void recreationOpportunityTableSurvivesEveryJsonEnvelope() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var original = recreationOpportunityTable();

    var tableJson = mapper.writerFor(KimLookupTable.class).writeValueAsString(original);
    assertLookup(mapper.readValue(tableJson, KimLookupTable.class));

    var contextualizable = new ContextualizableImpl();
    contextualizable.setLookupTable(original);
    var contextualizableCopy =
        mapper.readValue(mapper.writeValueAsString(contextualizable), Contextualizable.class);
    assertLookup(contextualizableCopy.getLookupTable());

    ServiceCall call =
        new ServiceCallImpl(
            RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
            "lookupTable",
            original);
    var callCopy = mapper.readValue(mapper.writeValueAsString(call), ServiceCall.class);
    assertLookup((KimLookupTable) callCopy.getParameters().get("lookupTable"));

    var actuator = new ActuatorImpl();
    actuator.setName("recreation-opportunity");
    actuator.setType(Artifact.Type.NUMBER);
    actuator.getComputation().add(call);
    var dataflow = new DataflowImpl();
    dataflow.setName("lookup-transport");
    dataflow.getComputation().add(actuator);
    var dataflowCopy =
        mapper.readValue(mapper.writeValueAsString(dataflow), Dataflow.class);
    var transportedCall = dataflowCopy.getComputation().getFirst().getComputation().getFirst();
    assertLookup((KimLookupTable) transportedCall.getParameters().get("lookupTable"));
  }

  private static void assertLookup(KimLookupTable table) {
    assertEquals(2, table.getLookupColumnIndex());
    assertEquals(Artifact.Type.NUMBER, table.getLookupType());
    assertEquals(
        List.of("remoteness", "recreation_potential", "score", "description"),
        table.getTable().getHeaders());
    assertEquals(9, table.getTable().getRowCount());
    assertEquals(4, table.getTable().getColumnCount());
    assertEquals(1.0, table.getTable().row(0)[2].getNumberMatch());
    assertEquals("low provision, easily accessible", table.getTable().row(0)[3].getStringMatch());
    assertEquals("high provision, not easily accessible", table.getTable().row(8)[3].getStringMatch());
    assertTrue(table.getTable().row(0)[0].getIntervalMatch().isLeftInfinite());
    assertEquals(0.25, table.getTable().row(0)[0].getIntervalMatch().getUpperBound());
    assertEquals(0.5, table.getTable().row(8)[0].getIntervalMatch().getLowerBound());
    assertTrue(table.getTable().row(8)[0].getIntervalMatch().isRightInfinite());
  }

  private static KimLookupTable recreationOpportunityTable() {
    var table = new KimTableImpl();
    table.setHeaders(
        List.of("remoteness", "recreation_potential", "score", "description"));
    var rows = new ArrayList<KimClassifier[]>();
    String[] descriptions = {
      "low provision, easily accessible",
      "low provision, accessible",
      "low provision, not easily accessible",
      "medium provision, easily accessible",
      "medium provision, accessible",
      "medium provision, not easily accessible",
      "high provision, easily accessible",
      "high provision, accessible",
      "high provision, not easily accessible"
    };
    for (int potential = 0; potential < 3; potential++) {
      for (int remoteness = 0; remoteness < 3; remoteness++) {
        int index = potential * 3 + remoteness;
        rows.add(
            new KimClassifier[] {
              remoteness == 0
                  ? interval(null, 0.25, true, true)
                  : remoteness == 1
                      ? interval(0.25, 0.5, true, false)
                      : interval(0.5, null, false, true),
              potential == 0
                  ? interval(null, 0.5, true, false)
                  : potential == 1
                      ? interval(0.5, 0.75, true, false)
                      : interval(0.75, null, false, true),
              number(index + 1),
              text(descriptions[index])
            });
      }
    }
    table.setRows(rows);

    var lookup = new KimLookupTableImpl();
    lookup.setTable(table);
    lookup.setArguments(
        new ArrayList<>(
            List.of(argument("remoteness"), argument("recreation_potential"), argument("?"), argument("*"))));
    KimValueMappingValidator.validateAndNormalize(lookup, false);
    return lookup;
  }

  private static KimLookupTable.Argument argument(String id) {
    var ret = new KimLookupTable.Argument();
    ret.id = id;
    return ret;
  }

  private static KimClassifier interval(
      Double lower, Double upper, boolean lowerInclusive, boolean upperInclusive) {
    var ret = new KimClassifierImpl();
    ret.setIntervalMatch(
        new NumericRangeImpl(lower, upper, !lowerInclusive, !upperInclusive));
    ret.setType(Artifact.Type.NUMBER);
    return ret;
  }

  private static KimClassifier number(double value) {
    var ret = new KimClassifierImpl();
    ret.setNumberMatch(value);
    ret.setType(Artifact.Type.NUMBER);
    return ret;
  }

  private static KimClassifier text(String value) {
    var ret = new KimClassifierImpl();
    ret.setStringMatch(value);
    ret.setType(Artifact.Type.TEXT);
    return ret;
  }
}
