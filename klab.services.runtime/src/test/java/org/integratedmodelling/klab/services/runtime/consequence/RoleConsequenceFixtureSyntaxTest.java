package org.integratedmodelling.klab.services.runtime.consequence;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.eclipse.emf.ecore.*;import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.runtime.kactors.compiler.BehaviorAnalyzer;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.junit.jupiter.api.Test;
/** Exact isolated acceptance sources: grammar/adapters, not a claim of semantic or live admission. */
class RoleConsequenceFixtureSyntaxTest {
  private String source(String name)throws Exception {try(var input=getClass().getResourceAsStream("/consequences/roles/"+name)){assertNotNull(input,name);return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
  private EObject parse(IParser parser,String name)throws Exception {
    var result=parser.parse(new StringReader(source(name)));var errors=new ArrayList<String>();
    result.getSyntaxErrors().forEach(n->errors.add(n.getStartLine()+": "+n.getSyntaxErrorMessage().getMessage()));assertTrue(errors.isEmpty(),errors.toString());return result.getRootASTElement();
  }
  private BasicObservableValidationScope scope() {
    var types=Map.ofEntries(Map.entry("imod:Earth",SemanticSyntax.Type.DOMAIN),Map.entry("earth:Region",SemanticSyntax.Type.SUBJECT),
      Map.entry("earth:Terrestrial",SemanticSyntax.Type.REALM),Map.entry("geography:Elevation",SemanticSyntax.Type.LENGTH),
      Map.entry("agency:Patient",SemanticSyntax.Type.ROLE),Map.entry("earth:Erosion",SemanticSyntax.Type.PROCESS),
      Map.entry("rolefixture:Bearer",SemanticSyntax.Type.SUBJECT),Map.entry("rolefixture:Input",SemanticSyntax.Type.LENGTH),
      Map.entry("rolefixture:ImpliedValue",SemanticSyntax.Type.LENGTH),Map.entry("rolefixture:Marked",SemanticSyntax.Type.ROLE),Map.entry("rolefixture:Birth",SemanticSyntax.Type.PROCESS));
    return new BasicObservableValidationScope(){public ConceptDescriptor getConceptDescriptor(String urn){var type=types.get(urn);if(type==null)return super.getConceptDescriptor(urn);var parts=urn.split(":",2);return new ConceptDescriptor(parts[0],parts[1],type,urn,"fixture-type-descriptor",false,false);}};
  }
  @Test void allDerivedGrammarImportsAreCoherentWithTheObservableGrammar() {
    assertNotNull(new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class));
    for(var type:List.of(org.integratedmodelling.languages.services.ObservableGrammarAccess.class,
        WorldviewStandaloneSetup.class,KimStandaloneSetup.class,KActorsStandaloneSetup.class,ObservationStandaloneSetup.class))
      System.out.println("ROLE_FIXTURE_LANGUAGE_ORIGIN "+type.getName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
  }
  @Test void exactAuthoredConferralAndImplicationKeepTheirTargetsAndBearerCondition()throws Exception {
    var errors=new ArrayList<String>();var tree=parse(new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class),"src/rolefixture.kwv");
    var syntax=new OntologySyntaxImpl((org.integratedmodelling.languages.worldview.Ontology)tree,scope()){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}};
    var ontology=LanguageAdapter.INSTANCE.adaptOntology(syntax,"rolefixture",List.of(),1L);assertTrue(errors.isEmpty(),errors.toString());
    var birth=ontology.getStatements().stream().filter(s->s.getUrn().equals("Birth")).findFirst().orElseThrow();var conferral=birth.getConsequenceRules().getFirst();
    assertEquals(KimConsequenceRule.Kind.CONFERS,conferral.kind());assertEquals("rolefixture:Marked",conferral.target().getUrn());assertEquals("rolefixture:Bearer",conferral.within().getUrn());assertEquals(KimConsequenceRule.RecipientKind.DEFAULT,conferral.recipient().kind());
    var role=ontology.getStatements().stream().filter(s->s.getUrn().equals("Marked")).findFirst().orElseThrow();var implication=role.getConsequenceRules().getFirst();
    assertEquals(KimConsequenceRule.Kind.IMPLIES,implication.kind());assertEquals("rolefixture:ImpliedValue",implication.target().getUrn());assertNull(implication.secondaryInherency());
  }
  @Test void exactProcessModelsAndActorTestcaseParseAdaptAndRetainSharedDefinitions()throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();var errors=new ArrayList<String>();
    var model=parse(new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class),"src/consequence/roles/process.kim");
    var syntax=new NamespaceSyntaxImpl((org.integratedmodelling.languages.kim.Model)model,scope()){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}};
    var namespace=LanguageAdapter.INSTANCE.adaptNamespace(syntax,"rolefixture",List.of(),1L);assertEquals(4,namespace.getStatements().size());
    var actor=parse(new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class),"testcases/consequence/roles/testsuite.kactors");
    var behaviorSyntax=new BehaviorSyntaxImpl((org.integratedmodelling.languages.kActors.Behavior)actor,scope()){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}};
    var behavior=LanguageAdapter.INSTANCE.adaptBehavior(behaviorSyntax,"rolefixture",List.of(),1L);assertTrue(errors.isEmpty(),errors.toString());assertEquals(2,behavior.getStatements().size());
    var analyzer=new BehaviorAnalyzer(behavior,new KActorsVisitor.LenientValidator());assertTrue(analyzer.analyze(),analyzer.getNotifications().toString());assertTrue(analyzer.getFields().stream().anyMatch(f->f.name().equals("bearer_definition")));
  }
}
