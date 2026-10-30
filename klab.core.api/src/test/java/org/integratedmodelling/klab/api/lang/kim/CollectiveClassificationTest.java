package org.integratedmodelling.klab.api.lang.kim;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
class CollectiveClassificationTest {
  static KimConceptImpl concept(String name, SemanticType... types) {
    var c=new KimConceptImpl(); c.setName(name); c.setType(new HashSet<>(List.of(types))); if(c.is(SemanticType.SUBJECT))c.getType().add(SemanticType.COUNTABLE); return c;
  }
  @Test void abstractAndConcreteAnyUseOneNativeDomainWithoutProductOrOptionality() {
    for(boolean any:List.of(false,true)) {
      var region=concept("earth:Region",SemanticType.SUBJECT,SemanticType.COUNTABLE);
      var predicate=concept(any?"earth:Terrestrial":"earth:PhysicalEnvironment",SemanticType.PREDICATE,SemanticType.REALM);
      if(!any)predicate.getType().add(SemanticType.ABSTRACT);
      var occurrence=predicate.mapSyntax(n->{}); if(any)occurrence.getType().add(SemanticType.ANY);
      var head=region.mapSyntax(n->{}); head.setCollective(true); head.getTraits().add(occurrence);
      var source=new KimObservableImpl(); source.setSemantics(head); var original=source.getUrn();
      var plan=CollectiveClassification.prepare(source,name->name.equals(region.getName())?region:predicate);
      assertNotNull(plan); assertEquals(1,plan.directives().size()); assertTrue(plan.members().getSemantics().isCollective());
      assertTrue(plan.members().getSemantics().getTraits().isEmpty()); assertFalse(plan.members().isOptional());
      assertEquals(predicate.getName(),plan.directives().getFirst().getSemantics().getName());
      assertFalse(plan.directives().getFirst().getSemantics().is(SemanticType.ANY));
      assertEquals(plan.members().getSemantics().getUrn(),plan.directives().getFirst().getSemantics().getInherent().getUrn());
      assertEquals(original,source.getUrn()); assertEquals(1,source.getSemantics().getTraits().size());
    }
  }
  @Test void parserArityWrapperPreservesItsQualifierAndDoesNotMutateSource() {
    var region=concept("earth:Region",SemanticType.SUBJECT);var predicate=concept("earth:Environment",SemanticType.PREDICATE,SemanticType.REALM,SemanticType.ABSTRACT);
    var wrapper=concept(null,SemanticType.SUBJECT);wrapper.setCollective(true);wrapper.setObservable(region);wrapper.getTraits().add(predicate);
    var source=new KimObservableImpl();source.setSemantics(wrapper);
    var plan=CollectiveClassification.prepare(source,name->name.equals("earth:Region")?region:predicate);
    assertNotNull(plan);assertEquals("each earth:Region",plan.members().getUrn());
    assertEquals(1,wrapper.getTraits().size());assertSame(region,wrapper.getObservable());assertFalse(region.isCollective());
  }
  @Test void genericPatternAndRetainedQualificationCannotLoseTheirMeaning() {
    var region=concept("earth:Region",SemanticType.SUBJECT);region.setCollective(true);
    var predicate=concept("earth:Environment",SemanticType.PREDICATE,SemanticType.REALM,SemanticType.ABSTRACT);
    var direct=predicate.mapSyntax(n->{});direct.setInherent(region);
    var source=new KimObservableImpl();source.setSemantics(direct);source.setGeneric(true);
    java.util.function.Function<String,KimConcept> declarations=name->name.equals("earth:Region")?region:predicate;
    assertNull(CollectiveClassification.prepare(source,declarations));
    source.setGeneric(false);source.setPattern("unbound");assertNull(CollectiveClassification.prepare(source,declarations));
    var qualified=region.mapSyntax(n->{});qualified.getTraits().add(predicate);
    qualified.getTraits().add(concept("earth:Protected",SemanticType.PREDICATE,SemanticType.REALM));
    source=new KimObservableImpl();source.setSemantics(qualified);
    assertNull(CollectiveClassification.prepare(source,name->name.equals("earth:Protected")?concept(name,SemanticType.PREDICATE,SemanticType.REALM):declarations.apply(name)));
  }
  @Test void ordinaryConcreteQualificationDoesNotRequestClassification() {
    var region=concept("earth:Region",SemanticType.SUBJECT); region.setCollective(true);
    var trait=concept("earth:Terrestrial",SemanticType.PREDICATE,SemanticType.REALM);region.getTraits().add(trait);
    var o=new KimObservableImpl();o.setSemantics(region);
    assertNull(CollectiveClassification.prepare(o,name->name.equals("earth:Region")?concept(name,SemanticType.SUBJECT):trait));
  }
}
