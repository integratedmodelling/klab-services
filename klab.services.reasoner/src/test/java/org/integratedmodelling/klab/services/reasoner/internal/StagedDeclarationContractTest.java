package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagedDeclarationContractTest {
  @TempDir Path directory;
  static KimConceptStatementImpl declaration(String namespace,String name,SemanticType kind) {
    var s=new KimConceptStatementImpl();s.setNamespace(namespace);s.setUrn(name);s.setLength(10);
    s.setType(EnumSet.of(kind));
    if(kind==SemanticType.SUBJECT||kind==SemanticType.AGENT||kind==SemanticType.QUALITY)s.getType().add(SemanticType.OBSERVABLE);
    if(kind==SemanticType.SUBJECT||kind==SemanticType.AGENT)s.getType().add(SemanticType.COUNTABLE);
    if(kind==SemanticType.DOMAIN)s.getType().add(SemanticType.PREDICATE);
    return s;
  }
  static KimConceptImpl reference(String urn) {
    var c=new KimConceptImpl();c.setName(urn);c.setUrn(urn);c.setLength(5);c.setOffsetInDocument(20);return c;
  }
  static WorldviewImpl world(String version,boolean reverse,boolean contradiction,boolean nested) {
    var root=new KimOntologyImpl();root.setUrn("w");root.setSourceCode("root "+version);
    for(var pair:List.of(new Object[]{"Thing","Subject",SemanticType.SUBJECT},new Object[]{"Value","Quality",SemanticType.QUALITY},
        new Object[]{"PatternRoot","Configuration",SemanticType.CONFIGURATION},new Object[]{"Knowledge","KnowledgeDomain",SemanticType.DOMAIN})) {
      var alias=declaration("w",(String)pair[0],(SemanticType)pair[2]);alias.setUpperConceptDefined("odo:"+pair[1]);alias.setAbstract(true);root.getStatements().add(alias);
    }
    var group=declaration("w","Group",SemanticType.SUBJECT);group.setDeclaredParent(reference("w:Thing"));group.setChildrenDisjoint(true);
    group.getChildren().add(declaration("w","Tree",SemanticType.SUBJECT));group.getChildren().add(declaration("w","Rock",SemanticType.SUBJECT));root.getStatements().add(group);
    var k=new KimOntologyImpl();k.setUrn("k");k.setSourceCode("knowledge "+version);k.setImportedOntologies(List.of("w"));k.setDomain(reference("w:Knowledge"));
    var q=declaration("k","Q",SemanticType.QUALITY);q.setDeclaredParent(reference("w:Value"));q.setDeclaredInherent(reference("w:Tree"));q.setOffsetInDocument(reverse?80:1);
    q.getObservablesDescribed().add(new PairImpl<>(reference("k:Pattern"),KimConceptStatement.DescriptionType.DESCRIBES));
    if(nested)q.getChildren().add(declaration("k","Child",SemanticType.QUALITY));
    var pattern=declaration("k","Pattern",SemanticType.CONFIGURATION);pattern.setDeclaredParent(reference("w:PatternRoot"));
    pattern.setDeclaredInherent(reference(contradiction?"w:Rock":"w:Tree"));pattern.setOffsetInDocument(reverse?1:80);pattern.getEmergenceTriggers().add(reference("k:Q"));
    k.getStatements().addAll(reverse?List.of(pattern,q):List.of(q,pattern));
    var world=new WorldviewImpl();world.setUrn("w");world.setWorldviewId("w");world.setEmpty(false);world.setOntologies(new ArrayList<>(List.of(root,k)));return world;
  }
  private SemanticEnvironmentFactory factory(String name) throws Exception {
    var core=new LinkedHashMap<String,byte[]>();
    try(var files=Files.list(Path.of(System.getProperty("klab.test.core.directory")))) {
      for(var file:files.filter(p->p.toString().endsWith(".owl")).toList())core.put(file.getFileName().toString(),Files.readAllBytes(file));
    }
    return new SemanticEnvironmentFactory(directory.resolve(name),core);
  }
  private CandidatePublicationCoordinator.Result<SemanticEnvironment> build(SemanticEnvironmentFactory factory,Worldview world) throws Exception {
    var input=factory.capture(world);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    return factory.build(input,()->false);
  }
  private static KimConceptStatementImpl q(Worldview world) {return (KimConceptStatementImpl)world.getOntologies().get(1).getStatements().stream().filter(s->s.getUrn().equals("Q")).findFirst().orElseThrow();}

  @Test void reciprocalSemanticClausesBothOrdersProduceSameCompletedAxioms() throws Exception {
    var first=build(factory("first"),world("same",false,false,false));
    var second=build(factory("second"),world("same",true,false,false));
    try(var a=first.state();var b=second.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,first.logicalStatus(),first.diagnostics().toString());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,second.logicalStatus(),second.diagnostics().toString());
      // Reordering source changes origin locations; all other axioms and annotations agree.
      assertEquals(withoutOriginLocations(a.actor().owl().getOntology("k").getOWLOntology()),
          withoutOriginLocations(b.actor().owl().getOntology("k").getOWLOntology()));
      assertEquals(1,a.actor().owl().compilationMetrics().get("reasonerCreations"));
    }
  }

  private static Set<org.semanticweb.owlapi.model.OWLAxiom> withoutOriginLocations(org.semanticweb.owlapi.model.OWLOntology ontology) {
    var result=new HashSet<org.semanticweb.owlapi.model.OWLAxiom>();
    for(var axiom:ontology.getAxioms()) {
      if(axiom instanceof org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom annotation
          &&annotation.getProperty().getIRI().toString().equals("urn:klab:validation:bearer-origin"))continue;
      var annotations=axiom.annotations().filter(a->!(a.getProperty().getIRI().toString().equals("urn:klab:validation:bearer-provenance")
          &&a.getValue() instanceof org.semanticweb.owlapi.model.OWLLiteral literal&&literal.getLiteral().startsWith("origin:")))
          .collect(java.util.stream.Collectors.toSet());
      result.add(axiom.getAxiomWithoutAnnotations().getAnnotatedAxiom(annotations));
    }
    return result;
  }

  @Test void nestedConcreteQualitySeesCompletedInheritedDescription() throws Exception {
    var result=build(factory("nested"),world("nested",false,false,true));
    try(var state=result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
      var child=state.actor().owl().getConcept("k:Child");assertNotNull(child);
      assertTrue(new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(state.actor().owl()).hasDescription(child));
    }
  }

  @Test void aboutPreambleResolvesOnlyAfterPhysicalDomainHierarchyIsComplete() throws Exception {
    var world=world("about",false,false,false);
    var root=world.getOntologies().getFirst();
    var physicalRoot=declaration("w","PhysicalRoot",SemanticType.DOMAIN);
    physicalRoot.setUpperConceptDefined("odo:PhysicalDomain");
    var physical=declaration("w","Physical",SemanticType.DOMAIN);physical.setDeclaredParent(reference("w:PhysicalRoot"));
    root.getStatements().addAll(List.of(physicalRoot,physical));
    var domain=reference("w:Knowledge");domain.getAbout().add(reference("w:Physical"));
    ((KimOntologyImpl)world.getOntologies().get(1)).setDomain(domain);
    var result=build(factory("about"),world);
    try(var state=result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
      for(var key:List.of("staged_reasonerCreations","staged_actualFlushes","staged_satisfiabilityQueries","staged_entailmentQueries"))
        assertEquals(0,state.actor().declarationStagingMetrics().get(key),key);
    }
  }

  @Test void laterUniversalContradictionInvalidatesEntireCandidate() throws Exception {
    var result=build(factory("bad"),world("bad",false,true,false));
    try(var state=result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID,result.logicalStatus());
      assertTrue(result.diagnostics().stream().anyMatch(m->m.contains("DESCRIPTION_BEARER_CONFLICT")),result.diagnostics().toString());
    }
  }

  @Test void unknownAndDeletedNamesCannotResolveFromAcceptedSnapshot() {
    var old=world("old",false,false,false);var changed=BeanIngestionPreflight.snapshot(old.getOntologies().get(1));
    changed.getStatements().removeIf(s->s.getUrn().equals("Pattern"));
    assertTrue(BeanIngestionPreflight.errors(List.of(changed),List.of(old)).stream().anyMatch(n->n.getMessage().contains("Undefined concept: k:Pattern")));
  }

  @Test void duplicateNamesAndStructuralCyclesFailTypedBeanGate() {
    var duplicate=world("duplicate",false,false,false);duplicate.getOntologies().get(1).getStatements().add(q(duplicate));
    assertTrue(BeanIngestionPreflight.errors(duplicate).stream().anyMatch(n->n.getMessage().contains("DECLARATION_DUPLICATE")));
    var cycle=world("cycle",false,false,false);var quality=q(cycle);var other=declaration("k","Other",SemanticType.QUALITY);
    quality.setDeclaredParent(reference("k:Other"));other.setDeclaredParent(reference("k:Q"));cycle.getOntologies().get(1).getStatements().add(other);
    assertTrue(BeanIngestionPreflight.errors(cycle).stream().anyMatch(n->n.getMessage().contains("DECLARATION_ANCESTRY_CYCLE")));
  }

  @Test void invalidEndpointAndParentKindsRejectBeforePrivateAssets() throws Exception {
    var bad=world("bad endpoint",false,false,false);q(bad).getObservablesDescribed().clear();
    q(bad).getObservablesDescribed().add(new PairImpl<>(reference("w:Knowledge"),KimConceptStatement.DescriptionType.DESCRIBES));
    var factory=factory("no-assets");var input=factory.capture(bad);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID,input.beanStatus());
    assertThrows(IllegalArgumentException.class,()->factory.build(input,()->false));assertFalse(Files.exists(directory.resolve("no-assets")));
    var parent=world("wrong parent",false,false,false);q(parent).setDeclaredParent(reference("w:Tree"));
    assertTrue(BeanIngestionPreflight.errors(parent).stream().anyMatch(n->n.getMessage().contains("DECLARATION_PARENT_TYPE_MISMATCH")));
  }

  @Test void missingCoreAliasAndUnresolvedGenericKindFailBeforePrivateAssets() throws Exception {
    var bad=world("unknown core",false,false,false);((KimConceptStatementImpl)bad.getOntologies().getFirst().getStatements().getFirst()).setUpperConceptDefined("odo:MisspelledSubject");
    var input=factory("core-invalid").capture(bad);assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID,input.beanStatus());
    assertTrue(input.diagnostics().stream().anyMatch(s->s.contains("CORE_ALIAS_TARGET_UNAVAILABLE")));
    var unresolved=world("unresolved",false,false,false);q(unresolved).setType(EnumSet.of(SemanticType.NOTHING));
    assertTrue(BeanIngestionPreflight.errors(unresolved).stream().anyMatch(n->n.getMessage().contains("DECLARATION_TYPE_UNRESOLVED")));
  }

  @Test void transportedParentFlagsCannotOverrideTheActualSourceDeclarationKind() throws Exception {
    var bad=world("misleading parent flags",false,false,false);
    var parent=reference("w:Tree");parent.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
    q(bad).setDeclaredParent(parent);
    var input=factory("misleading-parent").capture(bad);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID,input.beanStatus(),input.diagnostics().toString());
    assertTrue(input.diagnostics().stream().anyMatch(m->m.contains("DECLARATION_PARENT_TYPE_MISMATCH")));
    assertFalse(Files.exists(directory.resolve("misleading-parent")));
  }

  @Test void wrappedParentsAndOrdinaryAliasesUseActualNamedKinds() {
    for(boolean alias:List.of(false,true)) {
      var bad=world("wrapped-or-alias",false,false,false);var target=reference("k:Pattern");
      target.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
      var wrapped=new KimConceptImpl();wrapped.setObservable(target);wrapped.setType(target.getType());
      q(bad).setDeclaredParent(wrapped);q(bad).setAlias(alias);
      assertTrue(BeanIngestionPreflight.errors(bad).stream().anyMatch(n->n.getMessage().contains("DECLARATION_PARENT_TYPE_MISMATCH")),"alias="+alias);
    }
  }

  @Test void unaryParentKindsAreDerivedFromActualOperandKinds() {
    var valid=world("unary",false,false,false);var count=new KimConceptImpl();
    count.setObservable(reference("w:Tree"));count.setSemanticModifier(org.integratedmodelling.klab.api.lang.UnarySemanticOperator.COUNT);
    count.setType(EnumSet.of(SemanticType.SUBJECT));q(valid).setDeclaredParent(count);
    assertFalse(BeanIngestionPreflight.errors(valid).stream().anyMatch(n->n.getMessage().contains("DECLARATION_PARENT_TYPE_MISMATCH")));
    q(valid).setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE));
    assertTrue(BeanIngestionPreflight.errors(valid).stream().anyMatch(n->n.getMessage().contains("DECLARATION_PARENT_TYPE_MISMATCH")));
  }

  @Test void publishedDescriptionChainsTraceAuthoredBearerSourceAndLocation() throws Exception {
    for(boolean reverse:List.of(false,true)) {
      var world=world("provenance",false,false,false);var source=(KimOntologyImpl)world.getOntologies().get(1);
      var a=declaration("k","A",SemanticType.QUALITY);a.setAbstract(true);a.setDeclaredParent(reference("w:Value"));a.setDeclaredInherent(reference("w:Tree"));
      var b=declaration("k","B",SemanticType.QUALITY);b.setDeclaredParent(reference("w:Value"));b.getObservablesDescribed().add(new PairImpl<>(reference("k:A"),KimConceptStatement.DescriptionType.DESCRIBES));
      var c=declaration("k","C",SemanticType.QUALITY);c.setDeclaredParent(reference("w:Value"));c.getObservablesDescribed().add(new PairImpl<>(reference("k:B"),KimConceptStatement.DescriptionType.DESCRIBES));
      String prefix="ontology k using w in domain w:Knowledge; ";
      String aText="abstract quality A is w:Value within w:Tree;",bText="quality B is w:Value describes k:A;",cText="quality C is w:Value describes k:B;";
      String text=prefix+(reverse?cText+" "+bText+" "+aText:aText+" "+bText+" "+cText);
      source.setSourceCode(text);source.setProjectName("imod");source.getStatements().clear();source.getStatements().addAll(reverse?List.of(c,b,a):List.of(a,b,c));
      for(var pair:List.of(new Object[]{a,aText},new Object[]{b,bText},new Object[]{c,cText})) {
        var statement=(KimConceptStatementImpl)pair[0];statement.setOffsetInDocument(text.indexOf((String)pair[1]));statement.setLength(((String)pair[1]).length());
      }
      var factory=factory("provenance-"+reverse);
      try(var coordinator=new CandidatePublicationCoordinator<Worldview,SemanticEnvironment>(factory,input->true)) {
        coordinator.submit(factory.capture(world));assertTrue(coordinator.awaitIdle(Duration.ofSeconds(20)));
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,coordinator.status().logicalStatus(),coordinator.status().diagnostics().toString());
        try(var lease=coordinator.accepted().orElseThrow()) {
          var owl=lease.state().actor().owl();var ontology=owl.getOntology("k").getOWLOntology();
          var aClass=ontology.getClassesInSignature().stream().filter(x->x.getIRI().getShortForm().equals("A")).findFirst().orElseThrow();
          var cClass=ontology.getClassesInSignature().stream().filter(x->x.getIRI().getShortForm().equals("C")).findFirst().orElseThrow();
          var definitions=ontology.getAnnotationAssertionAxioms(aClass.getIRI()).stream()
              .filter(x->x.getProperty().getIRI().toString().equals("urn:klab:validation:bearer-origin"))
              .map(x->((org.semanticweb.owlapi.model.OWLLiteral)x.getValue()).getLiteral()).toList();
          var cEvidence=ontology.getSubClassAxiomsForSubClass(cClass).stream().flatMap(x->x.annotations())
              .filter(x->x.getProperty().getIRI().toString().equals("urn:klab:validation:bearer-provenance"))
              .map(x->x.getValue().toString()).toList();
          if(definitions.isEmpty())assertTrue(cEvidence.stream().anyMatch(x->x.contains("k:A")||x.contains("/k#A")),
              "C lost A's authored origin through B; published evidence="+cEvidence);
          var origin=definitions.stream().filter(x->x.contains("|source=k:A|")&&x.contains("|clause=within|"))
              .findFirst().orElseThrow(()->new AssertionError("Missing A's authored source origin: "+definitions));
          assertTrue(origin.contains("|document=k|"));assertTrue(origin.contains("|offset="+a.getOffsetInDocument()+"|"));assertTrue(origin.contains("|length="+a.getLength()+"|"));
          assertTrue(origin.contains("|sourceHash="+org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(text)+"|"));
          String id=origin.substring(0,origin.indexOf('|'));
          var references=ontology.getSubClassAxiomsForSubClass(cClass).stream().flatMap(x->x.annotations())
              .filter(x->x.getProperty().getIRI().toString().equals("urn:klab:validation:bearer-provenance"))
              .map(x->x.getValue()).filter(org.semanticweb.owlapi.model.OWLLiteral.class::isInstance)
              .map(x->((org.semanticweb.owlapi.model.OWLLiteral)x).getLiteral()).filter(x->x.startsWith("origin:")).collect(java.util.stream.Collectors.toSet());
          assertEquals(Set.of(id),references,"C must reference A's original bound without nested annotation expansion");
        }
      }
    }
  }

  @Test void invalidStagedEditRetainsAcceptedGenerationAndLeasedAxioms() throws Exception {
    var factory=factory("atomic");
    try(var coordinator=new CandidatePublicationCoordinator<Worldview,SemanticEnvironment>(factory,input->true)) {
      coordinator.submit(factory.capture(world("good",false,false,false)));assertTrue(coordinator.awaitIdle(Duration.ofSeconds(20)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,coordinator.status().logicalStatus(),coordinator.status().diagnostics().toString());
      long acceptedGeneration=coordinator.status().acceptedGeneration();
      try(var lease=coordinator.accepted().orElseThrow()) {
        var accepted=lease.state().actor().owl().getOntology("k").getOWLOntology().getAxioms();
        coordinator.submit(factory.capture(world("bad",true,true,false)));assertTrue(coordinator.awaitIdle(Duration.ofSeconds(20)));
        assertEquals(acceptedGeneration,coordinator.status().acceptedGeneration());
        assertEquals(accepted,lease.state().actor().owl().getOntology("k").getOWLOntology().getAxioms());
      }
    }
  }
}
