package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.util.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.KimStandaloneSetup;
import org.integratedmodelling.languages.NamespaceSyntaxImpl;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.KimNamespaceVisitor;
import org.integratedmodelling.klab.runtime.scale.space.GridAlignmentSupport;
import org.junit.jupiter.api.Test;

class GridDefinitionLanguageTest {
  @Test void parsedSuppressionKeepsAnnotationsAndSuppressesOnlyLexicallyBoundWarnings() {
    ServiceConfiguration.injectInstantiators();
    var parser=new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    for(var annotation:List.of("@suppress","@suppress(\"warnings\")","@suppress(warnings = true)","@suppress(\"errors\")")) {
      var parsed=parser.parse(new StringReader("private namespace test.grid version 1.0;\n"+annotation+"\ndefine grid example as {anchor: \"POINT (1 2)\" span: 50.m};"));
      assertFalse(parsed.hasSyntaxErrors(),annotation);
      var root=(Model)parsed.getRootASTElement();new ResourceImpl(URI.createURI("memory:/suppression.kim")).getContents().add(root);
      var validation=mock(LanguageValidationScope.class);when(validation.contextualize(any())).thenReturn(validation);
      var namespace=LanguageAdapter.INSTANCE.adaptNamespace(new NamespaceSyntaxImpl(root,validation) {
        @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
            org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature,String message) {}
        @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
            org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature,String message) { throw new AssertionError(message); }
      },"project",List.of(),1L);
      var definition=(KimSymbolDefinition)namespace.getStatements().getFirst();
      assertEquals("suppress",definition.getAnnotations().getFirst().getName());
      var grid=GridAlignmentSupport.decode(definition); assertFalse(grid.warnings().isEmpty());
      var visitor=new KimNamespaceVisitor();visitor.visit(namespace);
      var genericVisitor=new KimNamespaceVisitor(new KimNamespaceVisitor.LenientValidator() {
        @Override public List<Notification> validateSymbol(KimSymbolDefinition symbol, org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context context) {
          var lexical=Notification.LexicalContext.of(symbol,context.getDocument());
          return List.of(Notification.warning("generic warning",lexical),Notification.error("generic error",lexical));
        }
      },null);
      genericVisitor.visit(namespace);
      assertEquals(annotation.contains("errors") ? 2 : 1,genericVisitor.getNotifications().size());
      assertTrue(genericVisitor.getNotifications().stream().anyMatch(n->n.getLevel()==Notification.Level.Error));
      if(annotation.contains("errors")) {
        assertFalse(visitor.getNotifications().isEmpty()); assertNotNull(visitor.getNotifications().getFirst().getLexicalContext());
      } else { assertTrue(visitor.getNotifications().isEmpty(),visitor.getNotifications().toString()); assertTrue(grid.emittedWarnings().isEmpty()); }
    }
  }
  @Test void standardRetrieveDecodesNamedGridAssetsAndReturnsNullForMissingDefinitions() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var provider=mock(org.integratedmodelling.klab.services.resources.ResourcesProvider.class,CALLS_REAL_METHODS);
    doReturn("resources-test").when(provider).serviceId();
    var workspace=mock(org.integratedmodelling.klab.services.resources.storage.WorkspaceManager.class);
    var field=org.integratedmodelling.klab.services.resources.ResourcesProvider.class.getDeclaredField("workspaceManager");
    field.setAccessible(true); field.set(provider,workspace);
    var definition=new org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl();
    definition.setUrn("test.grid.example"); definition.setDefineClass("grid");
    definition.setValue(Map.of("anchor","POINT (0 0)","span",60));
    when(workspace.retrieve("test.grid.example",KimSymbolDefinition.class)).thenReturn(definition);
    var asset=provider.retrieve("test.grid.example",org.integratedmodelling.klab.api.digitaltwin.GridAlignment.class,null);
    assertEquals(definition.getUrn(),asset.getUrn()); assertEquals("resources-test",asset.getServiceId());
    assertTrue(asset.codeDefinition().contains("define grid example"));
    assertNull(provider.retrieve("missing",org.integratedmodelling.klab.api.digitaltwin.GridAlignment.class,null));
    verify(workspace).retrieve("test.grid.example",KimSymbolDefinition.class);
    verify(workspace).retrieve("missing",KimSymbolDefinition.class);
  }
  @Test void plainWktDecimalQuantityAndSnapSurviveParsingAdaptationAndValidation() {
    ServiceConfiguration.injectInstantiators();
    IParser parser=new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed=parser.parse(new StringReader("""
        private namespace test.grid version 1.0;
        define grid example as {
          projection: "EPSG:4326"
          anchor: "POINT (21.910616123751442 38.79364335500986)"
          span: 50.5.m
          strict: false
          snap: false
          resolutions: (25.25.m 50.5.m 101.m)
        };
        """));
    var errors=new ArrayList<String>();parsed.getSyntaxErrors().forEach(n->errors.add(n.getSyntaxErrorMessage().getMessage()));
    assertTrue(errors.isEmpty(),errors.toString());
    var root=(Model)parsed.getRootASTElement();new ResourceImpl(URI.createURI("memory:/grid.kim")).getContents().add(root);
    var validation=mock(LanguageValidationScope.class);when(validation.contextualize(any())).thenReturn(validation);
    var namespace=LanguageAdapter.INSTANCE.adaptNamespace(new NamespaceSyntaxImpl(root,validation) {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature,String message) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature,String message) { throw new AssertionError(message); }
    },"project",List.of(),1L);
    var definition=assertInstanceOf(KimSymbolDefinition.class,namespace.getStatements().getFirst());
    var alignment=GridAlignmentSupport.decode(definition);
    assertFalse(alignment.snap());assertFalse(alignment.strict());assertEquals(21.910616123751442,alignment.anchorX());
    var visitor=new KimNamespaceVisitor();visitor.visit(namespace);
    assertTrue(visitor.getNotifications().stream().noneMatch(n->n.getLevel()==Notification.Level.Error),visitor.getNotifications().toString());
    assertTrue(visitor.getNotifications().stream().anyMatch(n->n.getLevel()==Notification.Level.Warning && n.getMessage().contains("anchor point")));
    assertNotNull(visitor.getNotifications().getFirst().getLexicalContext());
  }
}
