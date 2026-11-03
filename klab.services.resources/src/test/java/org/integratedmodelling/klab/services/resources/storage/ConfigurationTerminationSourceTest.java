package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.runtime.language.ConfigurationTerminationValidation;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTerminationSourceTest {
  @TempDir Path directory;
  private KimNamespace parse(String name,String observable) throws Exception {
    var ontology=WorkspaceDomainIngestionTest.ROOT+" abstract configuration Configuration is core odo:Configuration; "
        +"configuration Pattern; configuration Alias equals imod:Pattern; thing Region;";
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(name)),Map.of("imod",ontology));
    manager.getOntologies(false);
    WorkspaceDomainIngestionTest.set(manager,"namespaceParser",new WorkspaceManager.KimParser());
    var read=WorkspaceManager.class.getDeclaredMethod("readNamespace",String.class,long.class,String.class,String.class);read.setAccessible(true);
    return (KimNamespace)read.invoke(manager,"namespace models.validation version 1.0; model "+observable+" on termination set to [0];",1L,"fixture","models.validation");
  }
  private static ReasonerService reasoner() {
    var reasoner=mock(ReasonerService.class);when(reasoner.owl()).thenReturn(mock(OWL.class));
    java.util.function.Function<String,Concept> meaning=urn->{
      var concept=new ConceptImpl();concept.setUrn(urn);concept.setType(EnumSet.of(
          urn.contains("Pattern")||urn.contains("Alias")?SemanticType.CONFIGURATION:SemanticType.QUALITY));return concept;
    };
    when(reasoner.resolveConcept(anyString())).thenAnswer(i->meaning.apply(i.getArgument(0)));
    when(reasoner.declareConcept(any())).thenAnswer(i->{KimConcept c=i.getArgument(0);return meaning.apply(c.getUrn());});
    when(reasoner.declareObservable(any())).thenAnswer(i->{
      KimObservable source=i.getArgument(0);var observable=new ObservableImpl();observable.setUrn(source.getUrn());
      observable.setSemantics(meaning.apply(source.getUrn()));return observable;
    });
    when(reasoner.satisfiable(any())).thenReturn(true);when(reasoner.traits(any())).thenReturn(List.of());when(reasoner.roles(any())).thenReturn(List.of());
    return reasoner;
  }
  @Test void parserAdapterAndServerRejectNativeAndAliasConfigurationTerminationWithSourceSpans() throws Exception {
    for(var name:List.of("Pattern","Alias")) {
      var namespace=parse(name,"imod:"+name);
      assertTrue(namespace.getNotifications().stream().anyMatch(n->n.getMessage().startsWith("CONFIGURATION_TERMINATION_UNSUPPORTED")),namespace.getNotifications().toString());
      var model=(KimModel)namespace.getStatements().getFirst();var action=model.getContextualization().getFirst();
      assertTrue(ConfigurationTerminationValidation.termination(action));assertNull(action.getTrigger());
      assertTrue(action.getOffsetInDocument()>0);assertTrue(action.getLength()>0);
      // Prove server-side effective meaning is authoritative even when caller flags claim SUBJECT.
      ((KimConceptImpl)model.getObservables().getFirst().getSemantics()).setType(EnumSet.of(SemanticType.SUBJECT));
      var diagnostics=new DocumentSemanticValidator(reasoner()).validate(namespace);
      var rejected=diagnostics.stream().filter(n->n.getMessage().startsWith("CONFIGURATION_TERMINATION_UNSUPPORTED")).findFirst().orElseThrow();
      assertEquals(action.getOffsetInDocument(),rejected.getLexicalContext().getOffsetInDocument());
      assertEquals(action.getLength(),rejected.getLexicalContext().getLength());
    }
  }
  @Test void existingNonConfigurationTerminationHookIsRetainedWithoutActivatingExecution() throws Exception {
    var namespace=parse("quality","presence of imod:Region");
    assertTrue(namespace.getNotifications().stream().noneMatch(n->n.getMessage().startsWith("CONFIGURATION_TERMINATION_UNSUPPORTED")));
    var action=((KimModel)namespace.getStatements().getFirst()).getContextualization().getFirst();
    assertTrue(ConfigurationTerminationValidation.termination(action));assertNull(action.getTrigger());
    assertTrue(new DocumentSemanticValidator(reasoner()).validate(namespace).stream()
        .noneMatch(n->n.getMessage().startsWith("CONFIGURATION_TERMINATION_UNSUPPORTED")));
  }
}
