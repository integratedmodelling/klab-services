package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.runtime.language.CapturedSemanticExpressionParser;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericQualityInheritanceTest {
  @TempDir Path directory;
  @SuppressWarnings("unchecked")
  private Set<SemanticType> types(SemanticSyntax.Type type) throws Exception {
    var method=LanguageAdapter.class.getDeclaredMethod("adaptSemanticType",SemanticSyntax.Type.class);
    method.setAccessible(true);
    return EnumSet.copyOf((Set<SemanticType>)method.invoke(LanguageAdapter.INSTANCE,type));
  }
  private KimConceptStatementImpl declaration(String name,SemanticSyntax.Type type,boolean abstr) throws Exception {
    var result=new KimConceptStatementImpl();result.setNamespace("imod");result.setUrn(name);
    result.setType(types(type));result.setGenericQuality(type==SemanticSyntax.Type.GENERIC_QUALITY);
    result.setAbstract(abstr);result.setOffsetInDocument(1);
    if(abstr)result.getType().add(SemanticType.ABSTRACT);
    return result;
  }
  private WorldviewImpl world() throws Exception {
    var ontology=new KimOntologyImpl();ontology.setUrn("imod");ontology.setProjectName("generic-quality-test");
    ontology.setSourceCode(" ".repeat(2048));
    for(var item:List.of(Map.entry("Quality",SemanticSyntax.Type.GENERIC_QUALITY),
        Map.entry("Mass",SemanticSyntax.Type.MASS),Map.entry("Length",SemanticSyntax.Type.LENGTH))) {
      var alias=declaration(item.getKey(),item.getValue(),true);
      alias.setUpperConceptDefined("odo:"+item.getKey());ontology.getStatements().add(alias);
    }
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));return world;
  }
  private SemanticEnvironment compile(Worldview world,String label) throws Exception {
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl")) {
      var root=System.getProperty("klab.test.core.directory");
      if(root!=null)core.put(name,Files.readAllBytes(Path.of(root,name)));
      else try(var input=getClass().getResourceAsStream("/knowledge/"+name)) {
        assertNotNull(input);core.put(name,input.readAllBytes());
      }
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve(label),core);
    var input=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var result=factory.build(input,()->false);
    assertNotNull(result.state(),result.diagnostics().toString());
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
    return result.state();
  }
  private org.semanticweb.owlapi.model.OWLClass owlClass(ReasonerService actor,Concept concept) {
    return actor.owl().getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(c->c.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }
  private boolean isA(ReasonerService actor,Concept child,Concept parent) {
    var f=actor.owl().getOntology(child.getNamespace()).getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
    return actor.owl().isEntailed(f.getOWLSubClassOfAxiom(owlClass(actor,child),owlClass(actor,parent)));
  }
  @Test void parentlessGenericQualityHasQualityAncestryWithoutInheritingAbstractFlag() throws Exception {
    var world=world();var statements=world.getOntologies().getFirst().getStatements();
    statements.add(declaration("Heterogeneity",SemanticSyntax.Type.GENERIC_QUALITY,true));
    statements.add(declaration("PlainQuality",SemanticSyntax.Type.GENERIC_QUALITY,false));
    var child=declaration("QualityChild",SemanticSyntax.Type.GENERIC_QUALITY,false);
    child.setDeclaredParent(new CapturedSemanticExpressionParser(world).concept("imod:Quality"));statements.add(child);
    try(var state=compile(world,"parentless")) {
      var actor=state.actor();var core=actor.owl().getConcept("odo:Quality");assertTrue(core.isAbstract());
      assertEquals("odo:Quality",actor.owl().getConcept("imod:Quality").getUrn());
      for(var name:List.of("Heterogeneity","PlainQuality","QualityChild")) {
        var concept=actor.owl().getConcept("imod:"+name);assertNotNull(concept);
        assertTrue(concept.is(SemanticType.QUALITY));assertTrue(isA(actor,concept,core),name);
        assertEquals(name.equals("Heterogeneity"),concept.isAbstract(),name);
        System.out.println("RESULT GENERIC "+name+" isQuality=true abstract="+concept.isAbstract());
      }
    }
  }
  @Test void derivedAndInheritedGenericRatiosRetainTypeOperatorAncestryAndParentUnit() throws Exception {
    var world=world();var statements=world.getOntologies().getFirst().getStatements();
    var parser=new CapturedSemanticExpressionParser(world);
    for(var item:List.of(Map.entry("Y",SemanticSyntax.Type.MASS),Map.entry("Z",SemanticSyntax.Type.LENGTH))) {
      var operand=declaration(item.getKey(),item.getValue(),false);
      operand.setDeclaredParent(parser.concept(item.getValue()==SemanticSyntax.Type.MASS?"imod:Mass":"imod:Length"));
      operand.getMetadata().put(CoreOntology.NS.SI_UNIT_PROPERTY,item.getValue()==SemanticSyntax.Type.MASS?"kg":"m");
      statements.add(operand);
    }
    var expression=new CapturedSemanticExpressionParser(world).concept("ratio of imod:Y to imod:Z");
    assertTrue(expression.is(SemanticType.RATIO));
    var derived=declaration("DerivedRatio",SemanticSyntax.Type.GENERIC_QUALITY,false);
    derived.setDeclaredParent(expression);statements.add(derived);
    var inherited=declaration("InheritedRatio",SemanticSyntax.Type.GENERIC_QUALITY,false);
    inherited.setDeclaredParent(new CapturedSemanticExpressionParser(world).concept("imod:DerivedRatio"));statements.add(inherited);
    var explicit=declaration("ExplicitAbstractRatio",SemanticSyntax.Type.GENERIC_QUALITY,true);
    explicit.setDeclaredParent(expression);statements.add(explicit);
    var mass=declaration("InheritedMass",SemanticSyntax.Type.GENERIC_QUALITY,false);
    mass.setDeclaredParent(new CapturedSemanticExpressionParser(world).concept("imod:Mass"));statements.add(mass);
    try(var state=compile(world,"specialized")) {
      var actor=state.actor();var parent=actor.resolveConcept("ratio of imod:Y to imod:Z");
      assertNotNull(parent);assertTrue(parent.is(SemanticType.RATIO));
      assertEquals("kg/m",parent.getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY));
      for(var name:List.of("DerivedRatio","InheritedRatio","ExplicitAbstractRatio")) {
        var concept=actor.owl().getConcept("imod:"+name);assertNotNull(concept);
        assertTrue(concept.is(SemanticType.RATIO),name);assertTrue(concept.is(SemanticType.QUANTIFIABLE),name);
        assertEquals("odo:Ratio",actor.owl().getCoreOntology().getCoreType(concept.getType()).getUrn(),
            "Generic QUALITY fallback must not override the specialized type");
        assertTrue(isA(actor,concept,parent),name);
        assertTrue(isA(actor,concept,actor.owl().getConcept("odo:Ratio")),name);
        assertEquals(name.equals("ExplicitAbstractRatio"),concept.isAbstract(),name);
        System.out.println("RESULT SPECIALIZED "+name+" isRatio=true abstract="+concept.isAbstract()
            +" operatorParentUnit="+parent.getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY)
            +" ownUnit="+concept.getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY));
      }
      var inheritedMass=actor.owl().getConcept("imod:InheritedMass");
      assertTrue(inheritedMass.is(SemanticType.MASS));assertTrue(inheritedMass.is(SemanticType.EXTENSIVE));
      assertTrue(isA(actor,inheritedMass,actor.owl().getConcept("odo:Mass")));assertFalse(inheritedMass.isAbstract());
      var coreGraph=actor.owl().getOntology("odo").getOWLOntology();
      var rawMassMetadata=new org.integratedmodelling.klab.services.reasoner.owl.OWLMetadata(
          owlClass(actor,actor.owl().getConcept("odo:Mass")),coreGraph);
      assertEquals("kg",rawMassMetadata.get(CoreOntology.NS.SI_UNIT_PROPERTY),"Imported annotation must remain intact");
      assertTrue(actor.owl().getConcept("odo:Mass").isAbstract(),"A child must not relabel its canonical parent");
    }
  }
}
