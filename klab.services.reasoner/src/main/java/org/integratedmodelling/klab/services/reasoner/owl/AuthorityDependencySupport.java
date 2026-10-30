package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

/** Conservative OWL signature dependency proof. No provider calls or missing-term invention. */
public final class AuthorityDependencySupport {
  public record Closure(boolean known, Set<String> authorities, String reason) {}
  /** Owned by one OWL actor. Never stores provider answers, permissions, or UNKNOWN proofs. */
  static final class Cache {
    static final int CAPACITY = 512;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private final OWLOntologyChangeListener listener = changes -> revision.incrementAndGet();
    private OWLOntologyManager manager;
    private Object reasoner;
    private OWLOntology root;
    private Set<OWLOntology> ontologies;
    private long retainedRevision = -1, hits, computations;
    private final Map<Key, Closure> answers = new LinkedHashMap<>(32, .75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<Key, Closure> entry) {
        return size() > CAPACITY;
      }
    };

    void changed() { revision.incrementAndGet(); }

    void reset() {
      if (manager != null) manager.removeOntologyChangeListener(listener);
      manager = null; reasoner = null; root = null; ontologies = null;
      answers.clear(); retainedRevision = -1; changed();
    }

    long prepare(OWL owl) {
      if (manager != owl.manager) {
        reset(); manager = owl.manager;
        manager.addOntologyChangeListener(listener);
      }
      var current = manager.getOntologies();
      var currentRoot = owl.getOntology().getOWLOntology();
      var currentReasoner = owl.structuralReasonerIdentity();
      // Manager create/remove need not emit axiom events; compare by object identity, including
      // an empty same-ID replacement. Reasoner/root replacement must also retire old answers.
      if (ontologies == null || current.size() != ontologies.size()
          || current.stream().anyMatch(ontology -> !ontologies.contains(ontology))
          || root != currentRoot || reasoner != currentReasoner) {
        var owned = Collections.newSetFromMap(new IdentityHashMap<OWLOntology, Boolean>());
        owned.addAll(current); ontologies = owned;
        root = currentRoot; reasoner = currentReasoner; changed();
      }
      long now = revision.get();
      if (retainedRevision != now) { answers.clear(); retainedRevision = now; }
      return now;
    }

