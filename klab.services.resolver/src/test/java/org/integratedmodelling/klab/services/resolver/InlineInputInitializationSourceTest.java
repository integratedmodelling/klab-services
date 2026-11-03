package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.language.SourceModelMapper;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.junit.jupiter.api.Test;

/** UNRUN: authentic authored zero must survive syntax, adapter and executable model projection. */
class InlineInputInitializationSourceTest {
  @Test void actualFixtureZeroAndImpliedSevenRetainAnExecutableConstant() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var parser=new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed=parser.parse(new StringReader("private namespace consequence.roles.process version 1.0; private model 0 as imod:Input in m; private model 7 as imod:ImpliedValue in m;"));
    assertFalse(parsed.hasSyntaxErrors());
    var errors=new ArrayList<String>();
    var types=new BasicObservableValidationScope(){
      public ConceptDescriptor getConceptDescriptor(String urn){
        if(urn.equals("imod:Input")||urn.equals("imod:ImpliedValue"))return new ConceptDescriptor("imod",urn.substring(5),SemanticSyntax.Type.LENGTH,urn,"fixture",false,false);
        return super.getConceptDescriptor(urn);
      }
    };
    var syntax=new NamespaceSyntaxImpl((org.integratedmodelling.languages.kim.Model)parsed.getRootASTElement(),types){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}
    };
    var expected=List.of(0,7);
    for(int i=0;i<2;i++) {
      var model=(ModelSyntax)syntax.getStatements().get(i);
      assertEquals(1,model.getContextualizations().size(),"Inline model literal must become actual computation before Runtime INIT");
      assertEquals(Set.of(ModelSyntax.Contextualization.Trigger.INITIALIZATION),model.getContextualizations().getFirst().getTriggers());
      assertEquals(expected.get(i).intValue(),((Number)((ParsedLiteral)model.getContextualizations().getFirst().getContextualizable()).getPod()).intValue());
    }
    var namespace=LanguageAdapter.INSTANCE.adaptNamespace(syntax,"fixture",List.of(),1L);
    assertTrue(errors.isEmpty(),errors.toString());
    var reasoner=mock(Reasoner.class);var scope=mock(ContextScope.class);when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveObservable(anyString())).thenAnswer(call->{
      var concept=new ConceptImpl();concept.setUrn(call.getArgument(0));
      concept.setType(EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY,org.integratedmodelling.klab.api.knowledge.SemanticType.LENGTH));
      var value=new ObservableImpl();value.setSemantics(concept);value.setUrn(concept.getUrn());return value;
    });
    for(int i=0;i<2;i++) {
      var statement=(KimModel)namespace.getStatements().get(i);
      assertEquals(expected.get(i).intValue(),((Number)statement.getContextualization().getFirst().getLiteral()).intValue());
      var executable=SourceModelMapper.load(statement,namespace,scope);
      assertEquals(1,executable.getComputation().size());
      assertEquals(expected.get(i).intValue(),((Number)executable.getComputation().getFirst().getLiteral()).intValue());
      // Invoke the existing compiler's literal lowering itself, not a hand-built ServiceCall.
      var compiler=new DataflowCompiler(null,null,scope);
      var lower=DataflowCompiler.class.getDeclaredMethod("adaptContextualizer",
          org.integratedmodelling.klab.api.lang.Contextualizable.class,Map.class,
          org.integratedmodelling.klab.api.knowledge.Model.class);
      lower.setAccessible(true);
      var call=(org.integratedmodelling.klab.api.lang.ServiceCall)lower.invoke(compiler,
          executable.getComputation().getFirst(),Map.of(),executable);
      assertEquals(org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),call.getUrn());
      assertEquals(expected.get(i).intValue(),((Number)call.getParameters().get("value")).intValue());
    }
  }
}
