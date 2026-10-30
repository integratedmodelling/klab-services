package org.integratedmodelling.klab.runtime.kactors.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.io.StringReader;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.kActors.Behavior;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.lang.kactors.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.junit.jupiter.api.Test;

class ObserverUiIntegrationTest {
  @Test
  void webApplicationKeepsObserverLiteralsInDirectAndCompoundValues() throws Exception {
    String literal = "{{ demo:Height in m observed as demo:Observer }}";
    for (String expression : List.of(literal, "(" + literal + ", " + literal + ")",
        "{sample: " + literal + "}")) {
      var behavior = parse("""
          web app tests.ui.perspective
              "Observer-aware UI integration fixture"
              version 1.0
          action main:
              sample <- %s
          """.formatted(expression));
      assertEquals(KActorsBehavior.Platform.WEB, behavior.getPlatform());
      var value = ((KActorsStatement.Assignment) behavior.getStatements().getFirst()
          .getCode().getFirst()).getValue();
      var literals = new ArrayList<KimObservable>();
      collect(value, literals);
      assertEquals(expression.startsWith("(") ? 2 : 1, literals.size());
      var mapper = JacksonConfiguration.newObjectMapper();
      for (var source : literals) {
        var copy = mapper.readValue(mapper.writerFor(KimObservable.class)
            .writeValueAsBytes(source), KimObservable.class);
        assertEquals("m", copy.getUnit());
        assertEquals("demo:Observer", copy.getObserverSemantics().getUrn());
        assertEquals("demo:Height", copy.getSemantics().getUrn());
        assertEquals(source.getNamespace(), copy.getNamespace());
        assertEquals("tests.ui.perspective", source.getNamespace());
      }
      var compiler = new AgentCompiler(behavior, null, new KActorsVisitor.LenientValidator(), null);
      assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
      assertTrue(compiler.getSourceCode().contains("observableLiteral("));
      assertTrue(compiler.getSourceCode().contains("demo:Observer"));
    }
  }

  private static KActorsBehavior parse(String source) {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("demo");
    var height = new KimConceptStatementImpl();
    height.setUrn("Height"); height.setNamespace("demo");
    height.setType(EnumSet.of(SemanticType.LENGTH, SemanticType.QUALITY, SemanticType.OBSERVABLE));
    var observer = new KimConceptStatementImpl();
    observer.setUrn("Observer"); observer.setNamespace("demo");
    observer.setType(EnumSet.of(SemanticType.AGENT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    ontology.setStatements(List.of(height, observer));
    var worldview = new WorldviewImpl();
    worldview.setOntologies(List.of(ontology));
    var scope = new WorldviewValidationScope(worldview);
    var parser = new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration()
        .getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    var errors = new ArrayList<String>();
    parsed.getSyntaxErrors().forEach(error -> errors.add(error.getSyntaxErrorMessage().getMessage()));
    assertFalse(parsed.hasSyntaxErrors(), errors.toString());
    var syntax = new BehaviorSyntaxImpl((Behavior) parsed.getRootASTElement(), scope) {
      @Override protected void logWarning(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) {}
      @Override protected void logError(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) { fail(message); }
    };
    var notifications = new ArrayList<org.integratedmodelling.klab.api.services.runtime.Notification>();
    var behavior = LanguageAdapter.INSTANCE.adaptBehavior(syntax, "test-project", notifications, 1);
    assertTrue(notifications.stream().noneMatch(n -> n.getLevel().severity >=
        org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error.severity), notifications.toString());
    return behavior;
  }

  private static void collect(Object value, List<KimObservable> result) {
    if (value instanceof KActorsValue actorValue) {
      collect(actorValue.getValue(Object.class), result);
    } else if (value instanceof KimObservable observable) {
      result.add(observable);
    } else if (value instanceof Map<?, ?> map) {
      map.values().forEach(item -> collect(item, result));
    } else if (value instanceof Iterable<?> values) {
      values.forEach(item -> collect(item, result));
    }
  }
}