    Map<String, Long> metrics() {
      return Map.of("hits", hits, "computations", computations, "entries", (long) answers.size());
    }
  }

  private record Identity(Object value) {
    @Override public boolean equals(Object other) { return other instanceof Identity identity && value == identity.value; }
    @Override public int hashCode() { return System.identityHashCode(value); }
  }
  private record BindingKey(String worldview, String name, String root, Map<String, String> boundaries,
      String id, Identity provider) {
    BindingKey(AuthorityBindings.Binding binding) {
      this(binding.request().worldview(), binding.request().name(), binding.request().rootIdentity(),
          Map.copyOf(binding.request().semanticBoundaries()), binding.id(), new Identity(binding.provider()));
    }
  }
  private record Key(String recipe, String signature, List<BindingKey> bindings) {}
  private final OWL owl;
  public AuthorityDependencySupport(OWL owl) { this.owl=Objects.requireNonNull(owl); }

  public Closure closure(List<String> obligations,List<AuthorityBindings.Binding> bindings) {
    return closure(obligations, bindings, null);
  }

  public Closure closure(List<String> obligations,List<AuthorityBindings.Binding> bindings,String semanticRecipe) {
    synchronized(owl) {
      try {
        owl.requireAvailableClosure();
        if(obligations==null||obligations.isEmpty()||owl.getOntology()==null||!owl.hasReasoner())
          return new Closure(false,Set.of(),"Semantic signature or accepted OWL closure unavailable");
        var cache = owl.authorityDependencyCache();
        long before = cache.prepare(owl);
        var key = new Key(semanticRecipe, signature(obligations), bindings.stream().map(BindingKey::new).toList());
        var cached = cache.answers.get(key);
        if (cached != null && before == cache.prepare(owl)) { cache.hits++; return cached; }
        cache.computations++;
        var result = compute(obligations, bindings);
        if (before != cache.prepare(owl))
          return new Closure(false, Set.of(), "Semantic closure changed during dependency proof");
        if (result.known()) cache.answers.put(key, result);
        return result;
      } catch(RuntimeException unavailable) {
        return new Closure(false,Set.of(),"Semantic locality proof unavailable: "+unavailable.getClass().getSimpleName());
      }
    }
  }

  // Keep raw meanings exact, including collectivity and unsupported wrappers. A bounded-size
  // digest key avoids retaining up to four megabytes of request strings per cached answer.
  private static String signature(List<String> obligations) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      for (var term : obligations) {
        var bytes = term.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private Closure compute(List<String> obligations, List<AuthorityBindings.Binding> bindings) {
        var signature=new HashSet<OWLEntity>();
        for(var term:obligations) {
          OWLClass cls=materialized(term);
          if(cls==null||cls.isOWLNothing()) return new Closure(false,Set.of(),"Unmaterialized or unsupported semantic obligation: "+term);
          signature.add(cls);
        }
        var requested=new HashSet<OWLEntity>(signature);
        // Every term is conservatively an enumeration obligation too. BOT locality alone
        // preserves fixed-signature entailment, not discovery of all possible child types.
        for(var entity:requested)if(entity instanceof OWLClass cls)
          owl.getSubClasses(cls,false).entities().filter(child->!child.isOWLNothing()).forEach(signature::add);
        var authorities=new TreeMap<String,Set<OWLEntity>>();
        var roots=new TreeMap<String,OWLClass>();
        var generatedClasses=new TreeMap<String,Set<OWLClass>>();
        var ontology=owl.getOntology().getOWLOntology();
        var acceptedClasses=ontology.importsClosure().flatMap(OWLOntology::classesInSignature).distinct().toList();
        for(var binding:bindings) {
          var entities=new HashSet<OWLEntity>();
          var root=materialized(binding.request().rootIdentity());
          if(root==null) return new Closure(false,Set.of(),"Configured authority root is not materialized");
          entities.add(root);
          for(var boundary:binding.request().semanticBoundaries().values()) {
            var target=materialized(boundary);
            if(target==null) return new Closure(false,Set.of(),"Configured authority boundary is not materialized");
            entities.add(target);
          }
          var generated=owl.getOntology(AuthorityBindings.ontologyId(binding));
          String generatedIri=generated==null
              ?OWL.INTERNAL_ONTOLOGY_PREFIX+"/"+AuthorityBindings.ontologyId(binding)
              :generated.getOWLOntology().getOntologyID().getOntologyIRI().orElseThrow().toString();
          var known=new HashSet<OWLClass>();
          // Include references in every accepted import, even before a provider has materialized
          // that name. Namespace registration/reference-map absence is not independence proof.
          acceptedClasses.stream().filter(cls->cls.getIRI().toString().startsWith(generatedIri+"#")).forEach(known::add);
          entities.addAll(known);
          authorities.put(binding.request().name(),entities);
          roots.put(binding.request().name(),root);generatedClasses.put(binding.request().name(),known);
        }
        // BOT locality preserves logical consequences over this signature, including negative,
        // boolean, equivalent and inherited expressions; namespace absence alone proves nothing.
        var axioms=ontology.importsClosure().flatMap(OWLOntology::axioms).distinct();
        var module=localModule(axioms,signature);
        var closure=new HashSet<OWLEntity>(signature);
        module.forEach(axiom->axiom.signature().forEach(closure::add));
        var used=new TreeSet<String>();
        for(var entry:authorities.entrySet()) {
          String name=entry.getKey();var entities=entry.getValue();var root=roots.get(name);
          if(!Collections.disjoint(closure,entities)) {used.add(name);continue;}
          // Existing imported authority IRIs that are not proven under their configured root
          // invalidate the restricted-extension premise. Never infer ownership from a name alone.
          if(generatedClasses.get(name).stream().anyMatch(cls->!subtype(cls,root))) {used.add(name);continue;}
          var parents=entities.stream().filter(OWLClass.class::isInstance).map(OWLClass.class::cast)
              .collect(java.util.stream.Collectors.toSet());
          var maximum=owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(parents);
          Boolean maximumSatisfiable=null;
          for(var query:requested)if(query instanceof OWLClass requestedClass) {
            // Every valid provider descendant is below this root. This shortcut requires the
            // rooted-vocabulary check above and does not remove positive module dependencies.
            if(!owl.isSatisfiable(owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(root,requestedClass)))continue;
            // Resolver additions are only subclass edges to roots, any boundary combination,
            // and generated parents. If the satisfiable maximum parent set does not entail Q,
            // no subset can entail Q by monotonicity. An inconsistent maximum proves nothing:
            // smaller valid combinations may qualify, so conservatively retain the dependency.
            if(maximumSatisfiable==null)maximumSatisfiable=owl.isSatisfiable(maximum);
            if(!maximumSatisfiable||subtype(maximum,requestedClass)) {used.add(name);break;}
          }
        }
        return new Closure(true,Collections.unmodifiableSet(used),null);
  }
  private static Set<OWLAxiom> localModule(java.util.stream.Stream<OWLAxiom> axioms,Set<OWLEntity> signature) {
    // OWLAPI's extractor creates a transient ontology in the supplied manager. It must not
    // mutate the accepted manager or ask its captured-catalog IRI mapper to admit an unnamed
    // analysis document. All input axioms are already owned and imports-expanded; this scratch
    // manager only copies in-memory axioms, with no import declarations or document loading.
    var analysis=org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    try {return new SyntacticLocalityModuleExtractor(analysis,axioms,ModuleType.BOT).extract(signature);}
    finally {for(var transientOntology:analysis.ontologies().toList())analysis.removeOntology(transientOntology);}
  }
  private boolean subtype(OWLClassExpression child,OWLClass parent) {
    return child.equals(parent)||owl.getEquivalentClasses(child).contains(parent)
        ||owl.getSuperClasses(child,false).containsEntity(parent);
  }
  private OWLClass materialized(String term) {
    // Canonical Concept collectivity is retained in the request/witness hash, but shares
    // OWL identity with the singular Concept (as OWL.getOWLClass already specifies).
    // This is not Observable-wrapper normalization and never declares a missing expression.
    boolean collective=term.startsWith("each ");
    String singular=collective?term.substring(5):term;
    if(singular.startsWith("each "))return null;
    var direct=owl.getMaterializedClass(term); if(direct!=null)return direct;
    direct=owl.getMaterializedClass(collective?singular:"each "+term);
    if(direct!=null)return direct;
    term=singular;
    int split=term.indexOf(':');
    if(split>0 && term.indexOf(' ')<0) {
      var ontology=owl.getOntology(term.substring(0,split));
      var concept=ontology==null?null:ontology.getConcept(term.substring(split+1));
      if(concept!=null)return owl.getOWLClass(concept);
    }
    return null;
  }
}
