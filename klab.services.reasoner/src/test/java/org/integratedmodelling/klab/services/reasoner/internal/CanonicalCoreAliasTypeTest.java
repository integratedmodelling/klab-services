package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.worldview.Ontology;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.runtime.language.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalCoreAliasTypeTest {
  @TempDir Path directory;
  private record Parsed(KimOntology ontology,WorldviewValidationScope scope) {}
  private Parsed parse(String body) {
    var parser=new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var result=parser.parse(new StringReader("ontology imod in domain root version 1.0.0; "+body));
    assertFalse(result.hasSyntaxErrors(),result.getSyntaxErrors().toString());
    var errors=new ArrayList<String>();var scope=new WorldviewValidationScope();
    var syntax=new OntologySyntaxImpl((Ontology)result.getRootASTElement(),scope) {
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object,org.eclipse.emf.ecore.EStructuralFeature feature,String message) {errors.add(message);}
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object,org.eclipse.emf.ecore.EStructuralFeature feature,String message) {}
    };
    var ontology=new LanguageAdapter().adaptOntology(syntax,"canonical-alias-test",List.of(),0L);
    assertTrue(errors.isEmpty(),errors.toString());return new Parsed(ontology,scope);
  }
  private WorldviewImpl world(KimOntology ontology) {
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));return world;
  }
  private SemanticEnvironment compile(KimOntology ontology,String name) throws Exception {
    var core=new TreeMap<String,byte[]>();
    for(var file:List.of("odo.owl","klab.owl","dc.owl")) {
      var root=System.getProperty("klab.test.core.directory");
      if(root!=null)core.put(file,Files.readAllBytes(Path.of(root,file)));
      else try(var input=getClass().getResourceAsStream("/knowledge/"+file)) {assertNotNull(input);core.put(file,input.readAllBytes());}
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve(name),core);var input=factory.capture(world(ontology));
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var result=factory.build(input,()->false);assertNotNull(result.state(),result.diagnostics().toString());
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());
    assertTrue(result.diagnostics().stream().noneMatch(m->m.contains("ambiguous")||m.contains("reassigned")),result.diagnostics().toString());
    return result.state();
  }
  private KimConceptStatement statement(KimOntology ontology,String name) {
    return ontology.getStatements().stream().filter(s->s.getUrn().equals(name)).findFirst().orElseThrow();
  }
  private void singleKind(Set<SemanticType> types,SemanticType expected) {
    var declared=EnumSet.copyOf(types);declared.retainAll(SemanticType.DECLARABLE_TYPES);
    assertEquals(EnumSet.of(expected),declared);
  }
  private static final String ALIASES="""
      abstract quantity Quantity is core odo:QuantifiableQuality;
      abstract quantity Ratio "Ratio interpretation from the worldview." is core odo:Ratio;
      abstract quantity Numerosity is core odo:Numerosity;
      abstract quantity Proportion is core odo:Proportion;
      """;
  @Test void rootAliasFormsAndNamedChildrenHaveOneSpecificKindInBothDeclarationOrders() throws Exception {
    for(boolean reverse:List.of(false,true)) {
      var aliases=new ArrayList<>(List.of(ALIASES.strip().split("\\n")));if(reverse)Collections.reverse(aliases);
      var parsed=parse(String.join("\n",aliases)+"""
          quality NamedRatio is imod:Ratio;
          quality ChildRatio is imod:NamedRatio;
          abstract quality AbstractRatio is imod:Ratio;
          abstract ordering Evidence;
          """);
      var expected=Map.of("Quantity",SemanticType.QUANTITY,"Ratio",SemanticType.RATIO,
          "Numerosity",SemanticType.NUMEROSITY,"Proportion",SemanticType.PROPORTION,
          "NamedRatio",SemanticType.RATIO,"ChildRatio",SemanticType.RATIO,"AbstractRatio",SemanticType.RATIO,
          "Evidence",SemanticType.ORDERING);
      var captured=new WorldviewValidationScope(world(parsed.ontology()));
      for(var entry:expected.entrySet()) {
        singleKind(statement(parsed.ontology(),entry.getKey()).getType(),entry.getValue());
        assertEquals(WorldviewValidationScope.getMainType(entry.getValue()),parsed.scope().getConceptDescriptor("imod:"+entry.getKey()).mainType());
        assertEquals(WorldviewValidationScope.getMainType(entry.getValue()),captured.getConceptDescriptor("imod:"+entry.getKey()).mainType());
      }
      try(var state=compile(parsed.ontology(),"order-"+reverse)) {
        var actor=state.actor();for(var entry:expected.entrySet())singleKind(actor.owl().getConcept("imod:"+entry.getKey()).getType(),entry.getValue());
        assertEquals("odo:Ratio",actor.owl().getConcept("imod:Ratio").getUrn());
        assertFalse(actor.owl().getConcept("imod:NamedRatio").isAbstract());
        assertTrue(actor.owl().getConcept("imod:AbstractRatio").isAbstract());
        assertTrue(actor.owl().getConcept("odo:Ratio").getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.DC_COMMENT,String.class).contains("Ratio interpretation"));
        System.out.println("RESULT ROOT order="+reverse+" alias/children=RATIO singleKind=true abstractness/documents=preserved");
      }
    }
  }
  @Test void capturedLegacyFlagsAreRefinedBeforeNamedChildrenRegardlessOfOrder() throws Exception {
    var parsed=parse(ALIASES+"quality NamedRatio is imod:Ratio; quality ChildRatio is imod:NamedRatio;");
    for(var name:List.of("Ratio","NamedRatio","ChildRatio")) {
      var s=(KimConceptStatementImpl)statement(parsed.ontology(),name);
      var flags=EnumSet.of(SemanticType.OBSERVABLE,SemanticType.QUALITY,SemanticType.QUANTITY,SemanticType.QUANTIFIABLE);
      if(s.isAbstract())flags.add(SemanticType.ABSTRACT);s.setType(flags);
    }
    Collections.reverse(parsed.ontology().getStatements());
    var captured=new WorldviewValidationScope(world(parsed.ontology()));
    for(var name:List.of("Ratio","NamedRatio","ChildRatio"))
      assertEquals(SemanticSyntax.Type.RATIO,captured.getConceptDescriptor("imod:"+name).mainType());
    try(var state=compile(parsed.ontology(),"legacy")) {
      for(var name:List.of("Ratio","NamedRatio","ChildRatio"))singleKind(state.actor().owl().getConcept("imod:"+name).getType(),SemanticType.RATIO);
      System.out.println("RESULT LEGACY reversed flags and named descendants normalize to RATIO");
    }
  }
  @Test void explicitRatioExpressionAndItsNamedChildStillRetainUnitsAndSpecialization() throws Exception {
    var parsed=parse(ALIASES+"""
        mass Y; length Z;
        quality ComputedRatio is ratio of imod:Y to imod:Z;
        quality ChildRatio is imod:ComputedRatio;
        """);
    statement(parsed.ontology(),"Y").getMetadata().put(CoreOntology.NS.SI_UNIT_PROPERTY,"kg");
    statement(parsed.ontology(),"Z").getMetadata().put(CoreOntology.NS.SI_UNIT_PROPERTY,"m");
    try(var state=compile(parsed.ontology(),"expression")) {
      var actor=state.actor();for(var name:List.of("ComputedRatio","ChildRatio"))singleKind(actor.owl().getConcept("imod:"+name).getType(),SemanticType.RATIO);
      var parent=actor.resolveConcept("ratio of imod:Y to imod:Z");
      assertEquals("kg/m",parent.getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY));
      System.out.println("RESULT EXPRESSION named descendants=RATIO operatorUnit=kg/m");
    }
  }
  @Test void exactTargetsNotNamesDriveRefinementAndConflictsFailWithoutPoisoningCanonicalState() throws Exception {
    var parsed=parse("abstract quantity QuotientKind is core odo:Ratio; abstract quantity Ratio is core odo:QuantifiableQuality;");
    singleKind(statement(parsed.ontology(),"QuotientKind").getType(),SemanticType.RATIO);
    singleKind(statement(parsed.ontology(),"Ratio").getType(),SemanticType.QUANTITY);
    assertEquals(SemanticSyntax.Type.QUANTITY,CoreAliasTypeSupport.syntaxType("odo:PreferenceValue",SemanticSyntax.Type.QUANTITY));
    assertEquals(SemanticSyntax.Type.QUANTITY,CoreAliasTypeSupport.syntaxType("other:Ratio",SemanticSyntax.Type.QUANTITY));
    assertEquals(SemanticSyntax.Type.RATIO,CoreAliasTypeSupport.syntaxType("http://integratedmodelling.org/odo#Ratio",SemanticSyntax.Type.QUANTITY));
    assertThrows(IllegalArgumentException.class,()->CoreAliasTypeSupport.syntaxType("odo:Ratio",SemanticSyntax.Type.SUBJECT));
    assertThrows(IllegalArgumentException.class,()->CoreAliasTypeSupport.types("odo:Ratio",EnumSet.of(SemanticType.NOTHING)));
    try(var state=compile(parsed.ontology(),"metadata")) {
      var actor=state.actor();var core=actor.owl().getConcept("odo:Ratio");core.getMetadata().put(CoreOntology.NS.SI_UNIT_PROPERTY,"m/m");
      var install=actor.getClass().getDeclaredMethod("installAlias",KimConceptStatement.class,
          org.integratedmodelling.klab.services.reasoner.owl.Ontology.class,org.integratedmodelling.klab.api.scope.Scope.class);install.setAccessible(true);
      var alias=new KimConceptStatementImpl();alias.setNamespace("imod");alias.setUrn("AnotherRatio");alias.setUpperConceptDefined("odo:Ratio");
      alias.setType(EnumSet.of(SemanticType.QUANTITY,SemanticType.QUALITY,SemanticType.OBSERVABLE));alias.setDocstring("Additional ratio interpretation.");
      install.invoke(actor,alias,actor.owl().getOntology("imod"),actor.serviceScope());
      singleKind(core.getType(),SemanticType.RATIO);assertEquals("m/m",core.getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY));
      assertEquals("m/m",actor.owl().getConcept("imod:AnotherRatio").getMetadata().get(CoreOntology.NS.SI_UNIT_PROPERTY));
      var before=Set.copyOf(core.getType());alias.setUrn("InvalidRatio");alias.setType(EnumSet.of(SemanticType.SUBJECT));
      var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
          ()->install.invoke(actor,alias,actor.owl().getOntology("imod"),actor.serviceScope()));
      assertInstanceOf(IllegalArgumentException.class,failure.getCause());assertEquals(before,core.getType());
      assertNull(actor.owl().getConcept("imod:InvalidRatio"));
    }
  }
}
