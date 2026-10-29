package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.Method;
import java.util.Set;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.language.AuditedSemanticFootprints;
import org.integratedmodelling.klab.runtime.libraries.NativeTestFixtures;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Finite implementation contracts. Names, annotations and absence of observed calls alone
 * never establish an arbitrary plugin's semantic footprint. */
final class SemanticExecutionFootprints {
  static final String DIAGNOSTIC_KEY = "diagnostic.semanticFootprints";
  private SemanticExecutionFootprints() {}

  /** Temporary identities/statuses only; not a semantic attestation or admission input. */
  private static void trace(ServiceContextScope scope,Method method,Object receiver,
      ServiceCall call,boolean accepted,Object component) {
    try {
    var tx=scope.getCurrentTransaction(); if(tx==null)return;
    var event=SemanticExecutionEvidence.diagnostic("call",call==null?null:call.getUrn(),
        "method",method==null?null:method.toGenericString(),"receiver",receiver==null?null:receiver.getClass().getName(),
        "exactReceiver",method!=null&&exactReceiver(method,receiver),"accepted",accepted,
        "component",component);
    if(method!=null) try {
      var names=java.util.List.of(method.getDeclaringClass().getName(),"org.integratedmodelling.generators.utils.RandomShapes");
      var classes=new java.util.ArrayList<Object>();
      for(var name:names) try {
        var type=Class.forName(name,false,method.getDeclaringClass().getClassLoader());
        var resource="/"+name.replace('.','/')+".class";
        try(var input=type.getResourceAsStream(resource)) {
          var location=type.getProtectionDomain().getCodeSource().getLocation();
          var archive=java.nio.file.Path.of(location.toURI());
          classes.add(SemanticExecutionEvidence.diagnostic("class",name,"loader",String.valueOf(type.getClassLoader()),
              "location",String.valueOf(location),"archiveSha256",java.nio.file.Files.isRegularFile(archive)
                  ?java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                      .digest(java.nio.file.Files.readAllBytes(archive))):null,
              "sha256",input==null?null:java.util.HexFormat.of().formatHex(
                  java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))));
        }
      } catch(Exception unavailable) { classes.add(SemanticExecutionEvidence.diagnostic("class",name,"unavailable",unavailable.getClass().getName())); }
      event.put("classes",classes);
    } catch(Exception unavailable) { event.put("identityUnavailable",unavailable.getClass().getName()); }
    var activity=tx.getActivity();
    synchronized(activity) {
      var old=activity.getMetadata().get(DIAGNOSTIC_KEY);
      var events=new java.util.ArrayList<Object>(); if(old instanceof String text)
        events.addAll(org.integratedmodelling.klab.utilities.Utils.Json.parseObject(text,java.util.List.class));
      events.add(event); activity.getMetadata().put(DIAGNOSTIC_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(events));
    }
    } catch (RuntimeException diagnosticUnavailable) {
      // Diagnostics must not change the existing execution-footprint decision.
    }
  }

  static boolean function(ComponentRegistry registry, Method actual, Object receiver, ServiceCall call,
      ServiceContextScope scope) {
    if (actual == null || call == null || !exactReceiver(actual, receiver)) {
      trace(scope,actual,receiver,call,false,null); return false;
    }
    if (actual.getDeclaringClass() == NativeTestFixtures.class
        && Set.of("empty", "classify", "discoverAfter", "failClassifier", "failConsumer").contains(actual.getName()))
      return true; // Actual core class; its existing private testcase permit remains mandatory.
    if (actual.getDeclaringClass() == NativeTestFixtures.class && "fixed".equals(actual.getName())
        && "klab.tests.fixtures.fixed".equals(call.getUrn()) && actual.getReturnType() == void.class
        && java.lang.reflect.Modifier.isStatic(actual.getModifiers())
        && java.util.Arrays.equals(actual.getParameterTypes(), new Class<?>[] {
            org.integratedmodelling.klab.api.data.Data.Builder.class,
            org.integratedmodelling.klab.api.geometry.Geometry.class,
            org.integratedmodelling.klab.api.knowledge.Observable.class,
            org.integratedmodelling.klab.api.scope.ContextScope.class}))
      return true; // Exact testcase producer only; execution still requires its private live permit.
    if (registry == null) { trace(scope,actual,receiver,call,false,null); return false; }
    var components=registry.getInstalledComponents(scope);
    boolean accepted=components.stream().anyMatch(component ->
        component.services().containsKey(call.getUrn())
            && AuditedSemanticFootprints.forFunction(component, actual, call.getUrn()).isPresent());
    trace(scope,actual,receiver,call,accepted,components.stream()
        .filter(component -> component.services().containsKey(call.getUrn()))
        .map(component -> SemanticExecutionEvidence.diagnostic("id",component.id(),"version",component.version(),
            "archive",String.valueOf(component.sourceArchive()))).toList());
    return accepted;
  }

  static boolean exactReceiver(Method method, Object receiver) {
    return method != null && (java.lang.reflect.Modifier.isStatic(method.getModifiers())
        || receiver != null && receiver.getClass() == method.getDeclaringClass());
  }
  static boolean exactReceiver(ComponentRegistry.ServiceImplementation implementation) {
    if (implementation == null || implementation.method == null) return false;
    // A deferred/custom constructor is UNKNOWN until its actual receiver is available.
    // Never attest a declaring-class Method while dispatch will run an overriding subclass.
    return exactReceiver(implementation.method, implementation.mainClassInstance);
  }

  static boolean call(ComponentRegistry registry, CompiledDataflow.CallDescriptors selected,
      ServiceCall call, ServiceContextScope scope) {
    if (selected == null) { trace(scope,null,null,call,false,"missing selected call descriptors"); return false; }
    if (selected.serviceInfo() != null) {
      var implementation = registry.implementation(selected.serviceInfo());
      if(implementation==null || !exactReceiver(implementation)) {
        trace(scope,implementation==null?null:implementation.method,
            implementation==null?null:implementation.mainClassInstance,call,false,"unavailable or inexact implementation");
        return false;
      }
      return function(registry, implementation.method, implementation.mainClassInstance, call, scope);
    }
    var adapter = selected.embeddedAdapter();
    if (adapter == null || adapter.getEncoder() == null || selected.resource() == null) {
      trace(scope,null,null,call,false,"missing adapter encoder or resource"); return false;
    }
    var encoder = registry.implementation(adapter.getEncoder());
    if (encoder == null || encoder.method == null || !exactReceiver(encoder)) {
      trace(scope,encoder==null?null:encoder.method,encoder==null?null:encoder.mainClassInstance,
          call,false,"unavailable or inexact encoder"); return false;
    }
    var receiverClass = java.lang.reflect.Modifier.isStatic(encoder.method.getModifiers())
        ? encoder.method.getDeclaringClass() : encoder.mainClassInstance.getClass();
    boolean accepted=AuditedSemanticFootprints.forAdapter(
        registry.getComponent(adapter.getComponentUrn(), adapter.getComponentVersion()),
        receiverClass, encoder.method, selected.resource().getUrn()).isPresent();
    var component=registry.getComponent(adapter.getComponentUrn(),adapter.getComponentVersion());
    trace(scope,encoder.method,encoder.mainClassInstance,call,accepted,
        SemanticExecutionEvidence.diagnostic("id",adapter.getComponentUrn(),"version",adapter.getComponentVersion(),
            "archive",component==null?null:String.valueOf(component.sourceArchive()),"resource",selected.resource().getUrn()));
    return accepted;
  }
}
