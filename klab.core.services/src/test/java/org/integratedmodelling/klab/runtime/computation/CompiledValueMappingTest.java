package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassificationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimLookupTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimValueMappingValidator;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class CompiledValueMappingTest {

  @Test
  void annotationCodelistsCompileToExactIntegralConceptMappings() {
    var reasoner = mock(Reasoner.class);
    var scope = mock(ContextScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var pasture = mock(Concept.class);
    when(reasoner.resolveConcept("landcover:Pastures")).thenReturn(pasture);
    var codelist = new CodelistImpl();
    codelist.setUrn("landcover:LandCoverType");
    codelist.getEntries().add(
        new CodelistImpl.Entry(
            "corine", 231L, concept("landcover:Pastures"), "Pastures", true));

    var mapping = CompiledValueMapping.compile(codelist, "corine", scope);
    assertEquals(pasture, mapping.lookup(new Object[] {231}, scope));
    assertEquals(pasture, mapping.lookup(new Object[] {231.0}, scope));
    assertEquals(null, mapping.lookup(new Object[] {231.5}, scope));
    assertEquals(null, mapping.lookup(new Object[] {Double.NaN}, scope));
  }

  @Test
  void matchesTheStagingFixtureWithFirstMatchWinsAndCachesMisses() {
    var mapping = CompiledValueMapping.compile(recreationTable(), null, 32);

    assertEquals(1.0, mapping.lookup(new Object[] {0.1, 0.2}, null));
    assertEquals(1.0, mapping.lookup(new Object[] {0.1, 0.2}, null));
    assertEquals(9.0, mapping.lookup(new Object[] {0.8, 0.9}, null));
    assertTrue(Double.isNaN((Double) mapping.lookup(new Object[] {Double.NaN, 0.9}, null)));
    assertTrue(Double.isNaN((Double) mapping.lookup(new Object[] {Double.NaN, 0.9}, null)));

    var statistics = mapping.statistics();
    assertTrue(statistics.hits() >= 2);
    assertTrue(statistics.misses() >= 3);
  }

  @Test
  void computedResultsAreEvaluatedAfterTheMatchedRuleIsCached() {
    var table = new KimTableImpl();
    var computed = new KimClassifierImpl();
    computed.setExpressionMatch(ExpressionCode.of("self * 2", "groovy"));
    computed.setType(Artifact.Type.NUMBER);
    table.setRows(
        List.<KimClassifier[]>of(
            new KimClassifier[] {interval(null, null, true, true), computed}));
    var lookup = lookup(table, "value", "?");

    var mapping = CompiledValueMapping.compile(lookup, null, 4);
    assertEquals(6.0, ((Number) mapping.lookup(new Object[] {3.0}, null)).doubleValue());
    assertEquals(6.0, ((Number) mapping.lookup(new Object[] {3.0}, null)).doubleValue());
    assertEquals(1, mapping.statistics().hits());
  }

  @Test
  void cacheIsBoundedAndCanBeDisabled() {
    var lookup = recreationTable();
    var bounded = CompiledValueMapping.compile(lookup, null, 2);
    for (int i = 0; i < 20; i++) bounded.lookup(new Object[] {i / 20.0, 0.2}, null);
    assertTrue(bounded.statistics().size() <= 2);
    assertTrue(bounded.statistics().evictions() > 0);

    var disabled = CompiledValueMapping.compile(lookup, null, 0);
    disabled.lookup(new Object[] {0.1, 0.2}, null);
    disabled.lookup(new Object[] {0.1, 0.2}, null);
    assertEquals(new CompiledValueMapping.Statistics(0, 0, 0, 0), disabled.statistics());
  }

  @Test
  void twoWayTablesMatchTheirRowAndColumnClassifiers() {
    var table = new KimTableImpl();
    table.setTwoWay(true);
    table.setRows(
        List.of(
            new KimClassifier[] {number(1), number(2)},
            new KimClassifier[] {number(3), number(4)}));
    table.setRowClassifiers(List.of(interval(null, 0.5, true, false), interval(0.5, null, true, true)));
    table.setColumnClassifiers(List.of(text("a"), text("b")));
    var lookup = new KimLookupTableImpl();
    lookup.setTwoWay(true);
    lookup.setTable(table);
    lookup.setRowClassifiers(table.getRowClassifiers());
    lookup.setColumnClassifiers(table.getColumnClassifiers());
    lookup.setArguments(
        new ArrayList<>(
            List.of(argument("score", KimLookupTable.Argument.Dimension.ROW), argument("kind", KimLookupTable.Argument.Dimension.COLUMN))));
    KimValueMappingValidator.validateAndNormalize(lookup, false);
    var mapping = CompiledValueMapping.compile(lookup, null, 8);
    assertEquals(2.0, mapping.lookup(new Object[] {0.25, "b"}, null));
    assertEquals(3.0, mapping.lookup(new Object[] {0.75, "a"}, null));
  }

  @Test
  void classificationsResolveConceptResultsOnceAndPreserveRuleOrder() {
    var reasoner = mock(Reasoner.class);
    var scope = mock(ContextScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var lowSyntax = concept("test:Low");
    var fallbackSyntax = concept("test:Fallback");
    var low = mock(Concept.class);
    var fallback = mock(Concept.class);
    when(reasoner.resolveConcept("test:Low")).thenReturn(low);
    when(reasoner.resolveConcept("test:Fallback")).thenReturn(fallback);
    var any = new KimClassifierImpl();
    any.setCatchAll(true);
    any.setType(Artifact.Type.VALUE);
    var classification = new KimClassificationImpl();
    classification.setClassifiers(
        new ArrayList<>(
            List.of(
                new PairImpl<>(lowSyntax, interval(null, 0.5, true, true)),
                new PairImpl<>(fallbackSyntax, any))));

    var mapping = CompiledValueMapping.compile(classification, scope, 8);
    assertEquals(low, mapping.lookup(new Object[] {0.25}, scope));
    assertEquals(fallback, mapping.lookup(new Object[] {0.75}, scope));
    assertEquals(low, mapping.lookup(new Object[] {0.25}, scope));
    assertTrue(mapping.statistics().hits() > 0);
  }

  @Test
  void scalarBuilderRunsTheCompiledMappingInOneScannerLoop() {
    var target = numericObservation();
    var remoteness = numericObservation();
    var potential = numericObservation();
    var builder =
        ScalarComputationGroovy.builder(
            target,
            mock(ContextScope.class),
            mock(Actuator.class),
            Map.of("remoteness", remoteness, "recreation_potential", potential));
    assertTrue(
        builder.add(
            new ServiceCallImpl(
                RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
                "lookupTable",
                recreationTable())));
    var computation = builder.build();
    assertNotNull(computation, target.getNotifications().toString());

    var output = new ArrayDoubleScanner(0, 0, 0);
    assertTrue(
        computation.execute(
            Map.of(
                "self",
                output,
                "remoteness",
                new ArrayDoubleScanner(0.1, 0.3, 0.8),
                "recreation_potential",
                new ArrayDoubleScanner(0.2, 0.6, 0.9)),
            null,
            null));
    assertEquals(List.of(1.0, 5.0, 9.0), output.written);
  }

  @Test
  void consecutiveScalarStepsShareSelfInOneScannerLoop() {
    var table = new KimTableImpl();
    table.setRows(
        List.of(
            new KimClassifier[] {interval(null, 0.5, true, true), number(1)},
            new KimClassifier[] {interval(0.5, null, false, true), number(2)}));
    var target = numericObservation();
    var builder =
        ScalarComputationGroovy.builder(
            target, mock(ContextScope.class), mock(Actuator.class), Map.of());
    assertTrue(
        builder.add(
            new ServiceCallImpl(
                RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),
                "value",
                0.75)));
    assertTrue(
        builder.add(
            new ServiceCallImpl(
                RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
                "lookupTable",
                lookup(table, "self", "?"))));
    var computation = builder.build();
    assertNotNull(computation, target.getNotifications().toString());

    var output = new ArrayDoubleScanner(0, 0, 0);
    assertTrue(computation.execute(Map.of("self", output), null, null));
    assertEquals(List.of(2.0, 2.0, 2.0), output.written);
  }

  @Test
  void classificationWritesConceptsThroughAKeyScanner() {
    var reasoner = mock(Reasoner.class);
    var scope = mock(ContextScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var low = mock(Concept.class);
    var high = mock(Concept.class);
    when(reasoner.resolveConcept("test:Low")).thenReturn(low);
    when(reasoner.resolveConcept("test:High")).thenReturn(high);
    var classification = new KimClassificationImpl();
    classification.setClassifiers(
        new ArrayList<>(
            List.of(
                new PairImpl<>(concept("test:Low"), interval(null, 0.5, true, true)),
                new PairImpl<>(concept("test:High"), interval(0.5, null, false, true)))));
    var target = observation(Storage.Type.KEYED);
    var builder =
        ScalarComputationGroovy.builder(target, scope, mock(Actuator.class), Map.of());
    assertTrue(
        builder.add(
            new ServiceCallImpl(
                RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),
                "value",
                0.75)));
    assertTrue(
        builder.add(
            new ServiceCallImpl(
                RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
                "classification",
                classification)));
    var computation = builder.build();
    assertNotNull(computation, target.getNotifications().toString());

    var output = new ArrayKeyScanner(null, null, null);
    assertTrue(computation.execute(Map.of("self", output), null, scope));
    assertEquals(List.of(high, high, high), output.written);
  }

  private static Observation numericObservation() {
    return observation(Storage.Type.DOUBLE);
  }

  private static Observation observation(Storage.Type type) {
    var observation = new ObservationImpl();
    var contextualization = new ObservationImpl.ContextualizationDataImpl();
    contextualization.setNativeShardingStrategy(Data.ShardingStrategy.trivial(type));
    observation.setContextualizationData(contextualization);
    return observation;
  }

  private static KimLookupTable recreationTable() {
    var table = new KimTableImpl();
    var rows = new ArrayList<KimClassifier[]>();
    for (int potential = 0; potential < 3; potential++) {
      for (int remoteness = 0; remoteness < 3; remoteness++) {
        rows.add(
            new KimClassifier[] {
              remoteness == 0
                  ? interval(null, 0.25, true, true)
                  : remoteness == 1
                      ? interval(0.25, 0.5, true, true)
                      : interval(0.5, null, false, true),
              potential == 0
                  ? interval(null, 0.5, true, true)
                  : potential == 1
                      ? interval(0.5, 0.75, true, true)
                      : interval(0.75, null, false, true),
              number(potential * 3 + remoteness + 1),
              text("description")
            });
      }
    }
    table.setRows(rows);
    return lookup(table, "remoteness", "recreation_potential", "?", "*");
  }

  private static KimLookupTable lookup(KimTableImpl table, String... ids) {
    var lookup = new KimLookupTableImpl();
    lookup.setTable(table);
    var arguments = new ArrayList<KimLookupTable.Argument>();
    for (var id : ids) arguments.add(argument(id, null));
    lookup.setArguments(arguments);
    KimValueMappingValidator.validateAndNormalize(lookup, false);
    return lookup;
  }

  private static KimLookupTable.Argument argument(
      String id, KimLookupTable.Argument.Dimension dimension) {
    var argument = new KimLookupTable.Argument();
    argument.id = id;
    argument.dimension = dimension;
    return argument;
  }

  private static KimClassifier interval(
      Double lower, Double upper, boolean lowerInclusive, boolean upperInclusive) {
    var classifier = new KimClassifierImpl();
    classifier.setIntervalMatch(
        new NumericRangeImpl(lower, upper, !lowerInclusive, !upperInclusive));
    classifier.setType(Artifact.Type.NUMBER);
    return classifier;
  }

  private static KimClassifier number(double value) {
    var classifier = new KimClassifierImpl();
    classifier.setNumberMatch(value);
    classifier.setType(Artifact.Type.NUMBER);
    return classifier;
  }

  private static KimClassifier text(String value) {
    var classifier = new KimClassifierImpl();
    classifier.setStringMatch(value);
    classifier.setType(Artifact.Type.TEXT);
    return classifier;
  }

  private static KimConceptImpl concept(String urn) {
    var concept = new KimConceptImpl();
    concept.setUrn(urn);
    concept.setName(urn.substring(urn.indexOf(':') + 1));
    return concept;
  }

  private static final class ArrayDoubleScanner implements Storage.DoubleScanner {
    private final double[] values;
    private final List<Double> written = new ArrayList<>();
    private int index;

    private ArrayDoubleScanner(double... values) {
      this.values = values;
    }

    @Override
    public double get() {
      return values[index++];
    }

    @Override
    public double peek() {
      return values[index];
    }

    @Override
    public void add(double value) {
      written.add(value);
      index++;
    }

    @Override
    public boolean hasNext() {
      return index < values.length;
    }

    @Override
    public long nextLong() {
      return index++;
    }

    @Override
    public Storage.Shard shard() {
      return null;
    }

    @Override
    public long position() { return index; }

    @Override
    public long size() {
      return values.length;
    }
  }

  private static final class ArrayKeyScanner implements Storage.KeyScanner<Concept> {
    private final Concept[] values;
    private final List<Concept> written = new ArrayList<>();
    private int index;

    private ArrayKeyScanner(Concept... values) {
      this.values = values;
    }

    @Override
    public org.integratedmodelling.klab.api.data.mediation.classification.DataKey key() {
      return null;
    }

    @Override
    public Concept get() {
      return values[index++];
    }

    @Override
    public Concept peek() {
      return values[index];
    }

    @Override
    public void add(Concept value) {
      written.add(value);
      index++;
    }

    @Override
    public boolean hasNext() {
      return index < values.length;
    }

    @Override
    public long nextLong() {
      return index++;
    }

    @Override
    public Storage.Shard shard() {
      return null;
    }

    @Override
    public long position() { return index; }

    @Override
    public long size() {
      return values.length;
    }
  }
}
