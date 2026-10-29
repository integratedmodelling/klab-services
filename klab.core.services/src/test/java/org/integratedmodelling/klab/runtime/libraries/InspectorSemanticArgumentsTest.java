package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry;
import org.junit.jupiter.api.Test;

class InspectorSemanticArgumentsTest {
  private static final String FAMILY = "test:Environment";

  private static KimConceptImpl meaning() {
    var concept = new KimConceptImpl();
    concept.setName(FAMILY); concept.setUrn(FAMILY);
    concept.setType(EnumSet.of(SemanticType.REALM, SemanticType.PREDICATE, SemanticType.ABSTRACT));
    return concept;
  }

  private static KimObservableImpl literal(String name) {
    var value = new KimObservableImpl();
    value.setSemantics(meaning()); value.setFormalName(name); value.setNamespace("test.model");
    value.setUrn(FAMILY + " named " + name); value.setUnit("m"); value.setOptional(true);
    return value;
  }

  private static ObservationInspector.MemberSnapshot before() {
    return new ObservationInspector.MemberSnapshot("test-context", 1, 2, Map.of(), Set.of());
  }

  @Test void allSupportedRepresentationsReachTheExistingConceptAssertion() {
    var context = mock(ContextScope.class); var before = before();
    var concept = mock(Concept.class); when(concept.getUrn()).thenReturn(FAMILY);
    var observable = mock(Observable.class); when(observable.getSemantics()).thenReturn(concept);
    when(observable.getUrn()).thenThrow(new AssertionError("Do not read the Observable wrapper URN"));
    try (var assertions = mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheck(context,before,FAMILY)).thenReturn(true);
      for (var value : List.of(concept,observable,meaning(),literal("first"),FAMILY))
        assertTrue(CoreActorLibrary.Inspector.classificationcheck(null,context,before,value));
      assertions.verify(() -> ObservationInspector.classificationCheck(context,before,FAMILY),times(5));
    }
  }

  @Test void concreteRequestedAndFamilyArgumentsBothNormalizeSemanticLiteralsAndLegacyStrings() {
    var context = mock(ContextScope.class); var snapshot = before();
    var requested = mock(Concept.class); when(requested.getUrn()).thenReturn("test:Terrestrial");
    var wrapper = mock(Observable.class); when(wrapper.getSemantics()).thenReturn(requested);
    when(wrapper.getUrn()).thenThrow(new AssertionError("Observable wrapper URN is not semantic identity"));
    try (var assertions = mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheckFor(context, snapshot, "test:Terrestrial", FAMILY)).thenReturn(true);
      for (var concrete : List.of(requested, wrapper, "test:Terrestrial"))
        for (var family : List.of(meaning(), literal("local_alias"), FAMILY))
          assertTrue(CoreActorLibrary.Inspector.classificationcheckfor(null, context, snapshot, concrete, family));
      assertions.verify(() -> ObservationInspector.classificationCheckFor(context, snapshot, "test:Terrestrial", FAMILY), times(9));
    }
  }

  @Test void localAliasesAndProductionFieldsNeverChangeConceptMeaning() {
    var context=mock(ContextScope.class);var before=before();
    var first=literal("first_local_name");var second=literal("second_local_name");
    second.setUnit("km");second.setOptional(false);
    try(var assertions=mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheck(context,before,FAMILY)).thenReturn(true);
      assertTrue(CoreActorLibrary.Inspector.classificationcheck(null,context,before,first));
      assertTrue(CoreActorLibrary.Inspector.classificationcheck(null,context,before,second));
      assertions.verify(() -> ObservationInspector.classificationCheck(context,before,FAMILY),times(2));
    }
  }

  @Test void allConceptVerbsUseTheSameSemanticBoundaryAndKeepLegacyStrings() {
    var context=mock(ContextScope.class);var before=before();var producer=mock(Observation.class);
    try(var assertions=mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheck(context,before,FAMILY)).thenReturn(true);
      assertions.when(() -> ObservationInspector.classificationNoop(context,before,FAMILY)).thenReturn(true);
      assertions.when(() -> ObservationInspector.relationshipsCheck(context,producer,FAMILY)).thenReturn(true);
      assertions.when(() -> ObservationInspector.selectionCheck(context,producer,FAMILY)).thenReturn(true);
      for(var expected:List.of(literal("local_name"),FAMILY)) {
        assertTrue(CoreActorLibrary.Inspector.classificationcheck(null,context,before,expected));
        assertTrue(CoreActorLibrary.Inspector.classificationnoop(null,context,before,expected));
        assertTrue(CoreActorLibrary.Inspector.relationshipscheck(null,context,producer,expected));
        assertTrue(CoreActorLibrary.Inspector.selectioncheck(null,context,producer,expected));
      }
    }
  }

  @Test void absentSemanticsAndArbitraryObjectsFailWithoutStringificationOrGraphAccess() {
    var arbitrary = new Object() {
      @Override public String toString() { throw new AssertionError("Do not stringify inspector semantics"); }
    };
    var absent = new KimObservableImpl(); absent.setUrn(FAMILY);
    var blank = new KimConceptImpl(); blank.setUrn(" ");
    for(var value:Arrays.asList(null,"", "  ",123,arbitrary,absent,blank))
      assertThrows(IllegalArgumentException.class,()->CoreActorLibrary.Inspector.semanticConceptDefinition(value));
  }

  @Test void oneObjectEntryPointPerVerbAvoidsReflectiveOverloadSelection() {
    for(var name:List.of("classificationcheck","classificationnoop","relationshipscheck","selectioncheck")) {
      var methods=Arrays.stream(CoreActorLibrary.Inspector.class.getDeclaredMethods())
          .filter(m->m.getName().equals(name)).toList();
      assertEquals(1,methods.size());
      assertEquals(Object.class,methods.getFirst().getParameterTypes()[3]);
      assertEquals(name,methods.getFirst().getAnnotation(Verb.class).name());
    }
  }

  @Test void actualReflectiveActorLookupPreservesLiteralAndLegacyArguments() throws Exception {
    var context=mock(ContextScope.class);var before=before();var caller=mock(AgentScope.class);
    var lookup=RuntimeAgentBase.class.getDeclaredMethod("findActorMethod",Class.class,
        String.class,boolean.class,AgentScope.class,Object[].class);
    lookup.setAccessible(true);
    try(var assertions=mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheck(context,before,FAMILY)).thenReturn(true);
      for(var expected:List.of(literal("reflective_local"),FAMILY)) {
        var method=(Method)lookup.invoke(mock(TestCaseBase.class),CoreActorLibrary.Inspector.class,
            "classificationcheck",true,caller,new Object[]{context,before,expected});
        assertEquals(Object.class,method.getParameterTypes()[3]);
        assertEquals(true,method.invoke(null,caller,context,before,expected));
      }
    }
  }

  @Test void freshlyCompiledActorsAcceptLegacyStringsAndTypedSemanticLiterals() throws Exception {
    var context=mock(ContextScope.class);var before=before();
    var descriptor=new Extensions.ActorDescriptor();descriptor.urn="core.inspector";
    var function=new Extensions.FunctionDescriptor();function.staticMethod=true;
    var info=new ServiceInfoImpl();info.setName("core.inspector.classificationcheck");function.serviceInfo=info;
    descriptor.verbs.add(function);
    var implementation=new ComponentRegistry.ServiceImplementation();
    implementation.implementation=CoreActorLibrary.Inspector.class;
    implementation.method=CoreActorLibrary.Inspector.class.getMethod("classificationcheck",
        RuntimeAgent.Scope.class,Object.class,ObservationInspector.MemberSnapshot.class,Object.class);
    var resolved=new AgentCompiler.ResolvedActor(descriptor,Map.of("classificationcheck",implementation));
    var resolver=new AgentCompiler.Resolver() {
      @Override public AgentCompiler.ResolvedActor resolveActor(String urn,UserScope scope) {
        return "core.inspector".equals(urn)?resolved:null;
      }
    };
    var environment=AgentCompiler.runtimeEnvironment(resolver,null);
    try(var assertions=mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheck(context,before,FAMILY)).thenReturn(true);
      for(boolean semantic:new boolean[]{false,true}) {
        var expected=new KActorsValueImpl();expected.setType(semantic?ValueType.OBSERVABLE:ValueType.STRING);
        expected.setStatedValue(semantic?literal("compiled_local"):FAMILY);
        var behavior=behavior(expected);
        var request=new AgentImpl();request.setBehaviorUrn(behavior.getUrn());request.setName("inspector argument");
        var handle=AgentRegistry.INSTANCE.getOrCreateAgent(request,behavior,null,environment.validator(),resolver);
        assertTrue(handle.isViable(),()->handle.getNotifications().toString());
        try {
          var runtime=(RuntimeAgentBase)AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
          assertEquals(true,runtime.invokeCheckpointAction("main",context,before));
        } finally { assertTrue(handle.stop()); }
      }
      assertions.verify(() -> ObservationInspector.classificationCheck(context,before,FAMILY),times(2));
    }
  }

  @Test void compiledConcreteAuditVerbAcceptsTwoSemanticLiteralsAndLegacyStrings() throws Exception {
    var context=mock(ContextScope.class);var snapshot=before();
    var descriptor=new Extensions.ActorDescriptor();descriptor.urn="core.inspector";
    var function=new Extensions.FunctionDescriptor();function.staticMethod=true;
    var info=new ServiceInfoImpl();info.setName("core.inspector.classificationcheckfor");function.serviceInfo=info;
    descriptor.verbs.add(function);
    var implementation=new ComponentRegistry.ServiceImplementation();implementation.implementation=CoreActorLibrary.Inspector.class;
    implementation.method=CoreActorLibrary.Inspector.class.getMethod("classificationcheckfor",
        RuntimeAgent.Scope.class,Object.class,ObservationInspector.MemberSnapshot.class,Object.class,Object.class);
    var resolved=new AgentCompiler.ResolvedActor(descriptor,Map.of("classificationcheckfor",implementation));
    var resolver=new AgentCompiler.Resolver() {
      @Override public AgentCompiler.ResolvedActor resolveActor(String urn,UserScope scope) {
        return "core.inspector".equals(urn)?resolved:null;
      }
    };
    var environment=AgentCompiler.runtimeEnvironment(resolver,null);
    try(var assertions=mockStatic(ObservationInspector.class)) {
      assertions.when(() -> ObservationInspector.classificationCheckFor(context,snapshot,"test:Terrestrial",FAMILY)).thenReturn(true);
      for(boolean typed:new boolean[]{false,true}) {
        var requestedMeaning=new KimConceptImpl();requestedMeaning.setName("test:Terrestrial");requestedMeaning.setUrn("test:Terrestrial");
        requestedMeaning.setType(EnumSet.of(SemanticType.REALM,SemanticType.PREDICATE));
        var requestedLiteral=new KimObservableImpl();requestedLiteral.setSemantics(requestedMeaning);requestedLiteral.setFormalName("model_local");
        var requested=new KActorsValueImpl();requested.setType(typed?ValueType.OBSERVABLE:ValueType.STRING);
        requested.setStatedValue(typed?requestedLiteral:"test:Terrestrial");
        var family=new KActorsValueImpl();family.setType(typed?ValueType.OBSERVABLE:ValueType.STRING);
        family.setStatedValue(typed?literal("family_local"):FAMILY);
        var behavior=behavior("classificationcheckfor",List.of(requested,family));
        var request=new AgentImpl();request.setBehaviorUrn(behavior.getUrn());request.setName("concrete audit argument");
        var handle=AgentRegistry.INSTANCE.getOrCreateAgent(request,behavior,null,environment.validator(),resolver);
        assertTrue(handle.isViable(),()->handle.getNotifications().toString());
        try {
          var runtime=(RuntimeAgentBase)AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
          assertEquals(true,runtime.invokeCheckpointAction("main",context,snapshot));
        } finally { assertTrue(handle.stop()); }
      }
      assertions.verify(() -> ObservationInspector.classificationCheckFor(context,snapshot,"test:Terrestrial",FAMILY),times(2));
    }
  }

  private static KActorsBehaviorImpl behavior(KActorsValueImpl expected) {
    return behavior("classificationcheck",List.of(expected));
  }

  @Test void compiledPartialSupportExpressionChecksStableIdsAndGeometry() throws Exception {
    var value=new KActorsValueImpl();value.setType(ValueType.EXPRESSION);
    value.setStatedValue("after.members().keySet().containsAll(before.members().keySet()) && before.members().entrySet().stream().allMatch(entry -> after.members().get(entry.getKey()).geometry().equals(entry.getValue().geometry()))");
    var returned=new KActorsStatementImpl.ReturnImpl();returned.setValue(value);
    var action=new KActorsActionImpl();action.setUrn("main");action.setCode(List.of(returned));
    action.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("before"),new KActorsActionImpl.ArgumentImpl("after")));
    var behavior=new KActorsBehaviorImpl();behavior.setUrn("test.inspector.partial."+UUID.randomUUID().toString().replace("-",""));
    behavior.setDescription("Partial discovery preserves identities");behavior.setBehaviorType(KActorsBehavior.Type.SCRIPT);behavior.setStatements(List.of(action));
    var resolver=new AgentCompiler.Resolver() {
      @Override public AgentCompiler.ResolvedActor resolveActor(String urn,UserScope scope) { return null; }
    };
    var environment=AgentCompiler.runtimeEnvironment(resolver,null);
    var request=new AgentImpl();request.setBehaviorUrn(behavior.getUrn());request.setName("partial-support identity expression");
    var handle=AgentRegistry.INSTANCE.getOrCreateAgent(request,behavior,null,environment.validator(),resolver);
    assertTrue(handle.isViable(),()->handle.getNotifications().toString());
    var state=new ObservationInspector.MemberState("member:20","test:Region","test:Region","S2",List.of());
    var before=new ObservationInspector.MemberSnapshot("context",1,2,Map.of(20L,state),Set.of());
    var expanded=new ObservationInspector.MemberSnapshot("context",1,2,Map.of(20L,state,21L,state),Set.of());
    var replaced=new ObservationInspector.MemberSnapshot("context",1,2,Map.of(22L,state,23L,state),Set.of());
    var changedGeometry=new ObservationInspector.MemberState("member:20","test:Region","test:Region","S3",List.of());
    var moved=new ObservationInspector.MemberSnapshot("context",1,2,Map.of(20L,changedGeometry,21L,state),Set.of());
    try {
      var runtime=(RuntimeAgentBase)AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
      assertEquals(true,runtime.invokeCheckpointAction("main",before,expanded));
      assertEquals(false,runtime.invokeCheckpointAction("main",before,replaced));
      assertEquals(false,runtime.invokeCheckpointAction("main",before,moved));
    } finally { assertTrue(handle.stop()); }
  }

  private static KActorsBehaviorImpl behavior(String verb,List<KActorsValueImpl> semantics) {
    var call=new KActorsStatementImpl.VerbImpl();call.setRecipient("inspector");call.setMessage(verb);
    call.setArguments(new KActorsArgumentsImpl());
    for(var name:List.of("context","before")) {
      var value=new KActorsValueImpl();value.setType(ValueType.IDENTIFIER);value.setStatedValue(name);
      call.getArguments().putUnnamed(value);
    }
    semantics.forEach(call.getArguments()::putUnnamed);
    var returned=new KActorsStatementImpl.ReturnImpl();returned.setFunction(call);
    var main=new KActorsActionImpl();main.setUrn("main");main.setCode(List.of(returned));
    main.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("context"),new KActorsActionImpl.ArgumentImpl("before")));
    var imported=new KActorsBehaviorImpl.ImportImpl();imported.setImportedBehavior("core.inspector");imported.setImportedAlias("inspector");
    var behavior=new KActorsBehaviorImpl();behavior.setUrn("test.inspector.semantic."+UUID.randomUUID().toString().replace("-",""));
    behavior.setDescription("Inspector semantic argument compatibility");behavior.setBehaviorType(KActorsBehavior.Type.SCRIPT);
    behavior.setStatements(List.of(main));behavior.setImports(List.of(imported));
    return behavior;
  }
}
