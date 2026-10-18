package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.IRI;

/** Production adapter and private compiler regressions; no live services or deployment state. */
class CoreAlignmentRegressionTest {
  @TempDir Path directory;
  @SuppressWarnings("unchecked")
  private Set<SemanticType> adapted(SemanticSyntax.Type type) throws Exception {
    var method=LanguageAdapter.class.getDeclaredMethod("adaptSemanticType",SemanticSyntax.Type.class);
    method.setAccessible(true);
    return EnumSet.copyOf((Set<SemanticType>)method.invoke(LanguageAdapter.INSTANCE,type));
  }
  private KimConceptStatementImpl declaration(String name,SemanticSyntax.Type type,boolean abstr) throws Exception {
    var value=new KimConceptStatementImpl();value.setUrn(name);value.setNamespace("imod");
    value.setType(adapted(type));value.setOffsetInDocument(1);value.setAbstract(abstr);
    value.setGenericQuality(type==SemanticSyntax.Type.GENERIC_QUALITY);
    if(abstr)value.getType().add(SemanticType.ABSTRACT);
    return value;
  }
  private KimConceptStatementImpl alias(String name,SemanticSyntax.Type type,String target) throws Exception {
    var value=declaration(name,type,true);value.setUpperConceptDefined(target);return value;
  }
  private KimConceptImpl reference(String name,Set<SemanticType> types) {
    var value=new KimConceptImpl();value.setName(name);value.setUrn(name);value.setType(EnumSet.copyOf(types));
    value.setOffsetInDocument(1);return value;
  }
  private SemanticEnvironment build(String label,List<KimConceptStatementImpl> declarations) throws Exception {
    var ontology=new KimOntologyImpl();ontology.setUrn("imod");ontology.setProjectName("alignment-probe");
    ontology.setSourceCode(" ".repeat(1024)+label);ontology.getStatements().addAll(declarations);
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl")) {
      var coreDirectory=System.getProperty("klab.test.core.directory");
      if(coreDirectory!=null) core.put(name,Files.readAllBytes(Path.of(coreDirectory,name)));
      else try(var input=getClass().getResourceAsStream("/knowledge/"+name)) {
        assertNotNull(input,"Missing bundled core "+name);core.put(name,input.readAllBytes());
      }
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve(label),core);
    var input=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var result=factory.build(input,()->false);
    System.out.println("PROBE "+label+" compilerStatus="+result.logicalStatus()+" diagnostics="+result.diagnostics());
    assertNotNull(result.state(),"Compiler did not supply an inspectable isolated actor: "+result.diagnostics());
    return result.state();
  }
  private Set<String> assertedParents(ReasonerService actor,Concept concept) {
    var parents=new TreeSet<String>();
    actor.owl().getOntology(concept.getNamespace()).getOWLOntology()
      .subClassAxiomsForSubClass(owlClass(actor,concept)).forEach(a->{
        if(!a.getSuperClass().isAnonymous())parents.add(a.getSuperClass().asOWLClass().getIRI().toString());
      });
    return parents;
  }
  private org.semanticweb.owlapi.model.OWLClass owlClass(ReasonerService actor,Concept concept) {
    return actor.owl().getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(c->c.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }
  private boolean isA(ReasonerService actor,Concept concept,String coreName) {
    var manager=actor.owl().getOntology(concept.getNamespace()).getOWLOntology().getOWLOntologyManager();
    var f=manager.getOWLDataFactory();
    return actor.owl().isEntailed(f.getOWLSubClassOfAxiom(owlClass(actor,concept),
        f.getOWLClass(IRI.create("http://integratedmodelling.org/odo#"+coreName))));
  }
  @Test void standaloneMassComparedWithExplicitImodMass() throws Exception {
    var alias=alias("Mass",SemanticSyntax.Type.MASS,"odo:Mass");
    var standalone=declaration("StandaloneMass",SemanticSyntax.Type.MASS,false);
    var explicit=declaration("ExplicitMass",SemanticSyntax.Type.MASS,false);
    explicit.setDeclaredParent(reference("imod:Mass",adapted(SemanticSyntax.Type.MASS)));
    try(var state=build("mass",List.of(alias,standalone,explicit))) {
      var actor=state.actor();var a=actor.owl().getConcept("imod:StandaloneMass");var b=actor.owl().getConcept("imod:ExplicitMass");
      assertNotNull(a);assertNotNull(b);
      System.out.println("RESULT MASS adapted="+adapted(SemanticSyntax.Type.MASS)+" fallback="+actor.owl().getCoreOntology().getCoreType(adapted(SemanticSyntax.Type.MASS)).getUrn());
      System.out.println("RESULT MASS standalone parents="+assertedParents(actor,a)+" isMass="+isA(actor,a,"Mass")+" isLength="+isA(actor,a,"Length"));
      System.out.println("RESULT MASS explicit parents="+assertedParents(actor,b)+" isMass="+isA(actor,b,"Mass")+" isLength="+isA(actor,b,"Length"));
      assertFalse(isA(actor,a,"Length"));assertTrue(isA(actor,a,"Mass"));
      assertTrue(isA(actor,b,"Mass"));assertFalse(isA(actor,b,"Length"));
      assertEquals("odo:Mass",actor.owl().getConcept("imod:Mass").getUrn());
    }
  }
  @Test void realmAdapterAndActualAncestry() throws Exception {
    try(var state=build("realm",List.of(alias("Realm",SemanticSyntax.Type.REALM,"odo:Realm"),
        declaration("PhysicalEnvironment",SemanticSyntax.Type.REALM,true)))) {
      var actor=state.actor();var value=actor.owl().getConcept("imod:PhysicalEnvironment");assertNotNull(value);
      System.out.println("RESULT REALM adapted="+adapted(SemanticSyntax.Type.REALM)+" parents="+assertedParents(actor,value)
          +" isRealm="+isA(actor,value,"Realm")+" isAttribute="+isA(actor,value,"Attribute")
          +" traitProperty="+CoreOntology.getBaseTraitProperty(value));
      assertFalse(value.is(SemanticType.ATTRIBUTE));assertTrue(value.is(SemanticType.REALM));
      assertFalse(isA(actor,value,"Attribute"));assertTrue(isA(actor,value,"Realm"));
      assertEquals("odo:hasRealm",CoreOntology.getBaseTraitProperty(value));
    }
  }
  @Test void missingOperatorSuperclassesFailBeforeWritingAxioms() throws Exception {
    var length=declaration("MeasuredLength",SemanticSyntax.Type.LENGTH,false);
    var body=declaration("Body",SemanticSyntax.Type.SUBJECT,false);
    try(var state=build("operators",List.of(length,body))) {
      var actor=state.actor();var q=actor.owl().getConcept("imod:MeasuredLength");var b=actor.owl().getConcept("imod:Body");
      assertNotNull(q);assertNotNull(b);
      var graph=actor.owl().getOntology("imod").getOWLOntology();
      var original=Set.copyOf(graph.getAxioms());
      var operations=new LinkedHashMap<String,java.util.function.Supplier<Concept>>();
      // ChangeRate now has the separately approved foundation and dedicated derivative tests.
      operations.put("Magnitude",()->actor.owl().makeMagnitude(q));
      operations.put("Level",()->actor.owl().makeLevel(q));operations.put("Occurrence",()->actor.owl().makeOccurrence(b));
      for(var entry:operations.entrySet()) {
        assertNull(actor.owl().getConcept("odo:"+entry.getKey()));
        var failure=assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,entry.getValue()::get);
        assertTrue(failure.getMessage().contains("odo:"+entry.getKey()),failure.getMessage());
        assertEquals(original,graph.getAxioms(),"Failed operator must not leave a partial result class");
        System.out.println("RESULT OPERATOR "+entry.getKey()+" rejection="+failure.getMessage()+" axiomsUnchanged=true");
      }
    }
  }
}
