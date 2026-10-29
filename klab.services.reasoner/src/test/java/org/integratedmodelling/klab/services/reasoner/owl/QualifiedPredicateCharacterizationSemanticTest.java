package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real captured OWL/parser reproduction of the two preserved multi-family failure expressions. */
class QualifiedPredicateCharacterizationSemanticTest {
  @TempDir java.nio.file.Path directory;

  private KimConceptStatementImpl declaration(String namespace, String name, SemanticType kind) {
    var value=new KimConceptStatementImpl();value.setUrn(name);value.setNamespace(namespace);
    value.setOffsetInDocument(1);
    value.setType(EnumSet.of(kind,kind==SemanticType.SUBJECT?SemanticType.OBSERVABLE:SemanticType.PREDICATE));
    if(kind==SemanticType.SUBJECT)value.getType().add(SemanticType.COUNTABLE);
    value.setUpperConceptDefined("odo:"+switch(kind){case SUBJECT->"Subject";case ROLE->"Role";case ATTRIBUTE->"Attribute";case IDENTITY->"Identity";case REALM->"Realm";default->throw new IllegalArgumentException();});
    return value;
  }
  private KimOntologyImpl ontology(String namespace, Map<String,SemanticType> declarations) {
    var ontology=new KimOntologyImpl();ontology.setUrn(namespace);ontology.setProjectName("characterization-audit");
    ontology.setSourceCode(" ".repeat(4096));
    declarations.forEach((name,kind)->{
      var leaf=declaration(namespace,name,kind);leaf.setUpperConceptDefined(null);
      var parent=new KimConceptImpl();parent.setName("audit:"+kind.name());parent.setUrn("audit:"+kind.name());
      parent.setType(EnumSet.of(kind,kind==SemanticType.SUBJECT?SemanticType.OBSERVABLE:SemanticType.PREDICATE));
      if(kind==SemanticType.SUBJECT)parent.getType().add(SemanticType.COUNTABLE);
      leaf.setDeclaredParent(parent);ontology.getStatements().add(leaf);
    });
    return ontology;
  }
  @Test void compoundConcretePredicatesKeepAnIndividualExplicitCharacterizationBearer() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var world=new WorldviewImpl();world.setUrn("audit");world.setWorldviewId("audit");world.setEmpty(false);
    var roots=new KimOntologyImpl();roots.setUrn("audit");roots.setProjectName("characterization-audit");roots.setSourceCode(" ".repeat(4096));
    for(var kind:List.of(SemanticType.SUBJECT,SemanticType.ROLE,SemanticType.ATTRIBUTE,SemanticType.IDENTITY,SemanticType.REALM)) roots.getStatements().add(declaration("audit",kind.name(),kind));
    world.setOntologies(new ArrayList<>(List.of(roots,
        ontology("earth",Map.of("Region",SemanticType.SUBJECT,"Location",SemanticType.SUBJECT,"Terrestrial",SemanticType.REALM)),
        ontology("agency",Map.of("Patient",SemanticType.ROLE,"Volitional",SemanticType.ROLE,"Appropriate",SemanticType.ATTRIBUTE)),
        ontology("imod",Map.of("Reference",SemanticType.ROLE,"Areal",SemanticType.IDENTITY)))));
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl"))try(var input=getClass().getResourceAsStream("/knowledge/"+name)){
      assertNotNull(input);core.put(name,input.readAllBytes());
    }
    var factory=new SemanticEnvironmentFactory(directory,core);
    var built=factory.build(factory.capture(world),()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());
    try(var state=built.state()) {
      var reasoner=state.actor();
      for(var atomic:List.of("earth:Region","earth:Location","earth:Terrestrial","agency:Patient","agency:Volitional","agency:Appropriate","imod:Reference","imod:Areal"))
        assertFalse(reasoner.resolveConcept(atomic).is(SemanticType.NOTHING),"Atomic fixture declaration: "+atomic);
      var raw="imod:Reference of earth:Terrestrial (agency:Patient of earth:Terrestrial earth:Region) earth:Region";
      var legacy=reasoner.resolveObservable(raw);
      assertEquals(Contextualization.ACKNOWLEDGEMENT,legacy.getContextualization(),
          "Preserve the existing grammar interpretation of ambiguous handwritten text");
      assertNull(reasoner.directInherent(legacy));
      var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
      var predicates=new ArrayList<Concept>();predicates.add(reasoner.resolveConcept("imod:Reference"));predicates.add(reasoner.resolveConcept("agency:Volitional"));
      var qualified=reasoner.resolveConcept("imod:Reference of earth:Region");
      System.out.println("CHARACTERIZATION-QUALIFIED predicate="+qualified.getUrn()+" type="+qualified.getType());
      checks.add(()->assertFalse(qualified.is(SemanticType.NOTHING)));
      checks.add(()->assertTrue(qualified.is(SemanticType.PREDICATE)&&reasoner.is(qualified,reasoner.resolveConcept("imod:Reference"))));
      if(!qualified.is(SemanticType.NOTHING)&&qualified.is(SemanticType.PREDICATE))predicates.add(qualified);
      for(var memberText:List.of("earth:Region","earth:Terrestrial earth:Region",
          "earth:Terrestrial (agency:Patient of earth:Terrestrial earth:Region) earth:Region",
          "(agency:Appropriate of imod:Areal earth:Location) earth:Terrestrial earth:Region",
          "each earth:Terrestrial (agency:Patient of earth:Terrestrial earth:Region) earth:Region",
          "each (agency:Appropriate of imod:Areal earth:Location) earth:Terrestrial earth:Region",
          "earth:Terrestrial ((agency:Patient of earth:Terrestrial earth:Region)) earth:Region",
          "((agency:Appropriate of imod:Areal earth:Location)) earth:Terrestrial earth:Region")) {
        var member=reasoner.resolveConcept(memberText);assertFalse(member.is(SemanticType.NOTHING));
        var expectedKind=member.isCollective()?Contextualization.CLASSIFICATION:Contextualization.CHARACTERIZATION;
        var groupedText="imod:Reference of ("+member.getUrn()+")";
        var syntax=reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class).declareObservable(groupedText);
        var explicitlyGrouped=reasoner.resolveObservable(groupedText);
        System.out.println("CHARACTERIZATION-GROUPED source="+groupedText+" source-syntax="+syntax.getUrn()+" resolved="+explicitlyGrouped.getUrn()+" kind="+explicitlyGrouped.getContextualization());
        checks.add(()->assertEquals(expectedKind,explicitlyGrouped.getContextualization(),groupedText));
        checks.add(()->assertEquals(member,reasoner.directInherent(explicitlyGrouped),groupedText));
        // The real lifecycle promotes a returned predicate (possibly already qualified),
        // then installs the prior member's complete singular semantics as its bearer.
        for(var predicate:predicates) {
          var generated=org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder.create(
              org.integratedmodelling.klab.api.knowledge.Observable.promote(predicate),reasoner,reasoner.serviceScope())
              .of(member).buildObservable();
          var reparsed=reasoner.resolveObservable(generated.getUrn());
          var inherent=reasoner.directInherent(reparsed);
          System.out.println("CHARACTERIZATION-AUDIT predicate="+predicate.getUrn()+" member="+memberText+" generated="+generated.getUrn()+" reparsed="+reparsed.getUrn()+" inherent="+(inherent==null?null:inherent.getUrn()));
          checks.add(()->assertEquals(expectedKind,reparsed.getContextualization(),"Generated lifecycle intent: "+generated.getUrn()));
          checks.add(()->assertEquals(member,inherent,"Generated lifecycle bearer: "+generated.getUrn()));
          checks.add(()->assertTrue(inherent!=null&&inherent.isCollective()==member.isCollective()));
          var canonical=reparsed.getUrn();
          for(int round=0;round<3;round++) {
            var next=reasoner.resolveObservable(canonical);
            var prior=canonical;checks.add(()->assertEquals(prior,next.getUrn(),"Canonical round-trip stability"));
            checks.add(()->assertEquals(member,reasoner.directInherent(next),"Repeated round-trip bearer"));
            canonical=next.getUrn();
          }
        }
      }
      assertAll(checks);
    }
  }
}