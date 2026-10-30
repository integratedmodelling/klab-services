package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.Method;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityCollection;
import org.integratedmodelling.klab.components.ComponentRegistry;

/** Inspects the installed method, never trusting artifact-type metadata or importing a component. */
final class MemberQualityConsumerSupport {
  static org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection
      inspect(ComponentRegistry registry,ServiceCall call) {
    var unavailable=org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection.unavailable();
    if(registry==null || call==null)return unavailable;
    try {
      var candidates=registry.getFunctionDescriptor(call);
      if(candidates==null || candidates.isEmpty())return unavailable;
      var ports=new java.util.HashSet<String>();
      for(var descriptor:candidates) {
        var implementation=registry.implementation(descriptor);
        if(implementation==null || implementation.method==null)return unavailable;
        boolean isStatic=java.lang.reflect.Modifier.isStatic(implementation.method.getModifiers());
        if(descriptor.staticMethod!=isStatic || !isStatic && implementation.mainClassInstance==null)return unavailable;
        for(var parameter:implementation.method.getParameters()) {
          if(parameter.getType()!=MemberQualityCollection.class)continue;
          var input=parameter.getAnnotation(KlabFunction.Input.class);
          if(input==null || input.name().isBlank() || !ports.add(input.name()))return unavailable;
        }
      }
      // Ordinary overload selection belongs to the existing executor. Typed overloads
      // require a unique method, rather than assuming one member of an overload set wins.
      if(!ports.isEmpty() && candidates.size()!=1)return unavailable;
      return org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection.known(ports);
    } catch(RuntimeException failedInspection) {return unavailable;}
  }
  static java.util.Set<String> requiredPorts(ComponentRegistry registry,ServiceCall call) {
    if(registry==null || call==null)return java.util.Set.of();
    var candidates=registry.getFunctionDescriptor(call);
    if(candidates==null)return java.util.Set.of();
    var ports=new java.util.HashSet<String>();boolean typed=false;
    for(var descriptor:candidates) {
      var implementation=registry.implementation(descriptor);
      if(implementation==null || implementation.method==null)continue;
      for(var parameter:implementation.method.getParameters()) {
        if(parameter.getType()!=MemberQualityCollection.class)continue;
        typed=true;var input=parameter.getAnnotation(KlabFunction.Input.class);
        if(input==null || input.name().isBlank() || !ports.add(input.name()))
          throw new IllegalStateException("Typed collection parameter lacks a unique explicit input name");
      }
    }
    if(typed && candidates.size()!=1)
      throw new IllegalStateException("Ambiguous installed typed collection consumer");
    return java.util.Set.copyOf(ports);
  }
  static boolean supports(ComponentRegistry registry,ServiceCall call,String port) {
    if(registry==null || call==null || port==null || port.isBlank())return false;
    var candidates=registry.getFunctionDescriptor(call);
    if(candidates==null || candidates.size()!=1)return false;
    var implementation=registry.implementation(candidates.getFirst());
    if(implementation==null || !supports(implementation.method,port))return false;
    boolean isStatic=java.lang.reflect.Modifier.isStatic(implementation.method.getModifiers());
    return candidates.getFirst().staticMethod==isStatic && (isStatic || implementation.mainClassInstance!=null);
  }
  static boolean supports(Method method,String port) {
    if(method==null || port==null || port.isBlank())return false;
    int found=0;
    for(var parameter:method.getParameters()) {
      var input=parameter.getAnnotation(KlabFunction.Input.class);
      if(input!=null && port.equals(input.name())) {
        if(parameter.getType()!=MemberQualityCollection.class)return false;
        found++;
      }
    }
    return found==1;
  }
  private MemberQualityConsumerSupport() {}
}
