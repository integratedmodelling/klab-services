package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.language.WorldviewValidationScope;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.languages.ObservationStandaloneSetup;
import org.integratedmodelling.languages.ObservationSyntaxAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Diagnostic baseline only. Never admits a live model, evaluates expressions, or submits Jobs. */
class ModelResolutionAdmissionDiagnosticTest {
  @BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private ObservationReasoner selector() {
    var parser = new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader("""
        strategies diagnostic version 2.0;
        strategy 0 named dependent.direct
          for pattern { node { kind = one_of(quality, process); abstract = false; } }
          ensure context.exists(), request.fully_specified($this)
          observe $this;
        """));
    assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.observation.ObservationDocument) parsed.getRootASTElement();
    var syntax = (org.integratedmodelling.languages.api.ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) { fail(message); }
    }.adapt(root, new WorldviewValidationScope());
    var document = LanguageAdapter.INSTANCE.adaptStrategies(syntax, "diagnostic", List.of(), 0L);
    var selector = new ObservationReasoner(mock(Reasoner.class), ignored -> { throw new AssertionError("No declaration expected"); });
    document.getStatements().forEach(selector::registerStrategy);
    return selector;
  }
  private ObservableImpl observable(String urn, SemanticType... types) {
    var concept = new ConceptImpl(); concept.setUrn(urn); concept.setType(EnumSet.copyOf(List.of(types)));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(urn);
    return observable;
  }
  private ServiceContextScope root() {
    var user = mock(UserIdentity.class); when(user.getUsername()).thenReturn("alice");
    var session = new ServiceSessionScope(new ServiceUserScope(user, mock(KlabService.class)));
    var root = new ServiceContextScope(session,
        org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration.builder().id("s.c").owner("alice").build(), user);
    root.setId("s.c"); return root;
  }
  private ObservationImpl region() {
    var region = new ObservationImpl(); region.setId(10);
    region.setObservable(observable("diagnostic:Region", SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    return region;
  }
  private ObservationImpl quality() {
    var observation = new ObservationImpl();
    observation.setObservable(observable("diagnostic:Elevation", SemanticType.QUALITY, SemanticType.LENGTH,
        SemanticType.QUANTIFIABLE, SemanticType.OBSERVABLE));
    return observation;
  }

  @Test void nativeDependentStrategyRequiresFocusButDoesNotRequireNumericalExecution() {
    var selector = selector(); var root = root(); var observation = quality();
    assertTrue(selector.computeMatchingStrategies(observation, root, true).isEmpty());
    var focused = root.within(region());
    var matches = selector.computeMatchingStrategies(observation, focused, true);
    assertEquals(1, matches.size()); assertEquals("dependent.direct", matches.getFirst().getUrn());
    assertNull(root.getContextObservation()); assertEquals(10, focused.getContextObservation().getId());
  }

  @Test void nativeTokenRoundtripRetainsFocusOnAReconstructedServiceScope() throws Exception {
    var source = root().within(region()); var token = ContextScope.getScopeId(source);
    assertEquals("s.c.10", token);
    var reconstructed = spy(root()); var transportedRegion = JacksonConfiguration.newObjectMapper()
        .readValue(JacksonConfiguration.newObjectMapper().writeValueAsBytes(region()), Observation.class);
    doReturn(transportedRegion).when(reconstructed).getObservationForAdmission(10);
    var manager = mock(ScopeManager.class, CALLS_REAL_METHODS);
    var focused = manager.contextualizeScope(reconstructed, ContextScope.parseScopeId(token), Map.of());
    var transportedRequest = JacksonConfiguration.newObjectMapper().readValue(
        JacksonConfiguration.newObjectMapper().writeValueAsBytes(quality()), Observation.class);
    assertEquals(10, focused.getContextObservation().getId());
    assertEquals(1, selector().computeMatchingStrategies(transportedRequest, focused, true).size());
  }

  @Test void unresolvedSnapshotEnvelopeRetainsFocusWithoutRegisteringAnObservation() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var snapshot = region(); snapshot.setId(-2);
    var request = new org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest();
    request.setObservation(quality());
    request.setResolutionConstraints(List.of(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(
        org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.UnresolvedContextObservation, snapshot)));
    var decoded = mapper.readValue(mapper.writeValueAsBytes(request), request.getClass());
    var original = root();
    var focused = original.withResolutionConstraints(decoded.getResolutionConstraints().toArray(
        new org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[0]));
    assertEquals(-2, focused.getContextObservation().getId());
    assertNull(original.getContextObservation());
    assertEquals(1, selector().computeMatchingStrategies(decoded.getObservation(), focused, true).size());
  }

  @Test void focusDoesNotSilentlyConcretizeAnAbstractRequestOnTransport() throws Exception {
    var observation = quality();
    ((ObservableImpl) observation.getObservable()).setAbstract(true);
    ((ConceptImpl) observation.getObservable().getSemantics()).setAbstract(true);
    var mapper = JacksonConfiguration.newObjectMapper();
    var decoded = mapper.readValue(mapper.writeValueAsBytes(observation), Observation.class);
    assertTrue(decoded.getObservable().isAbstract());
    assertTrue(decoded.getObservable().getSemantics().isAbstract());
    assertTrue(selector().computeMatchingStrategies(decoded, root().within(region()), true).isEmpty());
  }
}
