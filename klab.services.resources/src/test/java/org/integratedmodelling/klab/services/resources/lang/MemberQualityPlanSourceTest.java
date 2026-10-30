package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.util.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.MemberQualityPlan;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.junit.jupiter.api.Test;

class MemberQualityPlanSourceTest {
  static final String SOURCE="""
      strategies bridge version 1.0;
      strategy 1 named gradients.native
        for test:Gradient
        resolve each test:Stream to streams
        for each stream in streams {
          resolve test:Gradient within $stream to value
          yield value
        } into gradients
        yield gradients;
      """;
  KimObservationStrategy parse() {
    var parser=new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed=parser.parse(new StringReader(SOURCE));var errors=new ArrayList<String>();
    parsed.getSyntaxErrors().forEach(e->errors.add(e.getSyntaxErrorMessage().getMessage()));assertTrue(errors.isEmpty(),errors.toString());
    var root=parsed.getRootASTElement();new ResourceImpl(URI.createURI("memory:/member.obs")).getContents().add(root);
    var scope=new LanguageValidationScope() {
      public ConceptDescriptor getConceptDescriptor(String urn) {var parts=urn.split(":",2);return new ConceptDescriptor(parts[0],parts[1],
          parts[1].equals("Gradient")?SemanticSyntax.Type.RATIO:SemanticSyntax.Type.SUBJECT,urn,"fixture",false,false);}
      public void notifyCoreConcept(String urn,SemanticSyntax.Type type) {}
      public LanguageValidationScope contextualize(EObject context) {return this;}
    };
    var syntax=(ObservationSyntax.StrategyDocument)new ObservationSyntaxAdapter() {
      protected void logWarning(ParsedObject target,EObject object,EStructuralFeature feature,String message) {}
      protected void logError(ParsedObject target,EObject object,EStructuralFeature feature,String message) {fail(message);}
    }.adapt((org.integratedmodelling.languages.observation.ObservationDocument)root,scope);
    return LanguageAdapter.INSTANCE.adaptStrategies(syntax,"fixture.project",List.of(),0).getStatements().getFirst();
  }
  ContextScope scope() {
    var scope=mock(ContextScope.class);var reasoner=mock(Reasoner.class);var caps=mock(Reasoner.Capabilities.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(reasoner.capabilities(scope)).thenReturn(caps);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(7L);
    var context=new ObservationImpl();context.setId(10);context.setUrn("region:10");context.setGeometry(Geometry.create("t0(1)"));context.setEventTimestamps(List.of(0L));
    var region=new ConceptImpl();region.setUrn("test:Region");region.setType(EnumSet.of(SemanticType.SUBJECT));
    var observed=new ObservableImpl();observed.setUrn(region.getUrn());observed.setSemantics(region);context.setObservable(observed);
    when(scope.getContextObservation()).thenReturn(context);
    when(reasoner.resolveObservable(anyString())).thenAnswer(call->{
      String urn=call.getArgument(0);var concept=new ConceptImpl();concept.setUrn(urn);
      concept.setType(EnumSet.of(urn.contains("Gradient")?SemanticType.QUALITY:SemanticType.SUBJECT));concept.setCollective(urn.startsWith("each "));
      var value=new ObservableImpl();value.setUrn(urn);value.setSemantics(concept);return value;
    });return scope;
  }
  @Test void existingMemberGrammarAndAdapterLowerIntoTypedRequest() throws Exception {
    var strategy=parse();var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsBytes(strategy),KimObservationStrategy.class);
    var request=MemberQualityPlan.lower(transported,scope());
    assertEquals("each test:Stream",request.members());assertEquals("test:Gradient",request.quality());
    assertEquals(10,request.container().contextId());assertEquals(7,request.container().knowledgeRevision());
  }
  @Test void wrongMemberContextAndTierZeroAreRejected() {
    var source=parse();var block=(MemberPlanImpl)source.getPlan().getSteps().get(1);
    var producer=(GraphProducerImpl)block.getBody().getSteps().getFirst();
    var wrong=new StrategyVariableImpl();wrong.setSpelling("$context");producer.setContext(wrong);
    assertThrows(IllegalArgumentException.class,()->MemberQualityPlan.lower(source,scope()));
    var zero=parse();((org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategyImpl)zero).setRank(0);
    assertThrows(IllegalArgumentException.class,()->MemberQualityPlan.lower(zero,scope()));
  }
}
