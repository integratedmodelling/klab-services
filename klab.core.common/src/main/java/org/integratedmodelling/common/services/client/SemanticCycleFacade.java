package org.integratedmodelling.common.services.client;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;

/** A separate uncached facade: no current-client concept/distance cache can leak into a cycle. */
public final class SemanticCycleFacade {
  private SemanticCycleFacade() {}
  private static final Set<String> METADATA=Set.of("serviceId","getUrl","status","settings","serviceScope",
      "getLocalName","getName","getType","getServiceType","isLocal","getServiceScope","getServiceName","getIdentity","getVersion");
  public static Reasoner create(Reasoner base,SemanticCycleRequest cycle,Scope scope,
      java.util.function.BiFunction<SemanticCycleInvocation,Scope,SemanticCycleResult> transport) {
    Objects.requireNonNull(base);Objects.requireNonNull(cycle.binding());Objects.requireNonNull(scope);
    return (Reasoner)Proxy.newProxyInstance(Reasoner.class.getClassLoader(),new Class<?>[]{Reasoner.class},
        (proxy,method,args)->{
      if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
        case "equals" -> proxy==args[0];case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "Cycle-bound Reasoner "+cycle.binding().reasonerServiceId()+"/"+cycle.cycleId();
        default -> throw new IllegalStateException();
      };
      if(METADATA.contains(method.getName())) {
        try {
          var result=method.invoke(base,args);
          return result instanceof Scope nested?pinnedScope(nested,(Reasoner)proxy):result;
        }catch(InvocationTargetException e){throw e.getCause();}
      }
      if(!SemanticCycleCodec.allowed(method))throw new UnsupportedOperationException(
          "Operation cannot escape a retained semantic cycle: "+method.getName());
      var values=args==null?new Object[0]:args;
      var types=method.getGenericParameterTypes();var encoded=new ArrayList<String>();
      Scope requestScope=scope;
      for(int i=0;i<values.length;i++) {
        if(Scope.class.isAssignableFrom(method.getParameterTypes()[i])) {
          if(values[i] instanceof Scope supplied) {
            if(scope.getIdentity()!=null && supplied.getIdentity()!=null
                && !Objects.equals(scope.getIdentity().getId(),supplied.getIdentity().getId()))
              throw new SecurityException("Semantic cycle scope belongs to another identity");
            if(supplied instanceof ContextScope context) {
              if(!Objects.equals(context.getId(),cycle.contextId()))throw new SecurityException("Semantic cycle context mismatch");
              var envelope=context.getResolutionCycleBinding();
              if(envelope!=null && (!Objects.equals(envelope.cycleId(),cycle.cycleId())
                  ||!Objects.equals(envelope.semanticBinding(),cycle.binding())))
                throw new SecurityException("Semantic cycle scope carries a different binding");
              requestScope=supplied;
            }
            // A UserScope argument changes neither the authenticated context nor the pin.
          }
          encoded.add("null");
        } else encoded.add(SemanticCycleCodec.encode(values[i],types[i]));
      }
      var invocation=new SemanticCycleInvocation(cycle,method.getName(),
          Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),encoded);
      var response=Objects.requireNonNull(transport.apply(invocation,requestScope),"Missing pinned semantic response");
      if(response.arguments().size()!=values.length)throw new IllegalStateException("Pinned semantic response arity mismatch");
      // match's third argument is an output, not just a serialized input.
      if(method.getName().equals("match") && values.length==3 && values[2] instanceof Map original) {
        var updated=(Map)SemanticCycleCodec.decode(response.arguments().get(2),types[2]);
        original.clear();original.putAll(updated);
      }
      return SemanticCycleCodec.decode(response.result(),method.getGenericReturnType());
    });
  }
  /** Metadata may expose service/nested scopes, but never an unbound semantic service. */
  private static Scope pinnedScope(Scope original,Reasoner pinned) {
    var interfaces=new LinkedHashSet<Class<?>>();
    for(Class<?> type=original.getClass();type!=null;type=type.getSuperclass())
      for(var implemented:type.getInterfaces())
        if(Scope.class.isAssignableFrom(implemented)&&Modifier.isPublic(implemented.getModifiers()))interfaces.add(implemented);
    interfaces.add(Scope.class);
    return (Scope)Proxy.newProxyInstance(Scope.class.getClassLoader(),interfaces.toArray(Class<?>[]::new),
        (proxy,method,args)->{
          if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
            case "equals" -> proxy==args[0];case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> original.toString();default -> throw new IllegalStateException();
          };
          if(args!=null&&args.length>0) {
            if(args[0] instanceof Class<?> requested && Reasoner.class.isAssignableFrom(requested)) {
              if(requested!=Reasoner.class)throw new UnsupportedOperationException("Concrete/admin Reasoner lookup cannot escape a semantic cycle");
              if(method.getName().equals("getService"))return pinned;
              if(method.getName().equals("getServices"))return List.of(pinned);
              if(method.getName().equals("findService"))return ((java.util.function.Predicate)args[1]).test(pinned)?Optional.of(pinned):Optional.empty();
            }
            if(method.getName().equals("getService")&&args[0]==org.integratedmodelling.klab.api.services.KlabService.Type.REASONER)return pinned;
            if(method.getName().equals("findService")&&args[0] instanceof Class<?> requested
                &&org.integratedmodelling.klab.api.services.KlabService.class.isAssignableFrom(requested)) {
              var services=original.getServices((Class<? extends org.integratedmodelling.klab.api.services.KlabService>)requested);
              for(var candidate:services) {
                var bound=candidate instanceof Reasoner?pinned:candidate;
                if(!requested.isInstance(bound))throw new UnsupportedOperationException("Concrete Reasoner selector cannot escape a semantic cycle");
                if(((java.util.function.Predicate)args[1]).test(bound))return Optional.of(bound);
              }
              return Optional.empty();
            }
          }
          try {
            var result=method.invoke(original,args);
            return scopeResult(result,original,(Scope)proxy,pinned);
          }catch(InvocationTargetException failure){throw failure.getCause();}
        });
  }

  /** Scope APIs also expose sessions/contexts inside collections and optional results. */
  private static Object scopeResult(Object value,Scope original,Scope proxy,Reasoner pinned) {
    if(value instanceof Reasoner)return pinned;
    if(value instanceof Scope nested)return nested==original?proxy:pinnedScope(nested,pinned);
    if(value instanceof Optional<?> optional)return optional.map(item->scopeResult(item,original,proxy,pinned));
    if(value instanceof Set<?> values) {
      var result=new LinkedHashSet<>();for(var item:values)result.add(scopeResult(item,original,proxy,pinned));return result;
    }
    if(value instanceof Collection<?> values)
      return values.stream().map(item->scopeResult(item,original,proxy,pinned)).toList();
    return value;
  }
}
