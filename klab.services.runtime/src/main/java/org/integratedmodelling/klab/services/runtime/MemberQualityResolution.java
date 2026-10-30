package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.*;

/** Native member execution coordinator. No input sampling, aggregation or bearer reassignment. */
final class MemberQualityResolution {
  private MemberQualityResolution() {}

  /** Pure preflight, shared by the public entry point and continuation; never discovers or allocates. */
  static void preflight(MemberQualityRequest request,ContextScope scope) {
    request.validate();request.container().requireContext(scope);
    var reasoner=Objects.requireNonNull(scope.getService(Reasoner.class));
    var members=reasoner.resolveObservable(request.members());var quality=reasoner.resolveObservable(request.quality());
    if (members==null || quality==null || !members.getSemantics().isCollective() || members.isAbstract()
        || !(members.is(SemanticType.SUBJECT)||members.is(SemanticType.AGENT)) || quality.isAbstract()
        || !quality.is(SemanticType.QUALITY) || quality.getContextualization()!=Contextualization.QUANTIFICATION
        || quality.getUnit()!=null || quality.getCurrency()!=null || quality.getObserverSemantics()!=null)
      throw new IllegalArgumentException("Unsupported closed native member-quality semantics");
    var bearer=reasoner.inherent(quality);
    if (bearer==null || !reasoner.is(members.getSemantics().singular(),bearer))
      throw new IllegalArgumentException("Member type does not prove the quality's native bearer");
    var observer=scope.getObserver();
    if(observer!=null && (observer.getId()<=0 || observer.getUrn()==null || observer.getObservable()==null
        || !observer.getObservable().is(SemanticType.AGENT)))
      throw new IllegalStateException("Member quality observer is not a known agent");
  }

  static CompletableFuture<MemberQualityCollection> resolve(
      org.integratedmodelling.klab.api.services.RuntimeService runtime, MemberQualityRequest request,
      ContextScope scope, java.util.function.BiFunction<Observable,MemberQualityCollection,Observation> completedDiscovery) {
    return resolve(runtime,request,scope,completedDiscovery,new MemberQualityTask<>());
  }

  static CompletableFuture<MemberQualityCollection> resolve(
      org.integratedmodelling.klab.api.services.RuntimeService runtime, MemberQualityRequest request,
      ContextScope scope, java.util.function.BiFunction<Observable,MemberQualityCollection,Observation> completedDiscovery,
      MemberQualityTask<MemberQualityCollection> task) {
    try {
      task.requireActive();
      preflight(request,scope);
      request.validate();
      request.container().requireContext(scope);
      var reasoner=Objects.requireNonNull(scope.getService(Reasoner.class));
      var members=reasoner.resolveObservable(request.members());
      var quality=reasoner.resolveObservable(request.quality());
      if (members==null || quality==null || !members.getSemantics().isCollective()
          || members.isAbstract() || !(members.is(SemanticType.SUBJECT)||members.is(SemanticType.AGENT))
          || quality.isAbstract() || !quality.is(SemanticType.QUALITY)
          || quality.getContextualization()!=Contextualization.QUANTIFICATION
          || quality.getUnit()!=null || quality.getCurrency()!=null || quality.getObserverSemantics()!=null)
        throw new IllegalArgumentException("Unsupported closed native member-quality semantics");
      var bearer=reasoner.inherent(quality);
      if (bearer==null || !reasoner.is(members.getSemantics().singular(),bearer))
        throw new IllegalArgumentException("Member type does not prove the quality's native bearer");
      var observer=scope.getObserver();
      if (observer!=null && (observer.getId()<=0 || observer.getUrn()==null
          || observer.getObservable()==null || !observer.getObservable().is(SemanticType.AGENT)))
        throw new IllegalStateException("Member quality observer is not a known agent");
      var result=new MemberQualityCollectionImpl(); result.setRequest(request);
      result.setObserverId(observer==null?null:observer.getId());
      result.setObserverUrn(observer==null?null:observer.getUrn());
      result.setObserverClass(observer==null?null:observer.getObservable().getUrn());
      var query=completedDiscovery.apply(members,result);
      requireScope(result,scope);
      if (query==null || query.isEmpty() || query.getObservable()==null
          || !request.members().equals(query.getObservable().getUrn()) || !Double.isFinite(query.getResolvedCoverage())
          || query.getResolvedCoverage()<1.0-1e-9
          || !sameSupport(query.getGeometry(),scope.getContextObservation().getGeometry()))
        throw new IllegalStateException("Member discovery is not complete over the exact container support");
      var actualMembers=List.copyOf(runtime.getMembers(query,scope));
      var identities=new HashSet<Long>();
      // Validate all members before scheduling any quality computation.
      for(var member:actualMembers) {
        task.requireActive();
        if (!SingularContextBinding.eligible(member) || !identities.add(member.getId())
            || !reasoner.is(member.getObservable(),members.getSemantics().singular()))
          throw new IllegalStateException("Invalid or duplicate native member identity");
        requireMember(member,scope);
        if (!sameSupport(member.getGeometry(),query.getGeometry())
            && !sameSupport(member.getGeometry(),RuntimeService.intersection(member.getGeometry(),query.getGeometry())))
          throw new IllegalStateException("Partial member support requires an explicit support mapping");
      }
      CompletableFuture<MemberQualityCollection> future=CompletableFuture.completedFuture(result);
      for(var member:actualMembers) {
        future=future.thenCompose(view->{
          task.requireActive();
          requireScope(result,scope);requireMember(member,scope);
          var nativeScope=scope.within(member);
          var builder=new Observation.NaiveBuilder(quality,nativeScope);builder.geometry(member.getGeometry());
          var submitted=builder.make();
          var child=runtime.submit(submitted,nativeScope);task.active(child);
          return child.thenApply(resolved->{
            task.requireActive();
            requireScope(result,scope);requireMember(member,scope);
            if (resolved==null || resolved.isEmpty() || resolved.getId()<=0
                || resolved.getParentId()!=member.getId() || !Double.isFinite(resolved.getResolvedCoverage())
                || resolved.getResolvedCoverage()<1.0-1e-9
                || !compatibleQuality(resolved.getObservable(),quality,scope)
                || !sameSupport(member.getGeometry(),resolved.getGeometry()))
              throw new IllegalStateException("Native quality did not resolve with the member identity and support");
            result.getEntries().add(new MemberQualityCollectionImpl.EntryImpl(
                Observation.forTransport(member),Observation.forTransport(resolved)));
            return result;
          });
        });
      }
      future.thenApply(view->{task.requireActive();requireScope(result,scope);return view;}).whenComplete(task::finish);
    } catch (Exception failure) { task.completeExceptionally(failure); }
    return task;
  }

