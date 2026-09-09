package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.junit.jupiter.api.*;

class PredicateTransformationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  static ObservableImpl observable(String urn, SemanticType type, Contextualization activity) {
    var c = new ConceptImpl(); c.setUrn(urn); c.setName(urn); c.setReferenceName(urn);
    c.setType(EnumSet.of(type));
    var o = new ObservableImpl(); o.setSemantics(c); o.setUrn(urn); o.setName(urn);
    o.setDescriptionType(activity); return o;
  }

  static ObservationImpl observation(Observable o, long id, Geometry geometry) {
    var ret = new ObservationImpl(); ret.setObservable(o); ret.setId(id); ret.setGeometry(geometry);
    return ret;
  }

  @Test void predicateSplitSelectsAndCompilesThroughTransport() throws Exception {
    exercise(true, true);
  }
  @Test void baseCoverageCannotReplaceMissingTransformer() throws Exception {
    exercise(false, true);
  }
  @Test void missingContextRejectsDependentStrategy() throws Exception {
    exercise(true, false);
  }

  @Test void missingOrAmbiguousTransformerPortsAreRejected() throws Exception {
    exercise(true, true, 0, false);
    exercise(true, true, 2, false);
  }
  @Test void reversedMergeInputsAreRejectedDuringLowering() throws Exception {
    exercise(true, true, 1, true);
  }

  private void exercise(boolean transformerAvailable, boolean hasContext) throws Exception {
    exercise(transformerAvailable, hasContext, 1, false);
  }

  @Test void unobservedBaseRecursivelyUsesItsDirectModel() throws Exception {
    exercise(true, true, 1, false, false);
  }

  private void exercise(boolean transformerAvailable, boolean hasContext, int ports, boolean reversed) throws Exception {
    exercise(transformerAvailable, hasContext, ports, reversed, true);
  }

  private void exercise(boolean transformerAvailable, boolean hasContext, int ports, boolean reversed,
      boolean baseExists) throws Exception {
    var geometry = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    var requested = observable("data:Normalized geography:Elevation", SemanticType.QUALITY, Contextualization.MEASURE);
    var raw = observable("geography:Elevation", SemanticType.QUALITY, Contextualization.MEASURE);
    var predicate = observable("data:Normalized", SemanticType.PREDICATE, Contextualization.VOID).asConcept();
    var transformer = observable("data:Normalized of geography:Elevation", SemanticType.PREDICATE, Contextualization.TRANSFORMATION);
    var subject = observable("earth:Region", SemanticType.SUBJECT, Contextualization.ACKNOWLEDGEMENT);
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getId()).thenReturn("transform-test");
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    if (hasContext) when(scope.getContextObservation()).thenReturn(observation(subject, 1, geometry));
    when(reasoner.directTraits(requested)).thenReturn(List.of(predicate));
    when(reasoner.directInherent(transformer)).thenReturn(raw.asConcept());
    when(reasoner.buildObservable(any(), eq(scope))).thenAnswer(inv -> {
      ObservableBuildStrategy b = inv.getArgument(0);
      return b.getBaseObservable() != null ? raw : transformer;
    });
    var strategies = new ObservationReasoner(reasoner, ignored -> { throw new AssertionError(); });
    var source = ObservationPipelineTest.document().getStatements().stream()
        .filter(s -> s.getUrn().equals("quality.split.predicate")).findFirst().orElseThrow();
    if (reversed) {
      var merge = (org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.GraphMergeImpl)
          source.getPlan().getSteps().getLast();
      var left = merge.getLeft(); merge.setLeft(merge.getRight()); merge.setRight(left);
    }
    strategies.registerStrategy(source);
    strategies.registerStrategy(ObservationPipelineTest.document().getStatements().stream()
        .filter(s -> s.getUrn().equals("dependent.direct")).findFirst().orElseThrow());
    strategies.initializeStrategies();
    var request = observation(requested, -1, geometry);
    var matches = strategies.computeMatchingStrategies(request, scope, true);
    if (!hasContext) { assertTrue(matches.isEmpty()); return; }
    if (reversed) { assertEquals(1, matches.size()); assertEquals(0, matches.getFirst().getRank()); return; }
    assertEquals(2, matches.size());
    assertEquals(0, matches.getFirst().getRank());
    assertEquals(1, matches.getLast().getRank());
    var mapper = JacksonConfiguration.newObjectMapper();
    var compiled = mapper.readValue(mapper.writerFor(ObservationStrategy.class)
        .writeValueAsString(matches.getLast()), ObservationStrategy.class);
    assertEquals(2, compiled.getOperations().size());
    assertEquals("rawquality", compiled.getOperations().getLast().getTransformationTarget());
    assertEquals(transformer.getUrn(), compiled.getOperations().getLast().getObservable().getUrn());
    when(reasoner.computeObservationStrategies(any(), eq(scope))).thenAnswer(inv -> {
      Observation incoming = inv.getArgument(0);
      return incoming.getObservable().getUrn().equals(requested.getUrn())
          ? List.of(matches.getFirst(), compiled) : strategies.computeMatchingStrategies(incoming, scope, true);
    });
    when(scope.observation(any(Observable.class))).thenAnswer(inv -> {
      Observable target = inv.getArgument(0);
      return new Observation.NaiveBuilder(target, scope) {
        @Override public Observation register() { return observation(target, -42, geometry); }
      };
    });
    var root = ResolutionGraph.create(scope);
    var input = mock(ServiceInfo.Argument.class);
    when(input.getName()).thenReturn("input"); when(input.getTags()).thenReturn(Set.of(ServiceInfo.Tag.INPUT));
    var prototype = mock(ServiceInfo.class); when(prototype.listInputs()).thenReturn(Collections.nCopies(ports, input));
    var key = ResolverService.class.getDeclaredField("RESOLUTION_GRAPH_KEY"); key.setAccessible(true);
    when(scope.getData()).thenReturn(Parameters.create((String) key.get(null), root));
    var runtime = mock(RuntimeService.class); when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.getServiceInfo("test.normalize", scope)).thenReturn(prototype);
    var requirements = new ResourceSet(); requirements.setEmpty(false);
    var service = new ResourceSet.Resource(); service.setResourceUrn("test.normalize");
    service.setKnowledgeClass(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION);
    requirements.getResults().add(service);
    when(runtime.resolveContextualizables(any(), eq(scope))).thenReturn(requirements);
    var model = mock(Model.class); when(model.getCoverage()).thenReturn(geometry);
    when(model.getDependencies()).thenReturn(List.of()); when(model.getAnnotations()).thenReturn(List.of());
    var computation = new ContextualizableImpl(); computation.setServiceCall(new ServiceCallImpl("test.normalize"));
    when(model.getComputation()).thenReturn(List.of(computation));
    var rawModel = mock(Model.class); when(rawModel.getCoverage()).thenReturn(geometry);
    when(rawModel.getDependencies()).thenReturn(List.of()); when(rawModel.getAnnotations()).thenReturn(List.of());
    var rawComputation = new ContextualizableImpl(); rawComputation.setServiceCall(new ServiceCallImpl("test.elevation"));
    when(rawModel.getComputation()).thenReturn(List.of(rawComputation));
    var compiler = new ResolutionCompiler(mock(ResolverService.class)) {
      @Override public List<Model> queryModels(Observable o, Concept c, ContextScope s, Scale scale) {
        if (o.getUrn().equals(requested.getUrn())) return List.of();
        if (o.getUrn().equals(raw.getUrn())) return List.of(rawModel);
        assertEquals(transformer.getUrn(), o.getUrn());
        return transformerAvailable ? List.of(model) : List.of();
      }
      @Override QueryMatch query(Observable o, Scale scale, ContextScope s) {
        if (baseExists && o.getUrn().equals(raw.getUrn())) {
          var existing = observation(raw, 42, geometry);
          return new QueryMatch(existing, existing, scale, scale, Coverage.create(scale, 1));
        }
        return new QueryMatch(null, null, scale, null, Coverage.create(scale, 0));
      }
    };
    var graph = compiler.resolve(request, scope);
    assertEquals(!transformerAvailable || ports != 1, graph.isEmpty());
    if (!transformerAvailable || ports != 1) return;
    var dataflow = new DataflowCompiler(request, graph, scope).compile();
    var actuator = dataflow.getComputation().getFirst();
    assertEquals("rawquality", actuator.getChildren().getFirst().getName());
    if (!baseExists) assertEquals(1, actuator.getChildren().getFirst().getComputation().size());
    assertEquals(1, actuator.getComputation().size());
    assertEquals("rawquality", actuator.getComputation().getFirst().getParameters().get("input").toString());
  }
}
