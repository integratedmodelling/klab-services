package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** Inherit existing scientific execution accounting only from the guarded native snapshot. */
final class ConsequenceInputEvidence {
  private ConsequenceInputEvidence() {}
  static void requireAttribution(ConsequenceNativeInputs.Snapshot inputs,long id) {
    if(inputs.reads().edges().stream().noneMatch(edge->edge.target().id() instanceof Number target&&target.longValue()==id
        &&edge.source().labels().contains(GraphModel.Labels.ACTIVITY)
        &&(edge.relationship()==GraphModel.Relationship.CLASSIFIED||edge.relationship()==GraphModel.Relationship.CHARACTERIZED)))
      throw new IllegalStateException("Role trigger has no captured attribution proof");
  }
  static Set<String> inherit(ConsequenceNativeInputs.Snapshot inputs,Collection<Long> consumed,
      Reasoner reasoner,ServiceContextScope scope) {
    return inherit(inputs,consumed,List.of(),reasoner,scope);
  }
  static Set<String> inherit(ConsequenceNativeInputs.Snapshot inputs,Collection<Long> references,Collection<Long> meaningOnly,
      Reasoner reasoner,ServiceContextScope scope) {
    var result=new TreeSet<String>();var consumed=new TreeSet<Long>(references);consumed.addAll(meaningOnly);
    for(var id:consumed) {
      var observation=inputs.observation(id);
      // Dependency requests carry canonical semantic meanings. Observable URNs may additionally
      // contain units/ranges; those remain in the guarded native observation and execution proof.
      result.add(observation.getObservable().asConcept().getUrn());
      var sourceEffect=GraphModel.Relationship.forContextualization(observation.getObservable().getContextualization());
      boolean source=false;
      for(var edge:inputs.reads().edges()) {
        if(!(edge.target().id() instanceof Number target)||target.longValue()!=id
            ||!edge.source().labels().contains(GraphModel.Labels.ACTIVITY))continue;
        boolean attribution=edge.relationship()==GraphModel.Relationship.CLASSIFIED||edge.relationship()==GraphModel.Relationship.CHARACTERIZED;
        boolean possibleBirth=references.contains(id)&&edge.relationship()==GraphModel.Relationship.CREATED
            &&observation.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.PROCESS);
        if(!attribution&&(!references.contains(id)||edge.relationship()!=sourceEffect&&!possibleBirth))continue;
        var snapshot=inputs.reads().assets().stream().filter(asset->asset.elementId().equals(edge.source().elementId())).findFirst()
            .orElseThrow(()->new IllegalStateException("Uncaptured consequence source Activity"));
        var properties=snapshot.nativeProperties();
        var encoded=properties.get(GraphModel.Fields.METADATA);
        SemanticEvidenceBinding binding=null;boolean birth=false;
        try { if(Activity.Outcome.SUCCESS.name().equals(properties.get(GraphModel.Fields.OUTCOME))&&encoded instanceof String metadata) {
          var decoded=Utils.Json.parseObject(metadata,Map.class);
          var value=decoded.get(SemanticEvidenceBinding.DATA_KEY);
          if(possibleBirth) {
            var type=properties.get(GraphModel.Fields.TYPE);
            birth=type instanceof String text&&org.integratedmodelling.klab.services.runtime.CompiledDataflow.processBirthAccounting(Activity.Type.valueOf(text),decoded,observation);
          }
          if(value instanceof String text)binding=Utils.Json.parseObject(text,SemanticEvidenceBinding.class);
        }}catch(RuntimeException malformed) {
          if(attribution)throw new IllegalStateException("Malformed consequence attribution accounting",malformed);
          continue;
        }
        if(possibleBirth&&!birth)continue;
        if(binding==null||!binding.complete()||binding.witness()==null) {
          if(attribution)throw new IllegalStateException("Unknown consequence attribution execution accounting");
          continue;
        }
        var before=binding.witness();
        var epoch=Objects.requireNonNull(scope.getResolutionCycleBinding()).semanticBinding();
        var request=new SemanticDependencyRequest(epoch.reasonerServiceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),binding.obligations());
        var current=reasoner.semanticDependencies(request,scope);
        if(!request.inputHash().equals(before.inputHash())||!SemanticDependencyWitness.revalidates(before,current)) {
          if(attribution)throw new IllegalStateException("Consequence attribution execution evidence expired or changed");
          continue;
        }
        result.addAll(binding.obligations());if(edge.relationship()==sourceEffect||birth)source=true;
      }
      if(references.contains(id)&&!source) {
        traceRejectedSource(inputs,id);
        throw new IllegalStateException("Unknown consequence source execution accounting for "+id);
      }
    }
    return Set.copyOf(result);
  }
  /** Read-only diagnostic of the already guarded snapshot; never used for admission. */
  private static void traceRejectedSource(ConsequenceNativeInputs.Snapshot inputs,long id) {
    if(!Boolean.getBoolean("klab.consequences.trace"))return;
    try {
      var rows=new ArrayList<Map<String,Object>>();
      for(var edge:inputs.reads().edges()) {
        if(!(edge.target().id() instanceof Number target)||target.longValue()!=id
            ||!edge.source().labels().contains(GraphModel.Labels.ACTIVITY))continue;
        var row=new LinkedHashMap<String,Object>();row.put("activity",edge.source().id());row.put("effect",edge.relationship().name());
        var activity=inputs.reads().assets().stream().filter(asset->asset.elementId().equals(edge.source().elementId())).findFirst();
        row.put("captured",activity.isPresent());
        if(activity.isPresent()) {
          var properties=activity.get().nativeProperties();row.put("type",properties.get(GraphModel.Fields.TYPE));row.put("outcome",properties.get(GraphModel.Fields.OUTCOME));
          if(properties.get(GraphModel.Fields.METADATA) instanceof String text) {
            var metadata=Utils.Json.parseObject(text,Map.class);
            row.put("birth",metadata.get(org.integratedmodelling.klab.services.runtime.CompiledDataflow.PROCESS_BIRTH_ACCOUNTING_KEY));
            row.put("collector",metadata.get("diagnostic.semanticExecution"));
            var encoded=metadata.get(SemanticEvidenceBinding.DATA_KEY);
            if(encoded instanceof String value) {
              var binding=Utils.Json.parseObject(value,SemanticEvidenceBinding.class);
              row.put("complete",binding.complete());row.put("obligations",binding.obligations());
              row.put("witnessPresent",binding.witness()!=null);
              row.put("witnessComplete",binding.witness()!=null&&binding.witness().completeAt(System.currentTimeMillis()));
            }
          }
        }
        rows.add(row);
      }
      org.integratedmodelling.common.logging.Logging.INSTANCE.info("CONSEQUENCE_SOURCE_ACCOUNTING "+Utils.Json.asString(
          Map.of("source",id,"meaning",inputs.observation(id).getObservable().getSemantics().getUrn(),"activities",rows)));
    } catch(RuntimeException ignored) { /* Diagnostics cannot replace the original rejection. */ }
  }
}
