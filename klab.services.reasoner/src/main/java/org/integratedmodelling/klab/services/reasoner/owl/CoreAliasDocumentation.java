package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.lang.ref.WeakReference;
import java.util.stream.Collectors;
import com.google.common.collect.MapMaker;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.runtime.language.SemanticDocumentation;
import org.semanticweb.owlapi.model.*;

/** Source-owned interpretations of existing core classes, private to one semantic actor. */
public final class CoreAliasDocumentation {
  private static final String NS = "http://integratedmodelling.org/ks/klab.owl#";
  public static final IRI DECLARATION = IRI.create(NS + "interpretationDeclaration");
  public static final IRI WORLDVIEW = IRI.create(NS + "interpretationWorldview");
  public static final IRI SOURCE_HASH = IRI.create(NS + "interpretationSourceHash");
  public static final IRI OFFSET = IRI.create(NS + "interpretationOffset");
  public static final IRI LENGTH = IRI.create(NS + "interpretationLength");
  private final OWL owl;
  // Track by identity: aliases compare equal but have separate metadata. Weak references let
  // obsolete delegate copies disappear after an incremental replacement clears its caches.
  private final Map<IRI, List<WeakReference<Concept>>> views = new HashMap<>();
  private final Set<Concept> observedViews = Collections.newSetFromMap(
      new MapMaker().weakKeys().<Concept, Boolean>makeMap());

  CoreAliasDocumentation(OWL owl) { this.owl = owl; }

  public record Interpretation(String declaration, String worldview, String sourceHash,
      String offset, String length, String text) {}

  private static String value(OWLAnnotationAssertionAxiom axiom, IRI property) {
    return axiom.getAnnotations().stream().filter(a -> a.getProperty().getIRI().equals(property))
        .map(OWLAnnotation::getValue).filter(v -> v instanceof OWLLiteral)
        .map(v -> ((OWLLiteral)v).getLiteral()).sorted().findFirst().orElse("");
  }

  private static boolean owned(OWLAnnotationAssertionAxiom axiom) {
    return !value(axiom, DECLARATION).isEmpty();
  }

