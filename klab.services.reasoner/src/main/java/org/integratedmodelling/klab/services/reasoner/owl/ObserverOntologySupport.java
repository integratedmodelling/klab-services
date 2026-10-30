package org.integratedmodelling.klab.services.reasoner.owl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.semanticweb.owlapi.model.*;

/**
 * Source-owned observable export records, not logical restrictions on the base concept.
 * Callers must supply only accepted, accessible source ontologies. Recorded declaration scope is
 * provenance, not an access grant. Session defaults and selected observer observations are excluded.
 */
public final class ObserverOntologySupport {
  public static final IRI PROPERTY = IRI.create("urn:klab:semantics:observer:observable:v1");
  private static final int MAX_RECORD_CHARS = 262144;
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
          .maxStringLength(MAX_RECORD_CHARS).build()).build())
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

  private ObserverOntologySupport() {}

  public record ObservableRecord(int version, String observableUrn, String absoluteObservableUrn,
      String baseConceptUrn, String observerClassUrn, String sourceNamespace, String sourceProject,
      KlabStatement.Scope sourceScope, int offset, int length) {
    public ObservableRecord {
      if (version != 1) throw invalid("unsupported record version");
      required(sourceNamespace, "source namespace");
      if (offset < -1 || length < 0) throw invalid("invalid source extent");
      if (!Objects.equals(observableUrn, compose(absoluteObservableUrn, baseConceptUrn, observerClassUrn)))
        throw invalid("inconsistent canonical observable identity");
    }
  }

  /** Only authored, namespace-owned observable declarations may create records. Absolute ones do not. */
  public static void compile(OWL owl, Ontology owner, Observable observable, KimObservable source) {
    Objects.requireNonNull(owl); Objects.requireNonNull(owner); Objects.requireNonNull(observable);
    Objects.requireNonNull(source);
    required(source.getNamespace(), "source namespace");
    if (!source.getNamespace().equals(owner.getName()))
      throw invalid("annotation owner differs from source namespace");
    if (observable.getObserverSemantics() == null) {
      if (source.getObserverSemantics() != null) throw invalid("authored observer was lost");
      return;
    }
    if (source.getObserverSemantics() == null) throw invalid("observer is not authored by this source");
    var observer = observable.getObserverSemantics();
    requireAgent(observer);
    String base = observable.asConcept().getUrn();
    String prefix = required(base, "base concept") + " observed as " + required(observer.getUrn(), "observer class");
    String full = required(observable.getUrn(), "observable");
    if (!full.startsWith(prefix) || full.length() > prefix.length() && full.charAt(prefix.length()) != ' ')
      throw invalid("observable identity differs from its structured observer");
    String absolute = base + full.substring(prefix.length());
    var record = new ObservableRecord(1, full, absolute, base, observer.getUrn(),
        source.getNamespace(), source.getProjectName(), source.getScope(),
        source.getOffsetInDocument(), source.getLength());
    var target = owner.getOWLOntology();
    var existing = records(List.of(target));
    for (var previous : existing) requireAgreement(previous, record);
    if (existing.contains(record)) return;
    var factory = target.getOWLOntologyManager().getOWLDataFactory();
    var annotation = factory.getOWLAnnotation(factory.getOWLAnnotationProperty(PROPERTY), factory.getOWLLiteral(encode(record)));
    target.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(target, annotation));
  }

  /** Read asserted ontology records only; no inference, live lookup or access authorization occurs. */
  public static List<ObservableRecord> records(Collection<OWLOntology> ontologies) {
    var found = new LinkedHashSet<ObservableRecord>();
    var identities = new HashMap<String, ObservableRecord>();
    for (var ontology : ontologies) for (var annotation : ontology.getAnnotations()) {
      if (!annotation.getProperty().getIRI().equals(PROPERTY)) continue;
      if (!(annotation.getValue() instanceof OWLLiteral literal)) throw invalid("record must be a literal");
      String value = literal.getLiteral();
      if (value.length() > MAX_RECORD_CHARS) throw invalid("record exceeds size limit");
      final ObservableRecord record;
      try { record = JSON.readValue(value, ObservableRecord.class); }
      catch (Exception failure) { throw invalid("malformed observable record"); }
      var previous = identities.putIfAbsent(record.observableUrn(), record);
      if (previous != null) requireAgreement(previous, record);
      found.add(record);
    }
    return found.stream().sorted(Comparator.comparing(ObserverOntologySupport::encode)).toList();
  }

  /**
   * Resolve canonical fields independently; never parse an observed-as URN as a Concept.
   * No matching record returns null. Malformed records or unavailable canonical semantics fail closed.
   */
  public static Observable restore(String observableUrn, Collection<OWLOntology> ontologies,
      Function<String, Observable> absoluteObservableResolver, Function<String, Concept> observerConceptResolver) {
    var record = records(ontologies).stream().filter(r -> r.observableUrn().equals(observableUrn)).findFirst().orElse(null);
    if (record == null) return null;
    var absolute = absoluteObservableResolver.apply(record.absoluteObservableUrn());
    if (!(absolute instanceof ObservableImpl implementation) || absolute.getObserverSemantics() != null
        || !record.absoluteObservableUrn().equals(absolute.getUrn())
        || absolute.asConcept() == null || absolute.asConcept().is(SemanticType.NOTHING)
        || !record.baseConceptUrn().equals(absolute.asConcept().getUrn()))
      throw invalid("absolute observable cannot be restored canonically");
    var observer = observerConceptResolver.apply(record.observerClassUrn());
    requireAgent(observer);
    if (!record.observerClassUrn().equals(observer.getUrn())) throw invalid("observer class is not canonical");
    var restored = new ObservableImpl(implementation);
    restored.setObserverSemantics(observer);
    restored.setUrn(compose(absolute.getUrn(), absolute.asConcept().getUrn(), observer.getUrn()));
    if (absolute.getReferenceName() != null && observer.getReferenceName() != null)
      restored.setReferenceName(absolute.getReferenceName() + "_observed_as_" + observer.getReferenceName());
    return restored;
  }

  private static String compose(String absolute, String base, String observer) {
    required(absolute, "absolute observable"); required(base, "base concept"); required(observer, "observer class");
    if (!absolute.startsWith(base) || absolute.length() > base.length() && absolute.charAt(base.length()) != ' '
        || absolute.contains(" observed as ") || base.contains(" observed as ") || observer.contains(" observed as "))
      throw invalid("invalid canonical identity fields");
    return base + " observed as " + observer + absolute.substring(base.length());
  }

  private static void requireAgent(Concept observer) {
    // Abstract Agent classes are valid perspective constraints; this does not instantiate an Agent.
    if (observer == null || !observer.is(SemanticType.AGENT) || observer.is(SemanticType.NOTHING))
      throw invalid("observer class must be a resolved Agent");
  }

  private static void requireAgreement(ObservableRecord a, ObservableRecord b) {
    if (a.observableUrn().equals(b.observableUrn()) && (!a.absoluteObservableUrn().equals(b.absoluteObservableUrn())
        || !a.baseConceptUrn().equals(b.baseConceptUrn()) || !a.observerClassUrn().equals(b.observerClassUrn())))
      throw invalid("contradictory records for one observable identity");
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank() || value.length() > MAX_RECORD_CHARS) throw invalid("missing or oversized " + label);
    return value;
  }

  private static String encode(ObservableRecord record) {
    try {
      String value = JSON.writeValueAsString(record);
      if (value.length() > MAX_RECORD_CHARS) throw invalid("record exceeds size limit");
      return value;
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw invalid("cannot encode observable record"); }
  }

  private static KlabValidationException invalid(String reason) {
    return new KlabValidationException("OBSERVER_METADATA_INVALID: " + reason);
  }
}
