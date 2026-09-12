package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.languages.api.ConceptDeclarationSyntax;
import org.integratedmodelling.languages.api.DefineSyntax;
import org.integratedmodelling.languages.api.FunctionCallSyntax;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.junit.jupiter.api.Test;

class ObservationAnnotationAdaptationTest {
  @Test
  void conceptDeclarationsKeepTheirAnnotations() throws Exception {
    var declaration = mock(ConceptDeclarationSyntax.class);
    when(declaration.getName()).thenReturn("Thing");
    when(declaration.getDeclaredType()).thenReturn(SemanticSyntax.Type.SUBJECT);
    var annotation = annotation();
    when(declaration.getAnnotations()).thenReturn(List.of(annotation));
    var method = LanguageAdapter.class.getDeclaredMethod("adaptConceptDefinition",
        ConceptDeclarationSyntax.class, String.class, String.class);
    method.setAccessible(true);
    var adapted = (KlabStatement) method.invoke(LanguageAdapter.INSTANCE, declaration, "test", "project");
    assertEquals("display", adapted.getAnnotations().getFirst().getName());
  }

  @Test
  void observationDefinitionsKeepTheirAnnotations() throws Exception {
    var definition = mock(DefineSyntax.class);
    when(definition.getName()).thenReturn("thing");
    when(definition.getInstanceClass()).thenReturn("observation");
    var annotation = annotation();
    when(definition.getAnnotations()).thenReturn(List.of(annotation));
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("test");
    namespace.setProjectName("project");
    var method = LanguageAdapter.class.getDeclaredMethod("adaptDefine", DefineSyntax.class, KimNamespace.class);
    method.setAccessible(true);
    var adapted = (KlabStatement) method.invoke(LanguageAdapter.INSTANCE, definition, namespace);
    assertEquals("display", adapted.getAnnotations().getFirst().getName());
  }

  private FunctionCallSyntax annotation() {
    var annotation = mock(FunctionCallSyntax.class);
    when(annotation.getName()).thenReturn("@display");
    when(annotation.encode()).thenReturn("@display");
    when(annotation.getArguments()).thenReturn(Map.of());
    return annotation;
  }
}
