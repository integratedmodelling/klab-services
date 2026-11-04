package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.junit.jupiter.api.Test;

class ConsequenceRuleTransportTest {
  private KimOntology adapt(String declarations) {
    var parser=new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var source="""
        ontology audit in domain root version 1.0;
        role Role; role OtherRole; role ThirdRole;
        quantity Quality; quantity OtherQuality; quantity ThirdQuality;
        thing Bearer; thing Recipient; thing Native; thing Context;
        configuration BasePattern;
        """+declarations;
    var parsed=parser.parse(new StringReader(source));
    var errors=new ArrayList<String>();parsed.getSyntaxErrors().forEach(e->errors.add(e.getSyntaxErrorMessage().getMessage()));
    assertTrue(errors.isEmpty(),errors.toString());
    var scope=new BasicObservableValidationScope(){
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        var parts=name.split(":");
        var type=name.contains("Role")?SemanticSyntax.Type.ROLE:
            name.contains("Quality")?SemanticSyntax.Type.GENERIC_QUALITY:
            name.contains("Pattern")?SemanticSyntax.Type.CONFIGURATION:SemanticSyntax.Type.SUBJECT;
        return new ConceptDescriptor(parts[0],parts[1],type,"","",false,false);
      }
    };
    var bean=new OntologySyntaxImpl((org.integratedmodelling.languages.worldview.Ontology)parsed.getRootASTElement(),scope){
      @Override protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m) {}
      @Override protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m) {}
    };
    var result=LanguageAdapter.INSTANCE.adaptOntology(bean,"audit",List.of(),0L);
    for(var statement:result.getStatements())for(var rule:statement.getConsequenceRules()) {
      var span=rule.source();
      assertEquals(source.substring(span.offset(),span.offset()+span.length()),span.source());
    }
    return result;
  }
  private KimConceptStatement statement(KimOntology ontology,String name) {
    return ontology.getStatements().stream().filter(s->name.equals(s.getUrn())).findFirst().orElseThrow();
  }
  /** Semantic type flags are sets; JSON array order is not part of their wire contract. */
  private com.fasterxml.jackson.databind.JsonNode semanticJson(Object value) {
    var tree=JacksonConfiguration.newObjectMapper().valueToTree(value);
    normalizeTypes(tree);return tree;
  }
  private void normalizeTypes(com.fasterxml.jackson.databind.JsonNode node) {
    if(node.isObject()) {
      node.fields().forEachRemaining(field->{
        if("type".equals(field.getKey())&&field.getValue().isArray()) {
          var values=new ArrayList<String>();field.getValue().forEach(v->values.add(v.asText()));
          values.sort(String::compareTo);var flags=(com.fasterxml.jackson.databind.node.ArrayNode)field.getValue();
          flags.removeAll();values.forEach(flags::add);
        } else normalizeTypes(field.getValue());
      });
    } else if(node.isArray())node.forEach(this::normalizeTypes);
  }

  @Test void conferralRecipientsAndNestedRulesSurviveServiceAndJsonTransport() throws Exception {
    var ontology=adapt("""
        process Default confers audit:Role;
        relationship Connection confers audit:Role to source, audit:OtherRole to target,
            audit:ThirdRole to audit:Bearer within audit:Context;
        process Parent has children (Child confers audit:Role);
        """);
    var rules=statement(ontology,"Connection").getConsequenceRules();
    assertEquals(List.of(KimConsequenceRule.RecipientKind.SOURCE,KimConsequenceRule.RecipientKind.TARGET,
        KimConsequenceRule.RecipientKind.EXPRESSION),rules.stream().map(r->r.recipient().kind()).toList());
    assertEquals("audit:Bearer",rules.get(2).recipient().expression().getUrn());
    assertEquals("audit:Context",rules.get(2).within().getUrn());
    assertEquals(KimConsequenceRule.RecipientKind.DEFAULT,statement(ontology,"Default").getConsequenceRules().get(0).recipient().kind());
    assertEquals(1,statement(ontology,"Parent").getChildren().get(0).getConsequenceRules().size());
    var mapper=JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsString(ontology),KimOntology.class);
    for(var name:List.of("Default","Connection"))
      assertEquals(semanticJson(statement(ontology,name).getConsequenceRules()),
          semanticJson(statement(copy,name).getConsequenceRules()));
    assertEquals(semanticJson(statement(ontology,"Parent").getChildren().get(0).getConsequenceRules()),
        semanticJson(statement(copy,"Parent").getChildren().get(0).getConsequenceRules()));
    assertTrue(rules.get(0).target().is(org.integratedmodelling.klab.api.knowledge.SemanticType.ROLE));
  }

  @Test void implicationPreservesNativeBearerSecondaryInherencyAndSpecializationSeparately() {
    var ontology=adapt("role Trigger implies audit:Quality of audit:Native for audit:Recipient within audit:Context;");
    var declaration=statement(ontology,"Trigger");
    var rule=declaration.getConsequenceRules().get(0);
    assertEquals(KimConsequenceRule.Kind.IMPLIES,rule.kind());
    assertEquals("audit:Native",rule.target().getInherent().getUrn());
    assertEquals("audit:Recipient",rule.secondaryInherency().getUrn());
    assertEquals("audit:Context",rule.within().getUrn());
    assertTrue(declaration.getImpliedObservables().isEmpty(),"Legacy readers must not execute scoped implications unconditionally");
  }

  @Test void emergencePreservesBooleanIngredientsAndWithinWithoutExecutingOrFlattening() throws Exception {
    var ontology=adapt("configuration Pattern emerges from (audit:Quality and (audit:OtherQuality or audit:ThirdQuality)) within audit:Context;");
    var declaration=statement(ontology,"Pattern");var rule=declaration.getConsequenceRules().get(0);
    assertEquals(KimConsequenceRule.Kind.EMERGES_FROM,rule.kind());
    assertEquals("audit:Context",rule.within().getUrn());
    assertNull(rule.secondaryInherency());
    assertTrue(declaration.getEmergenceTriggers().isEmpty(),"Scoped emergence requires typed condition evaluation");
    var mapper=JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsString(declaration),KimConceptStatement.class);
    assertEquals(semanticJson(rule.target()),semanticJson(copy.getConsequenceRules().get(0).target()));
    assertTrue(rule.target().getUrn().contains("and"));assertTrue(rule.target().getUrn().contains("or"));
  }

  @Test void configurationOfShorthandRetainsAnAuthoredEmergenceRule() {
    var ontology=adapt("""
        configuration Derived
          is  audit:BasePattern /* retain the native ingredient source */ of
             audit:Quality
          implies audit:Role for audit:Bearer;
        """);
    var declaration=statement(ontology,"Derived");var rule=declaration.getConsequenceRules().get(0);
    assertEquals(KimConsequenceRule.Kind.EMERGES_FROM,rule.kind());
    assertEquals("audit:Quality",rule.target().getUrn());
    assertEquals("isClause",rule.source().kind());
    assertTrue(rule.source().source().contains("/* retain the native ingredient source */"));
    assertEquals(KimConsequenceRule.Kind.IMPLIES,declaration.getConsequenceRules().get(1).kind());
    assertTrue(rule.source().offset()<declaration.getConsequenceRules().get(1).source().offset());
    assertNull(declaration.getDeclaredParent().getInherent(),"Configuration of is emergence, not native inherency");
  }
}
