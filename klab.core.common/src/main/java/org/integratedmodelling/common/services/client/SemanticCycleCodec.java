package org.integratedmodelling.common.services.client;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Shared typed transport for the explicit read/build semantic operation allowlist. */
public final class SemanticCycleCodec {
  private SemanticCycleCodec() {}
  private static final Set<String> ALLOWED=Set.of(
      "resolveConcept","resolveObservable","declareObservable","buildConcept","buildObservable",
      "capabilities","semanticDependencies","validateDocument","semanticDistance","assertedDistance",
      "adjacent","affected","affectedBy","affectedOrCreated","allChildren","allParents",
      "applicableObservables","attributes","authoredClosure","baseObservable","baseSubstantialType",
      "causant","caused","children","closure","compatible","compose","compresent",
      "computeIdentificationStrategies","computeObservationStrategies","concretize","contextuallyCompatible",
      "cooccurrent","coreObservable","created","createdBy","describedType","directAdjacent",
      "directAttributes","directCausant","directCaused","directCompresent","directCooccurrent",
      "directGoal","directIdentities","directInherent","directRealms","directRelativeTo",
      "directRoles","directTraits","displayLabel","displayName","domain","goal",
      "hasDirectRole","hasDirectTrait","hasParentRole","hasRole","hasTrait","identities",
      "impliedRole","impliedRoles","influences","inherent","is","isConsistent",
      "leastGeneralCommon","lexicalRoot","match","negated","observableType","occurrent",
      "operands","parent","parents","rawObservable","realms","relationshipSource",
      "relationshipSources","relationshipTarget","relationshipTargets","relativeTo","resolves",
      "resolving","roles","rolesFor","satisfiable","splitOperators","style","traits",
      "resolveAuthorityHierarchy","authorityCodelists","searchAuthority","getAuthorityBindings",
      "getAuthorityDocumentation","consequenceRules","consequenceApplicable");

  public static boolean allowed(Method method) {
    return method.getDeclaringClass()==Reasoner.class && !method.isBridge() && !method.isSynthetic()
        && ALLOWED.contains(method.getName());
  }
  public static Method method(String name,List<String> parameterTypes) {
    return Arrays.stream(Reasoner.class.getMethods()).filter(SemanticCycleCodec::allowed)
        .filter(m->m.getName().equals(name))
        .filter(m->Arrays.stream(m.getParameterTypes()).map(Class::getName).toList().equals(parameterTypes))
        .findFirst().orElseThrow(()->new SecurityException("Operation is not allowed in a retained semantic cycle"));
  }
  public static String encode(Object value,Type type) {
    try {return mapper().writeValueAsString(tree(value,type));}
    catch(java.io.IOException failure){throw new IllegalArgumentException("Cannot encode semantic cycle value",failure);}
  }
  public static Object decode(String json,Type type) {
    try {return value(mapper().readTree(json),type);}
    catch(java.io.IOException failure){throw new IllegalArgumentException("Cannot decode semantic cycle value",failure);}
  }
  private static ObjectMapper mapper(){return JacksonConfiguration.newObjectMapper();}
  private static Type bound(Type type) {
    if(type instanceof TypeVariable<?> variable)return bound(variable.getBounds()[0]);
    if(type instanceof WildcardType wildcard)return bound(wildcard.getUpperBounds()[0]);
    return type;
  }
  private static Class<?> raw(Type type) {
    type=bound(type);
    return type instanceof ParameterizedType p?(Class<?>)p.getRawType():(Class<?>)type;
  }
  private static Type argument(Type type,int position) {
    return type instanceof ParameterizedType p?p.getActualTypeArguments()[position]:Object.class;
  }
  private static JsonNode tree(Object value,Type type) throws java.io.IOException {
    type=bound(type);var raw=raw(type);var mapper=mapper();
    if(value==null || Scope.class.isAssignableFrom(raw))return NullNode.instance;
    // Match ordinary semantic RPCs: runtime scales/caches are local, not wire DTOs.
    if(value instanceof Observation observation)value=Observation.forTransport(observation);
    else if(value instanceof Geometry geometry)value=Geometry.forTransport(geometry);
    if(Map.class.isAssignableFrom(raw)) {
      var entries=mapper.createArrayNode();
      for(var entry:((Map<?,?>)value).entrySet()) {
        var pair=mapper.createArrayNode();pair.add(tree(entry.getKey(),argument(type,0)));
        pair.add(tree(entry.getValue(),argument(type,1)));entries.add(pair);
      }
      return mapper.createObjectNode().set("semanticMapEntries",entries);
    }
    if(Collection.class.isAssignableFrom(raw)) {
      var result=mapper.createArrayNode();
      for(var item:(Collection<?>)value)result.add(tree(item,argument(type,0)));
      return result;
    }
    if(raw.isArray()) {
      var result=mapper.createArrayNode();for(int i=0;i<Array.getLength(value);i++)result.add(tree(Array.get(value,i),raw.getComponentType()));return result;
    }
    return mapper.readTree(mapper.writerFor(mapper.getTypeFactory().constructType(type)).writeValueAsString(value));
  }
  private static Object value(JsonNode node,Type type) throws java.io.IOException {
    type=bound(type);var raw=raw(type);var mapper=mapper();
    if(Scope.class.isAssignableFrom(raw))throw new SecurityException("Scopes cannot be deserialized from semantic arguments");
    if(node==null || node.isNull())return null;
    if(Map.class.isAssignableFrom(raw)) {
      if(!node.isObject() || !node.path("semanticMapEntries").isArray())throw new IllegalArgumentException("Invalid typed semantic map");
      var result=new LinkedHashMap<>();
      for(var pair:node.path("semanticMapEntries")) {
        if(!pair.isArray()||pair.size()!=2)throw new IllegalArgumentException("Invalid semantic map entry");
        result.put(value(pair.get(0),argument(type,0)),value(pair.get(1),argument(type,1)));
      }
      return result;
    }
    if(Collection.class.isAssignableFrom(raw)) {
      if(!node.isArray())throw new IllegalArgumentException("Invalid semantic collection");
      Collection<Object> result=Set.class.isAssignableFrom(raw)?new LinkedHashSet<>():new ArrayList<>();
      for(var item:node)result.add(value(item,argument(type,0)));return result;
    }
    if(raw.isArray()) {
      if(!node.isArray())throw new IllegalArgumentException("Invalid semantic array");
      Object result=Array.newInstance(raw.getComponentType(),node.size());
      for(int i=0;i<node.size();i++)Array.set(result,i,value(node.get(i),raw.getComponentType()));return result;
    }
    var target=raw==Reasoner.Capabilities.class?ReasonerCapabilitiesImpl.class:type;
    // Registered polymorphic bean readers need the mapper as parser codec for nested generics.
    try(var parser=node.traverse(mapper)) {
      return mapper.readValue(parser,mapper.getTypeFactory().constructType(target));
    }
  }
}
