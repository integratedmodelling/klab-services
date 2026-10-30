package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.*;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;

class ObserverSourceTransportTest {
  private KimConceptStatementImpl declaration(String name,SemanticType type) {
    var s=new KimConceptStatementImpl();s.setUrn(name);s.setNamespace("demo");
    s.setType(EnumSet.of(type,SemanticType.OBSERVABLE));
    if(type==SemanticType.AGENT||type==SemanticType.SUBJECT)s.getType().add(SemanticType.COUNTABLE);
    return s;
  }
  private CapturedSemanticExpressionParser parser() {
    var ontology=new KimOntologyImpl();ontology.setUrn("demo");
    ontology.setStatements(List.of(declaration("Height",SemanticType.LENGTH),
        declaration("Observer",SemanticType.AGENT),declaration("OtherObserver",SemanticType.AGENT),
        declaration("Tree",SemanticType.SUBJECT)));
    var otherOntology=new KimOntologyImpl();otherOntology.setUrn("other");
    var sameLocalName=declaration("Observer",SemanticType.AGENT);sameLocalName.setNamespace("other");otherOntology.setStatements(List.of(sameLocalName));
    var world=new WorldviewImpl();world.setUrn("demo");world.setWorldviewId("demo");world.setEmpty(false);world.setOntologies(List.of(ontology,otherOntology));
    return new CapturedSemanticExpressionParser(world);
  }
  private KimConceptImpl concept(String urn,SemanticType... types) {
    var ret=new KimConceptImpl();ret.setName(urn);ret.setUrn(urn);ret.setType(types.length==0?EnumSet.noneOf(SemanticType.class):EnumSet.copyOf(List.of(types)));return ret;
  }
  private KimConcept namedHead(KimConcept expression) {
    var visited=Collections.newSetFromMap(new IdentityHashMap<KimConcept,Boolean>());
    while(expression.getName()==null) {
      assertTrue(visited.add(expression),"Observer AST must not contain a wrapper cycle");
      assertTrue(expression.getOperands().isEmpty(),"Simple observer must not acquire logical operands");
      assertNull(expression.getSemanticModifier(),"Simple observer must not acquire a unary operator");
      assertNotNull(expression.getObservable(),"Observer wrapper must retain its semantic head");
      expression=expression.getObservable();
    }
    assertTrue(expression.is(SemanticType.AGENT));
    return expression;
  }
  @Test void parserAdapterEncodeReparseRetainsExplicitObserverAndDistinctReferenceNames() {
    var parser=parser();var observed=parser.observable("demo:Height observed as demo:Observer");
    var other=parser.observable("demo:Height observed as demo:OtherObserver");var absolute=parser.observable("demo:Height");
    for(var text:List.of("demo:Height in m observed as demo:Observer","demo:Height observed as demo:Observer in m")) {
      var mediated=parser.observable(text);assertEquals("m",mediated.getUnit());
      assertEquals("demo:Observer",mediated.getObserverSemantics().getUrn());
      var reparsed=parser.observable(mediated.getUrn());assertEquals("m",reparsed.getUnit());assertEquals(mediated.getObserverSemantics().getUrn(),reparsed.getObserverSemantics().getUrn());
    }

    assertEquals("demo:Observer",observed.getObserverSemantics().getUrn());
    assertEquals("demo:Observer",namedHead(observed.getObserverSemantics()).getName());
    assertTrue(observed.getObserverSemantics().is(SemanticType.AGENT));
    assertTrue(observed.getUrn().contains(" observed as "));assertFalse(observed.getUrn().contains(" observed by "));
    var roundtrip=parser.observable(observed.getUrn());
    assertEquals(observed.getUrn(),roundtrip.getUrn());assertEquals(observed.getObserverSemantics().getUrn(),roundtrip.getObserverSemantics().getUrn());
    assertNotEquals(observed.getUrn(),other.getUrn());assertNotEquals(observed.getReferenceName(),other.getReferenceName());assertNotEquals(observed.getReferenceName(),absolute.getReferenceName());
    assertNull(absolute.getObserverSemantics());
    var sameLocalName=parser.observable("demo:Height observed as other:Observer");
    assertNotEquals(observed.getReferenceName(),sameLocalName.getReferenceName());
    assertEquals("demo:Observer",parser.observable(observed.format(new org.integratedmodelling.klab.api.lang.kim.style.KimStyle.PlainCodeAppender())).getObserverSemantics().getUrn());
    assertThrows(IllegalArgumentException.class,()->parser.observable("demo:Height observed by demo:Observer"));
  }
  @Test void wholeWorkspaceInterfaceTransportPreservesObserverClassAndAbsoluteNull()throws Exception {
    var parser=parser();var model=new KimModelImpl();model.setUrn("example");
    model.setObservables(List.of(parser.observable("demo:Height observed as demo:Observer"),parser.observable("demo:Height")));
    var namespace=new KimNamespaceImpl();namespace.setUrn("example.observers");namespace.setStatements(List.of(model));
    var project=new ProjectImpl();project.setUrn("demo");project.setNamespaces(List.of(namespace));
    var workspace=new WorkspaceImpl();workspace.setUrn("observer-test");workspace.setProjects(List.of(project));
    var mapper=JacksonConfiguration.newObjectMapper();var payload=mapper.writeValueAsBytes(workspace);
    var copy=mapper.readValue(payload,Workspace.class);
    var copied=(KimModel)copy.getProjects().iterator().next().getNamespaces().getFirst().getStatements().getFirst();
    assertEquals("demo:Observer",copied.getObservables().getFirst().getObserverSemantics().getUrn());
    assertEquals("demo:Observer",namedHead(copied.getObservables().getFirst().getObserverSemantics()).getName());
    assertTrue(copied.getObservables().getFirst().getObserverSemantics().is(SemanticType.AGENT));
    assertNull(copied.getObservables().get(1).getObserverSemantics());
    assertEquals(model.getObservables().getFirst().getUrn(),copied.getObservables().getFirst().getUrn());
  }
  @Test void copiedAndMappedObserverTreesAreDetachedAndVisited() {
    var source=new KimObservableImpl();source.setSemantics(concept("demo:Height",SemanticType.QUALITY));
    source.setObserverSemantics(concept("demo:Observer",SemanticType.AGENT));source.setUrn("demo:Height observed as demo:Observer");
    var copy=new KimObservableImpl(source);assertNotSame(source.getObserverSemantics(),copy.getObserverSemantics());
    ((KimConceptImpl)copy.getObserverSemantics()).setName("demo:OtherObserver");assertEquals("demo:Observer",source.getObserverSemantics().getName());
    var paths=new ArrayList<List<String>>();
    var mapped=new KimObservableImpl(source,(path,node)->{paths.add(path);if(path.equals(List.of("observerSemantics")))node.setName("demo:OtherObserver");});
    assertTrue(paths.contains(List.of("observerSemantics")));assertEquals("demo:OtherObserver",mapped.getObserverSemantics().getName());assertNull(mapped.getUrn());
    var visitor=new KimObservableVisitor(new KimObservableVisitor.LenientValidator(),null);visitor.visit(source);
    assertTrue(visitor.getConcepts().contains(source.getObserverSemantics()));assertTrue(visitor.getReferences().stream().anyMatch(r->r.urn().equals("demo:Observer")));
  }
  @Test void sourceValidationRejectsKnownWrongKindsIncludingBooleanAndDerivedHeadsButDefersUnknown() {
    var validator=new ObservableValidator();var observable=new KimObservableImpl();
    observable.setSemantics(concept("demo:Height",SemanticType.QUALITY));
    observable.setObserverSemantics(concept("demo:Observer",SemanticType.AGENT));assertTrue(validator.validateObservable(observable).isEmpty());
    observable.setObserverSemantics(concept("demo:Unknown"));assertTrue(validator.validateObservable(observable).isEmpty());
    observable.setObserverSemantics(concept("demo:Unresolved",SemanticType.NOTHING));assertTrue(validator.validateObservable(observable).isEmpty());
    observable.setObserverSemantics(concept("demo:Tree",SemanticType.SUBJECT));assertTrue(validator.validateObservable(observable).getFirst().getMessage().contains("OBSERVER_AGENT_REQUIRED"));
    var alternatives=new KimConceptImpl();alternatives.setType(EnumSet.of(SemanticType.AGENT));alternatives.setExpressionType(KimConcept.Expression.UNION);
    alternatives.setObservable(concept("demo:Observer",SemanticType.AGENT));alternatives.setOperands(List.of(concept("demo:Tree",SemanticType.SUBJECT)));observable.setObserverSemantics(alternatives);
    assertFalse(validator.validateObservable(observable).isEmpty());
    var derived=new KimConceptImpl();derived.setObservable(concept("demo:Observer",SemanticType.AGENT));derived.setSemanticModifier(UnarySemanticOperator.COUNT);observable.setObserverSemantics(derived);
    assertFalse(validator.validateObservable(observable).isEmpty());
  }
  @Test void actorObservableLiteralVisitorIncludesObserverSemantics()throws Exception {
    var observable=new KimObservableImpl();observable.setSemantics(concept("demo:Height",SemanticType.QUALITY));observable.setObserverSemantics(concept("demo:Observer",SemanticType.AGENT));
    var visited=new ArrayList<KimConcept>();
    var validator=new org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor.LenientValidator() {
      @Override public List<org.integratedmodelling.klab.api.services.runtime.Notification> validateConcept(KimConcept concept,org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor.KActorsContext context) {visited.add(concept);return List.of();}
    };
    var visitor=new org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor();
    var context=new org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor.KActorsContext(new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl(),validator);
    var visit=visitor.getClass().getDeclaredMethod("visitSemanticObservable",KimObservable.class,context.getClass());visit.setAccessible(true);visit.invoke(visitor,observable,context);
    assertEquals(List.of(observable.getSemantics(),observable.getObserverSemantics()),visited);
  }
}
