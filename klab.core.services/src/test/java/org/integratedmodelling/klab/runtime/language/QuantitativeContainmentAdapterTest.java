package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.languages.ObservableSyntaxImpl;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.observable.ObservableSequence;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class QuantitativeContainmentAdapterTest {
  @Test void realParserAndAdapterPreserveIdentitySlotsTypeAndUnitsAcrossReparsing() {
    var parser=new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var scope=new BasicObservableValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        var local=name.substring(name.indexOf(':')+1);
        return new ConceptDescriptor("test",local,local.equals("Volume")?SemanticSyntax.Type.VOLUME:SemanticSyntax.Type.IDENTITY,
            name,"",false,false,false,false);
      }
    };
    String definition="test:Oxygen within test:Air test:Volume in m3";
    for(int pass=0;pass<3;pass++) {
      var parsed=parser.parse(new StringReader(definition+";"));assertFalse(parsed.hasSyntaxErrors());
      var syntax=new ObservableSyntaxImpl(((ObservableSequence)parsed.getRootASTElement()).getObservables().get(0).getSemantics(),scope) {
        @Override protected void logWarning(ParsedObject p,org.eclipse.emf.ecore.EObject o,org.eclipse.emf.ecore.EStructuralFeature f,String m) {}
        @Override protected void logError(ParsedObject p,org.eclipse.emf.ecore.EObject o,org.eclipse.emf.ecore.EStructuralFeature f,String m) {fail(m);}
      };
      var adapted=LanguageAdapter.INSTANCE.adaptObservable(syntax,"test","test",KlabAsset.KnowledgeClass.NAMESPACE);
      assertTrue(adapted.getSemantics().is(SemanticType.VOLUME));
      assertTrue(adapted.getSemantics().is(SemanticType.EXTENSIVE));
      assertEquals("test:Oxygen",adapted.getSemantics().getPortionIdentity().getUrn());
      assertEquals("test:Air",adapted.getSemantics().getWholeIdentity().getUrn());
      assertNull(adapted.getSemantics().getInherent());assertTrue(adapted.getSemantics().getTraits().isEmpty());
      assertEquals("m3",adapted.getUnit());
      assertTrue(adapted.getCodeName().toLowerCase().contains("oxygen"));
      assertTrue(adapted.getCodeName().toLowerCase().contains("air"));
      assertTrue(adapted.getReferenceName().toLowerCase().contains("oxygen"));
      assertTrue(adapted.getReferenceName().toLowerCase().contains("air"));
      definition=adapted.getUrn();
      assertTrue(definition.contains("test:Oxygen within test:Air test:Volume"));
    }
  }
}