  /** Replace only this declaration's prior interpretation, including a removed/blank docstring. */
  public void replace(Ontology owner, Concept target, Concept delegate,
      KimConceptStatement declaration, KimOntology source, String worldview) {
    String id = declaration.getNamespace() + ":" + declaration.getUrn();
    var ontology = owner.getOWLOntology();
    var manager = ontology.getOWLOntologyManager();
    var factory = manager.getOWLDataFactory();
    var previous = ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION).stream()
        .filter(a -> id.equals(value(a, DECLARATION))).collect(Collectors.toSet());
    var affected = new HashSet<IRI>();
    previous.forEach(a -> { if (a.getSubject() instanceof IRI subject) affected.add(subject); });
    manager.removeAxioms(ontology, previous);
    var iri = owl.getOWLClass(target).getIRI();
    affected.add(iri);
    synchronized (views) { register(iri, target); register(iri, delegate); }
    String text = declaration.getDocstring();
    if (text != null && !text.isBlank()) {
      var provenance = new HashSet<OWLAnnotation>();
      provenance.add(annotation(factory, DECLARATION, id));
      if (worldview != null && !worldview.isBlank()) provenance.add(annotation(factory, WORLDVIEW, worldview));
      if (source != null && source.getSourceCode() != null)
        provenance.add(annotation(factory, SOURCE_HASH, SemanticValidationRequest.sourceHash(source.getSourceCode())));
      provenance.add(annotation(factory, OFFSET, Integer.toString(declaration.getOffsetInDocument())));
      provenance.add(annotation(factory, LENGTH, Integer.toString(declaration.getLength())));
      manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(
          factory.getRDFSComment(), iri, factory.getOWLLiteral(text), provenance));
    }
    refresh(affected);
  }

  private static OWLAnnotation annotation(OWLDataFactory factory, IRI property, String value) {
    return factory.getOWLAnnotation(factory.getOWLAnnotationProperty(property), factory.getOWLLiteral(value));
  }

  public List<Interpretation> interpretations(Concept target) {
    var cls = owl.getOWLClass(target);
    return cls == null ? List.of() : interpretations(cls.getIRI(), ontologies());
  }

  private Set<OWLOntology> ontologies() {
    var result = new HashSet<OWLOntology>();
    owl.getOntologies(true).forEach(o -> result.addAll(o.getOWLOntology().getImportsClosure()));
    return result;
  }

  private List<Interpretation> interpretations(IRI iri, Collection<OWLOntology> ontologies) {
    var result = new ArrayList<Interpretation>();
    for (var ontology : ontologies) for (var axiom : ontology.getAnnotationAssertionAxioms(iri)) {
      if (axiom.getProperty().isComment() && owned(axiom) && axiom.getValue() instanceof OWLLiteral text)
        result.add(new Interpretation(value(axiom, DECLARATION), value(axiom, WORLDVIEW),
            value(axiom, SOURCE_HASH), value(axiom, OFFSET), value(axiom, LENGTH), text.getLiteral()));
    }
    return result.stream().distinct().sorted(Comparator.comparing(Interpretation::declaration)
        .thenComparing(Interpretation::sourceHash).thenComparing(Interpretation::text)).toList();
  }

  /** Also initializes deterministic core-background fallback when no alias exists. */
  void track(Concept concept) {
    var cls = owl.getOWLClass(concept);
    synchronized (views) {
      if (cls != null && register(cls.getIRI(), concept))
        refresh(cls.getIRI(), views.get(cls.getIRI()), ontologies());
    }
  }

  private boolean register(IRI iri, Concept concept) {
    // MapMaker's weak keys use identity, matching the separately copied alias metadata beans.
    // Already-observed warm lookups avoid scanning all aliases of this core class.
    if (!observedViews.add(concept)) return false;
    var tracked = views.computeIfAbsent(iri, key -> new ArrayList<>());
    tracked.removeIf(reference -> reference.get() == null);
    tracked.add(new WeakReference<>(concept));
    return true;
  }

  Set<IRI> documentedSubjects(Ontology owner) {
    var result = new HashSet<IRI>();
    for (var axiom : owner.getOWLOntology().getAxioms(AxiomType.ANNOTATION_ASSERTION))
      if (owned(axiom) && axiom.getSubject() instanceof IRI subject) result.add(subject);
    return result;
  }

  /** Refresh only affected identities, using one import-closure snapshot per mutation. */
  void refresh(Collection<IRI> affected) {
    if (affected.isEmpty()) return;
    var ontologies = ontologies();
    synchronized (views) {
      for (var iri : affected) {
        var tracked = views.get(iri);
        if (tracked != null) refresh(iri, tracked, ontologies);
      }
    }
  }

  private void refresh(IRI iri, List<WeakReference<Concept>> concepts, Collection<OWLOntology> ontologies) {
      var interpretations = interpretations(iri, ontologies);
      String description;
      if (interpretations.size() == 1) description = interpretations.getFirst().text();
      else if (!interpretations.isEmpty()) description = interpretations.stream()
          .map(i -> i.declaration() + ": " + i.text()).collect(Collectors.joining("\n\n"));
      else {
        var background = new TreeSet<String>();
        for (var ontology : ontologies) for (var axiom : ontology.getAnnotationAssertionAxioms(iri)) {
          if (!owned(axiom) && (axiom.getProperty().isComment()
              || axiom.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/description"))
              && axiom.getValue() instanceof OWLLiteral text && !text.getLiteral().isBlank())
            background.add(text.getLiteral());
        }
        description = String.join("\n\n", background);
      }
      concepts.removeIf(reference -> reference.get() == null);
      for (var reference : concepts) {
        var concept = reference.get();
        if (concept == null) continue;
        if (description.isEmpty()) concept.getMetadata().remove(Metadata.DC_COMMENT);
        else concept.getMetadata().put(Metadata.DC_COMMENT, description);
      }
  }

  void reset() { synchronized (views) { views.clear(); observedViews.clear(); } }

  public String describe(Concept target) {
    var entries = interpretations(target);
    if (entries.isEmpty()) return "";
    var out = new StringBuilder("## Hosted worldview interpretation\n\n")
        .append("Quoted declaration meanings below interpret this same core class. Original core comments remain background documentation.\n\n");
    for (var entry : entries) {
      out.append("### ").append(SemanticDocumentation.code(entry.declaration())).append("\n\n")
          .append(entry.text()).append("\n\n")
          .append("Source worldview: ").append(SemanticDocumentation.code(entry.worldview()))
          .append("; source hash: ").append(SemanticDocumentation.code(entry.sourceHash()))
          .append("; declaration offset/length: ").append(SemanticDocumentation.code(entry.offset() + "/" + entry.length()))
          .append(".\n\n");
    }
    return out.toString();
  }
}
