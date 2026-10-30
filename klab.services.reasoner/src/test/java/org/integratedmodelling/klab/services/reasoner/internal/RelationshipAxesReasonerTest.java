package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;

class RelationshipAxesReasonerTest {
  @TempDir Path directory;
  private KimConceptStatementImpl relation(String name,RelationshipAxes.Nature nature,RelationshipAxes.Orientation orientation) {
    var s=StagedDeclarationContractTest.declaration("k",name,SemanticType.RELATIONSHIP);
    s.setAbstract(true);s.setDeclaredParent(StagedDeclarationContractTest.reference("w:Relation"));
    s.setDeclaredRelationshipNature(nature);s.setDeclaredRelationshipOrientation(orientation);
    new RelationshipAxes(nature,orientation).apply(s.getType());return s;
  }
  @Test void realWorldviewBooleanAliasAndDirectExpressionsPreserveCorrelatedAxes()throws Exception {
    var world=StagedDeclarationContractTest.world("relationship axes",false,false,false);
    var root=StagedDeclarationContractTest.declaration("w","Relation",SemanticType.RELATIONSHIP);
    root.setAbstract(true);root.setUpperConceptDefined("odo:Relationship");world.getOntologies().getFirst().getStatements().add(root);
    var left=relation("Left",RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);
    var right=relation("Right",RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var union=new KimConceptImpl();union.setUrn("k:Left or k:Right");union.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.UNION,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    var leftRef=StagedDeclarationContractTest.reference("k:Left");leftRef.setType(EnumSet.copyOf(left.getType()));
    var rightRef=StagedDeclarationContractTest.reference("k:Right");rightRef.setType(EnumSet.copyOf(right.getType()));
    union.setObservable(leftRef);union.setOperands(List.of(rightRef));union.setExpressionType(KimConcept.Expression.UNION);
    var alias=StagedDeclarationContractTest.declaration("k","Either",SemanticType.RELATIONSHIP);alias.setAbstract(true);alias.setAlias(true);alias.setDeclaredParent(union);
    world.getOntologies().get(1).getStatements().addAll(List.of(left,right,alias));
    var core=new LinkedHashMap<String,byte[]>();try(var files=Files.list(Path.of(System.getProperty("klab.test.core.directory")))) {
      for(var file:files.filter(p->p.toString().endsWith(".owl")).toList())core.put(file.getFileName().toString(),Files.readAllBytes(file));
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve("assets"),core);var input=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var built=factory.build(input,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());
    try(var state=built.state()) {
      var actor=state.actor();var either=actor.resolveConcept("k:Either");assertNotNull(either);
      assertEquals(RelationshipAxes.UNSPECIFIED,RelationshipAxes.of(either.getType()));
      assertTrue(actor.owl().permitsRelationshipAxes(either,new RelationshipAxes(RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED)));
      assertFalse(actor.owl().permitsRelationshipAxes(either,new RelationshipAxes(RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND)));
      var direct=actor.getClass().getDeclaredMethod("buildInternal",KimConceptStatement.class,org.integratedmodelling.klab.services.reasoner.owl.Ontology.class,KimConceptStatement.class,org.integratedmodelling.klab.api.scope.Scope.class);
      direct.setAccessible(true);
      var crossed=relation("NestedCrossed",RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND);crossed.setDeclaredParent(null);
      var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->direct.invoke(actor,crossed,actor.owl().getOntology("k"),alias,org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class)));
      assertInstanceOf(IllegalArgumentException.class,failure.getCause());assertTrue(failure.getCause().getMessage().contains("shared compatible"),failure.getCause().toString());
      var valid=relation("NestedValid",RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);valid.setDeclaredParent(null);
      assertNotNull(direct.invoke(actor,valid,actor.owl().getOntology("k"),alias,org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class)));
      var resolved=actor.resolveConcept("k:Left or k:Right");assertNotNull(resolved);assertEquals(RelationshipAxes.UNSPECIFIED,RelationshipAxes.of(resolved.getType()));
      assertThrows(IllegalArgumentException.class,()->actor.owl().getIntersection(List.of(actor.resolveConcept("k:Left"),actor.resolveConcept("k:Right")),actor.owl().getOntology("k"),EnumSet.of(SemanticType.RELATIONSHIP)));
    }
  }
}