  static void requireScope(MemberQualityCollection view,ContextScope scope) {
    view.getRequest().container().requireContext(scope);
    var observer=scope.getObserver();
    if (!Objects.equals(view.getObserverId(),observer==null?null:observer.getId())
        || !Objects.equals(view.getObserverUrn(),observer==null?null:observer.getUrn())
        || !Objects.equals(view.getObserverClass(),observer==null?null:observer.getObservable().getUrn()))
      throw new IllegalStateException("Member collection observer changed");
  }
  /** A transported collection is a view, not an admission certificate: re-read every identity. */
  static void requireForConsumption(MemberQualityCollection view,ContextScope scope) {
    if(view==null)throw new IllegalStateException("Missing typed member-quality input");
    preflight(view.getRequest(),scope);requireScope(view,scope);var seen=new HashSet<Long>();
    var reasoner=scope.getService(Reasoner.class);
    var quality=reasoner.resolveObservable(view.getRequest().quality());
    var members=reasoner.resolveObservable(view.getRequest().members());
    for(var entry:view.getEntries()) {
      if(entry==null || entry.getMember()==null || entry.getQuality()==null || entry.getQuality().getObservable()==null
          || !seen.add(entry.getMember().getId()))
        throw new IllegalStateException("Malformed member-quality collection");
      requireMember(entry.getMember(),scope);
      if(!reasoner.is(entry.getMember().getObservable(),members.getSemantics().singular())
          || !sameSupport(entry.getMember().getGeometry(),scope.getContextObservation().getGeometry())
              && !sameSupport(entry.getMember().getGeometry(),RuntimeService.intersection(
                  entry.getMember().getGeometry(),scope.getContextObservation().getGeometry())))
        throw new IllegalStateException("Member no longer proves the requested type and container support");
      var nativeQuality=scope.getObservation(entry.getQuality().getId());
      if(nativeQuality==null || nativeQuality.isEmpty()
          || nativeQuality.getId()<=0 || !Double.isFinite(nativeQuality.getResolvedCoverage())
          || nativeQuality.getResolvedCoverage()<1.0-1e-9
          || nativeQuality.getParentId()!=entry.getMember().getId()
          || !Objects.equals(nativeQuality.getUrn(),entry.getQuality().getUrn())
          || !compatibleQuality(nativeQuality.getObservable(),quality,scope)
          || !Objects.equals(nativeQuality.getObservable().getUrn(),entry.getQuality().getObservable().getUrn())
          || !sameSupport(nativeQuality.getGeometry(),entry.getQuality().getGeometry())
          || !sameSupport(nativeQuality.getGeometry(),entry.getMember().getGeometry()))
        throw new IllegalStateException("Native member result is inaccessible or changed");
    }
  }
  private static void requireMember(Observation member,ContextScope scope) {
    var visible=scope.getObservation(member.getId());
    if (!SingularContextBinding.eligible(visible) || !Objects.equals(visible.getUrn(),member.getUrn())
        || !Objects.equals(visible.getObservable().getUrn(),member.getObservable().getUrn())
        || !sameSupport(visible.getGeometry(),member.getGeometry()))
      throw new IllegalStateException("Member is inaccessible or changed");
  }
  static boolean sameSupport(Geometry a,Geometry b) {
    if(a==null || b==null)return false;
    if(Geometry.forTransport(a).encode().equals(Geometry.forTransport(b).encode()))return true;
    // A singleton physical interval's intersection may make its previously unspecified
    // full-interval resolution explicit. Normalize through self-intersection only when
    // topology, temporal kind and every explicitly declared resolution survive unchanged.
    try {
      var repository=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
      var left=repository.scale(a);var right=repository.scale(b);
      var lt=left.getTime();var rt=right.getTime();
      if(lt==null || rt==null || lt.getTimeType()!=org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Type.PHYSICAL
          || rt.getTimeType()!=lt.getTimeType() || lt.isRegular() || rt.isRegular() || lt.size()!=1 || rt.size()!=1
          || !sameTopology(a,b))return false;
      var lc=repository.getIntersection(a,a,Geometry.class);var rc=repository.getIntersection(b,b,Geometry.class);
      if(!sameTopology(a,lc) || !sameTopology(b,rc))return false;
      var lct=repository.scale(lc).getTime();var rct=repository.scale(rc).getTime();
      if(lct.getTimeType()!=lt.getTimeType() || rct.getTimeType()!=rt.getTimeType()
          || !retainsResolution(lt.getResolution(),lct.getResolution())
          || !retainsResolution(rt.getResolution(),rct.getResolution()))return false;
      return Geometry.forTransport(lc).encode().equals(Geometry.forTransport(rc).encode());
    } catch(RuntimeException invalidSupport) {return false;}
  }
  private static boolean sameTopology(Geometry first,Geometry second) {
    var a=Geometry.forTransport(first).getDimensions();var b=Geometry.forTransport(second).getDimensions();
    if(a.size()!=b.size())return false;
    for(int i=0;i<a.size();i++) {
      var x=a.get(i);var y=b.get(i);
      if(x.getType()!=y.getType() || x.isGeneric()!=y.isGeneric() || x.isRegular()!=y.isRegular()
          || x.getDimensionality()!=y.getDimensionality() || x.size()!=y.size() || !Objects.equals(x.getShape(),y.getShape())
          || x.getType()!=Geometry.Dimension.Type.TIME && !Objects.equals(x.encode(),y.encode()))return false;
      // Time.merge rebuilds physical bounds and can omit coverage or other extra metadata.
      // This fallback only understands the basic interval plus derived resolution; richer
      // supports must remain exactly equal or await an explicit support-mapping contract.
      if(x.getType()==Geometry.Dimension.Type.TIME) {
        var supported=Set.of("tstart","tend","ttype","tscope","tunit");
        if(!supported.containsAll(x.getParameters().keySet()) || !supported.containsAll(y.getParameters().keySet()))return false;
      }
    }
    return true;
  }
  private static boolean retainsResolution(
      org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution before,
      org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution after) {
    return before==null || after!=null && before.getType()==after.getType()
        && Double.compare(before.getMultiplier(),after.getMultiplier())==0;
  }
  private static boolean compatibleQuality(Observable actual,Observable requested,ContextScope scope) {
    if(actual==null || requested==null)return false;
    String expected=requested.getUrn();
    if(requested.getObserverSemantics()==null && actual.getObserverSemantics()!=null) {
      try {expected=org.integratedmodelling.common.knowledge.ObservableImpl
          .withObserverSemantics(requested,actual.getObserverSemantics()).getUrn();}
      catch(IllegalArgumentException noncanonical) {return false;}
    }
    return Objects.equals(expected,actual.getUrn())
        && org.integratedmodelling.klab.runtime.ObserverResolution.preference(actual,requested,scope)>=0;
  }
}
