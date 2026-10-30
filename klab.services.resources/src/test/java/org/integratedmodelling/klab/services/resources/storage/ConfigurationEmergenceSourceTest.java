package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;

class ConfigurationEmergenceSourceTest {
  @TempDir Path directory;
  private static final String ROOT="""
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract configuration Configuration is core odo:Configuration;
      abstract length Length is core odo:Length;
      abstract process Process is core odo:Process;
      abstract identity Identity is core odo:Identity;
      abstract identity Areal;
      thing Region inherits imod:Areal;
      thing Basin is imod:Region;
      thing Ingredient;
      thing Other;
      configuration Alias equals imod:Configuration;
      """;
  private KimOntology parse(String key,String source) throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(key)),Map.of("imod",ROOT+source));
    var result=manager.getOntologies(false).iterator().next();
    assertTrue(result.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),result.getNotifications().toString());
    return result;
  }
  private KimConceptStatement named(KimOntology source,String name) {
    return source.getStatements().stream().filter(s->s.getUrn().equals(name)).findFirst().orElseThrow();
  }
  private OWLClass owlClass(org.integratedmodelling.klab.services.reasoner.owl.OWL owl,
      org.integratedmodelling.klab.api.knowledge.Concept concept) {
    return owl.getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(c->c.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }
  @Test void shorthandExplicitAliasesAndNestedDeclarationsShareCanonicalTriggersAndTransport() throws Exception {
    var shortSource=parse("short","configuration Pattern is imod:Alias of (imod:Ingredient or imod:Other); "
        +"configuration Parent has children (Child is imod:Alias of imod:Ingredient);");
    var explicit=parse("explicit","configuration Pattern is imod:Alias emerges from (imod:Ingredient or imod:Other); "
        +"configuration Parent has children (Child is imod:Alias emerges from imod:Ingredient);");
    for(var source:List.of(shortSource,explicit)) {
      var statement=named(source,"Pattern");
      assertNull(statement.getDeclaredParent().getInherent());
      assertEquals("imod:Alias",statement.getDeclaredParent().getUrn());
      assertEquals(1,statement.getEmergenceTriggers().size());
      assertEquals(KimConcept.Expression.UNION,statement.getEmergenceTriggers().getFirst().getExpressionType());
      assertEquals(1,named(source,"Parent").getChildren().getFirst().getEmergenceTriggers().size());
      var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      var copy=mapper.readValue(mapper.writeValueAsBytes(source),KimOntology.class);
      assertEquals(statement.getEmergenceTriggers().getFirst().getUrn(),named(copy,"Pattern").getEmergenceTriggers().getFirst().getUrn());
      assertNull(named(copy,"Pattern").getDeclaredParent().getInherent());
    }
  }
  @Test void compiledEmergenceNeverBecomesNativeBearerAndExplicitApplicationScopesDescriptions() throws Exception {
    var source=parse("compiled","""
        configuration Shorthand is imod:Configuration of imod:Ingredient;
        configuration Explicit is imod:Configuration emerges from imod:Ingredient;
        configuration Alternatives is ((imod:Configuration of imod:Ingredient) or (imod:Configuration of imod:Other));
        configuration RegionalPattern is imod:Configuration applies to imod:Region;
        configuration BasinPattern is imod:RegionalPattern applies to imod:Basin;
        length Height is imod:Length of imod:Region requires identity imod:Areal;
        length View is imod:Length of imod:Region describes imod:BasinPattern;
        configuration QualityPattern is imod:Configuration of imod:Height;
        process Flow is imod:Process of imod:Region;
        configuration ProcessPattern is imod:Configuration of imod:Flow;
        configuration ExplicitProcessPattern is imod:Configuration emerges from imod:Flow;
        length Unscoped is imod:Length describes (imod:Configuration of imod:Ingredient);
        """);
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);world.setOntologies(List.of(source));
    var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),CoreOntologyCatalog.bundledCore());
    var captured=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
    var compiled=factory.build(captured,()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    try(var state=compiled.state()) {
      var actor=state.actor();var owl=actor.owl();assertTrue(owl.isConsistent());
      for(var name:List.of("Shorthand","Explicit","RegionalPattern","BasinPattern","QualityPattern","ProcessPattern","ExplicitProcessPattern"))
        assertNull(actor.inherent(actor.resolveConcept("imod:"+name)),name+" must have no native bearer");
      assertTrue(actor.satisfiable(actor.resolveConcept("imod:ProcessPattern")),"Process is a legal dependent emergence ingredient");
      assertTrue(actor.satisfiable(actor.resolveConcept("imod:ExplicitProcessPattern")));
      assertEquals("imod:Region",actor.inherent(actor.resolveConcept("imod:Flow")).getUrn(),"Process native bearer is preserved");
      var f=owl.getOntology("imod").getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
      var property=owl.getProperty("odo:emergesFrom").getOWLEntity().asOWLObjectProperty();
      for(var name:List.of("Shorthand","Explicit")) assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(
          owlClass(owl,actor.resolveConcept("imod:"+name)),f.getOWLObjectSomeValuesFrom(property,
              owlClass(owl,actor.resolveConcept("imod:Ingredient"))))));
      var alternatives=owlClass(owl,actor.resolveConcept("imod:Alternatives"));
      var a=f.getOWLObjectSomeValuesFrom(property,owlClass(owl,actor.resolveConcept("imod:Ingredient")));
      var b=f.getOWLObjectSomeValuesFrom(property,owlClass(owl,actor.resolveConcept("imod:Other")));
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(alternatives,f.getOWLObjectUnionOf(a,b))));
      assertFalse(owl.isEntailed(f.getOWLSubClassOfAxiom(alternatives,a)),"OR does not require its first ingredient unconditionally");
      assertFalse(owl.isEntailed(f.getOWLSubClassOfAxiom(alternatives,b)),"OR does not require its second ingredient unconditionally");
      assertTrue(actor.is(actor.inherent(actor.resolveConcept("imod:View")),actor.resolveConcept("imod:Basin")));
      assertFalse(actor.resolveConcept("imod:View").isAbstract());
      assertEquals("imod:Region",actor.inherent(actor.resolveConcept("imod:Height")).getUrn(),"Emergence does not propagate the configuration's application scope backwards");
      var unscoped=actor.resolveConcept("imod:Unscoped");
      assertNull(actor.inherent(unscoped),"Emergence ingredients do not supply application or native-bearer scope");
      assertFalse(org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport
          .unresolvedDescriptionBearers(owl,unscoped).isEmpty(),"Missing application scope remains unresolved");
      var nested=actor.resolveConcept("imod:Alias of imod:Other");
      assertNotNull(nested);assertNull(actor.inherent(nested));
      owl.flushReasoner(); // The nested expression was just materialized after candidate classification.
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl,nested),f.getOWLObjectSomeValuesFrom(property,
          owlClass(owl,actor.resolveConcept("imod:Other"))))),nested.getUrn()+" "+nested.getType());
    }
  }
  @Test void booleanQualifiedParentsKeepEmergenceInsideEachAlternative() throws Exception {
    var source=parse("branches","configuration Alternatives is ((imod:Configuration of imod:Ingredient) or (imod:Configuration of imod:Other));");
    var declaration=named(source,"Alternatives");
    assertTrue(declaration.getEmergenceTriggers().isEmpty(),"No branch can become an unconditional declaration trigger");
    var parent=declaration.getDeclaredParent().getUrn();
    assertTrue(parent.contains("imod:Ingredient"),parent);assertTrue(parent.contains("imod:Other"),parent);
    assertEquals(2,parent.split(" of ",-1).length-1,parent);
  }
}
