package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Optional regression over an exported accepted actor; fixture data is deliberately not committed.
 * Run with -Dklab.test.acceptedOwlSnapshot=/absolute/path/to/snapshot.
 * Every document and annotated axiom set is verified; uncaptured imports cannot access the network.
 */
@EnabledIfSystemProperty(named = "klab.test.acceptedOwlSnapshot", matches = ".+")
class AcceptedSnapshotBoundDiscoveryTest {

  static final String IDENTITY = "TAXA:[3DSK5]";

  static Object field(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  static class Fixture implements AutoCloseable {

    final OWL owl;

    final OWLOntology root, trials;

    final OWLDataFactory f;

    final TargetedBoundDiscoveryTest.CounterReasoner dl;

    final Map<String, ConceptImpl> concepts = new HashMap<>();

    final Map<String, OWLClass> classes = new HashMap<>();

    Fixture(Path directory, JsonNode manifest) throws Exception {
      var scope = (Scope) Proxy.newProxyInstance(Scope.class.getClassLoader(), new Class<?>[] { Scope.class }, (o, m, a) -> null);
      owl = new OWL(scope);
      owl.manager = OWLManager.createOWLOntologyManager();
      f = owl.manager.getOWLDataFactory();
      var local = new HashMap<String, IRI>();
      for (var d : manifest.path("documents")) {
        var p = directory.resolve(d.path("file").asText());
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
        if (!hash.equals(d.path("sha256").asText()))
          throw new IllegalStateException("Snapshot hash mismatch");
        var iri = IRI.create(p.toAbsolutePath().toUri());
        local.put(d.path("ontologyIri").asText(), iri);
        if (!d.path("versionIri").isNull())
          local.put(d.path("versionIri").asText(), iri);
      }
      local.put("urn:codex:offline:trials", IRI.create(Path.of("offline-targeted/scratch-trials.ofn").toAbsolutePath().toUri()));
      owl.manager.getIRIMappers().add(iri -> {
        var p = local.get(iri.toString());
        if (p == null)
          throw new IllegalStateException("Offline import not captured: " + iri);
        return p;
      });
      for (var d : manifest.path("documents")) if (owl.manager.getOntologies().stream().noneMatch(o -> o.getOntologyID().toString().equals(d.path("ontologyId").asText())))
        owl.manager.loadOntologyFromOntologyDocument(directory.resolve(d.path("file").asText()).toFile());
      root = owl.manager.getOntologies().stream().filter(o -> o.getOntologyID().toString().equals(manifest.path("rootOntologyId").asText())).findFirst().orElseThrow();
      var closure = root.importsClosure().map(o -> o.getOntologyID().toString()).sorted().toList();
      var expected = new ArrayList<String>();
      manifest.path("reasonerImportClosure").forEach(n -> expected.add(n.asText()));
      Collections.sort(expected);
      if (!closure.equals(expected))
        throw new IllegalStateException("Import closure changed in reload");
      for (var d : manifest.path("documents")) {
        var ontology = owl.manager.getOntologies().stream().filter(o -> o.getOntologyID().toString().equals(d.path("ontologyId").asText())).findFirst().orElseThrow();
        if (ontology.getAxiomCount() != d.path("axioms").asInt())
          throw new IllegalStateException("Reload axiom count mismatch: " + d.path("ontologyId").asText() + " expected=" + d.path("axioms").asInt() + " actual=" + ontology.getAxiomCount());
        var hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n", ontology.axioms().map(Object::toString).sorted().toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (!hash.equals(d.path("axiomSetSha256").asText()))
          throw new IllegalStateException("Exact axiom-set hash mismatch: " + d.path("ontologyId").asText());
      }
      var namespaces = (Map<String, Ontology>) field(owl, "ontologies");
      for (var n : manifest.path("namespaces")) {
        var ontology = owl.manager.getOntologies().stream().filter(o -> o.getOntologyID().toString().equals(n.path("ontologyId").asText())).findFirst().orElseThrow();
        var wrapper = new Ontology(owl, ontology, n.path("namespace").asText());
        wrapper.setInternal(n.path("internal").asBoolean());
        namespaces.put(n.path("namespace").asText(), wrapper);
      }
      var mappedClasses = (Map<String, OWLClass>) field(owl, "owlClasses");
      var mappedConcepts = (Map<String, ConceptImpl>) field(owl, "conceptsById");
      mappedClasses.clear();
      mappedConcepts.clear();
      for (var n : manifest.path("concepts")) {
        var c = new ConceptImpl();
        c.setUrn(n.path("urn").asText());
        c.setName(n.path("name").asText());
        c.setNamespace(n.path("namespace").asText());
        var types = EnumSet.noneOf(SemanticType.class);
        n.path("types").forEach(t -> types.add(SemanticType.valueOf(t.asText())));
        c.setType(types);
        var cls = f.getOWLClass(IRI.create(n.path("iri").asText()));
        mappedClasses.put(n.path("key").asText(), cls);
        mappedConcepts.put(n.path("key").asText(), c);
        classes.put(c.getUrn(), cls);
        concepts.put(c.getUrn(), c);
      }
      for (var n : manifest.path("namespaces")) {
        var wrapper = namespaces.get(n.path("namespace").asText());
        var ids = (Map<String, Concept>) field(wrapper, "conceptIDs");
        ids.clear();
        for (var c : concepts.values()) if (c.getNamespace().equals(n.path("namespace").asText()))
          ids.put(c.getName(), c);
      }
      var iri2ns = (Map<String, String>) field(owl, "iri2ns");
      iri2ns.clear();
      manifest.path("iri2ns").fields().forEachRemaining(e -> iri2ns.put(e.getKey(), e.getValue().asText()));
      trials = owl.manager.createOntology(IRI.create("urn:codex:offline:trials"));
      owl.manager.applyChange(new AddImport(root, f.getOWLImportsDeclaration(IRI.create("urn:codex:offline:trials"))));
      dl = new TargetedBoundDiscoveryTest.CounterReasoner(root);
      var reasoner = OWL.class.getDeclaredField("reasoner");
      reasoner.setAccessible(true);
      reasoner.set(owl, dl);
    }

    Object support(boolean targeted) {
      return new OWLSemanticClauseSupport(owl);
    }

    List<String> bounds(Object support) throws Exception {
      var m = support.getClass().getDeclaredMethod("bounds", Concept.class);
      m.setAccessible(true);
      return ((List<?>) m.invoke(support, concepts.get(IDENTITY))).stream().map(Object::toString).sorted().toList();
    }

    Concept trial(int index, String base) throws Exception {
      var c = new ConceptImpl();
      String name = String.format("Candidate%02d", index);
      c.setUrn("offlineTrial:" + name);
      c.setNamespace("offlineTrial");
      c.setName(name);
      c.setType(new HashSet<>(concepts.get(base).getType()));
      var cls = f.getOWLClass(IRI.create("urn:codex:offline:trials#" + name));
      var property = owl.getProperty("odo:hasIdentity").getOWLEntity().asOWLObjectProperty();
      owl.manager.addAxioms(trials, Set.of(f.getOWLDeclarationAxiom(cls), f.getOWLSubClassOfAxiom(cls, classes.get(base)), f.getOWLSubClassOfAxiom(cls, f.getOWLObjectSomeValuesFrom(property, classes.get(IDENTITY)))));
      ((Map<String, OWLClass>) field(owl, "owlClasses")).put(c.getUrn(), cls);
      ((Map<String, ConceptImpl>) field(owl, "conceptsById")).put(c.getUrn(), c);
      classes.put(c.getUrn(), cls);
      concepts.put(c.getUrn(), c);
      return c;
    }

    public void close() {
      dl.dispose();
    }
  }

  @Test
  void acceptedSnapshotPreservesBoundsAndFinalSatisfiabilityAcrossTwentySevenMutations() throws Exception {
    var directory = Path.of(System.getProperty("klab.test.acceptedOwlSnapshot"));
    var manifest = new ObjectMapper().readTree(directory.resolve("manifest.json").toFile());
    var candidateIds = List.of("imod:PhysicalPhenomena", "imod:Earth", "imod:Life", "imod:Society", "imod:BuiltEnvironment", "imod:Agency", "imod:AtmosphereKnowledge", "imod:Biology", "imod:Chemistry", "imod:Ecology", "imod:Economics", "imod:Engineering", "imod:Genetics", "imod:Geography", "imod:Geology", "imod:Hydrology", "imod:Land", "imod:Oceanography", "imod:Physics", "imod:Sociology", "imod:Soil", "imod:Valuation", "imod:Puntal", "imod:Lineal", "imod:Areal", "imod:Volumetric", "odo:Subject");
    try (var reference = new Fixture(directory, manifest);
      var candidate = new Fixture(directory, manifest)) {
      assertTrue(reference.concepts.keySet().containsAll(candidateIds));
      assertTrue(candidate.concepts.containsKey(IDENTITY));
      for (int i = 0; i < candidateIds.size(); i++) {
        var referenceHead = reference.trial(i, candidateIds.get(i));
        var candidateHead = candidate.trial(i, candidateIds.get(i));
        var oracle = new OWLSemanticClauseSupport(reference.owl);
        var actual = new OWLSemanticClauseSupport(candidate.owl);
        assertEquals(TargetedBoundDiscoveryTest.normalize(TargetedBoundDiscoveryTest.legacyBounds(reference.owl, oracle, reference.concepts.get(IDENTITY))), TargetedBoundDiscoveryTest.actualBounds(actual, candidate.concepts.get(IDENTITY)), candidateIds.get(i));
        assertEquals(oracle.applicableTo(reference.concepts.get(IDENTITY), referenceHead), actual.applicableTo(candidate.concepts.get(IDENTITY), candidateHead), candidateIds.get(i));
        assertEquals(reference.owl.isSatisfiable(reference.classes.get(referenceHead.getUrn())), candidate.owl.isSatisfiable(candidate.classes.get(candidateHead.getUrn())), candidateIds.get(i));
      }
      assertEquals(27, reference.dl.classifications);
      assertEquals(0, candidate.dl.classifications);
      assertEquals(0, candidate.dl.globalQueries);
    }
  }
}
