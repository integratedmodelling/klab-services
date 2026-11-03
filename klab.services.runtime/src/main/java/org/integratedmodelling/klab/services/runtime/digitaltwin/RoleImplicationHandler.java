package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Execute newly attributed role implications before optional conferred-role characterization. */
public final class RoleImplicationHandler implements ConsequenceEngine.Handler {
  private final RoleConsequenceRuntime runtime;
  public RoleImplicationHandler(RoleConsequenceRuntime runtime){this.runtime=Objects.requireNonNull(runtime);}
  @Override public ConsequenceEngine.Result evaluate(Consequence.Cause cause,KnowledgeGraph.Commit changes,
      ServiceContextScope scope)throws Exception {
    RoleConferralHandler.cancelled(scope);
    var arrivals=runtime.arrivals(changes,scope);
    if(arrivals.status()!=ConsequenceRecipients.Status.COMPLETE)
      return new ConsequenceEngine.Result(arrivals.status()==ConsequenceRecipients.Status.FAILED?
          Consequence.Outcome.FAILED:Consequence.Outcome.DEFERRED,arrivals.reason(),java.util.List.of());
    var unique=new LinkedHashMap<String,RoleConsequenceRuntime.Arrival>();
    for(var arrival:arrivals.values()) {
      var key=arrival.recipientId()+"|"+arrival.role().getUrn();
      var previous=unique.putIfAbsent(key,arrival);
      if(previous!=null&&arrival.characterize()&&!previous.characterize())unique.put(key,arrival);
    }
    var evidence=new ArrayList<Long>();var resolutionResults=new ArrayList<String>();int applied=0,deferred=0,failed=0;
    try {
      var sourceEffect=runtime.sourceImplications(cause,changes,scope);evidence.addAll(sourceEffect.observations());
      resolutionResults.add("source="+sourceEffect.resolution()+": "+sourceEffect.reason());
      switch(sourceEffect.outcome()){case APPLIED -> applied++;case DEFERRED -> deferred++;case FAILED -> failed++;default -> {}}
    } catch(InterruptedException cancellation){throw cancellation;}
    catch(Exception failure){RoleConferralHandler.cancelled(scope);failed++;resolutionResults.add("source=FAILED ("+failure.getClass().getSimpleName()+")");}
    // Complete every newly attributed role's implications before starting characterization.
    for(var arrival:unique.values()) {
      RoleConferralHandler.cancelled(scope);
      try {
        var effect=runtime.implications(cause,arrival,scope);evidence.addAll(effect.observations());
        resolutionResults.add("implies:"+arrival.recipientId()+"/"+arrival.role().getUrn()+"="+effect.resolution()+": "+effect.reason());
        switch(effect.outcome()){case APPLIED -> applied++;case DEFERRED -> deferred++;case FAILED -> failed++;default -> {}}
      } catch(InterruptedException cancellation){throw cancellation;}
      catch(Exception failure){RoleConferralHandler.cancelled(scope);failed++;resolutionResults.add("implies:"+arrival.recipientId()+"=FAILED ("+failure.getClass().getSimpleName()+")");}
    }
    for(var arrival:unique.values())if(arrival.characterize()) {
      RoleConferralHandler.cancelled(scope);
      try {
        var effect=runtime.characterize(cause,arrival,scope);evidence.addAll(effect.observations());
        resolutionResults.add("characterize:"+arrival.recipientId()+"/"+arrival.role().getUrn()+"="+effect.resolution()+": "+effect.reason());
        switch(effect.outcome()){case APPLIED -> applied++;case DEFERRED -> deferred++;case FAILED -> failed++;default -> {}}
      } catch(InterruptedException cancellation){throw cancellation;}
      catch(Exception failure){RoleConferralHandler.cancelled(scope);failed++;resolutionResults.add("characterize:"+arrival.recipientId()+"=FAILED ("+failure.getClass().getSimpleName()+")");
        characterizationFailure(scope,arrival.recipientId(),failure);}
    }
    return new ConsequenceEngine.Result(RoleConferralHandler.outcome(applied,deferred,failed),
        "Role implications/characterization: "+applied+" applied, "+deferred+" deferred, "+failed+" failed; "+String.join("; ",resolutionResults),evidence.stream().distinct().toList());
  }
  private static void characterizationFailure(ServiceContextScope scope,long recipient,Exception failure) {
    var location="Role characterization failed: recipient="+recipient;
    // Preserve wrapped cause types and frames without transporting arbitrary exception messages.
    var safe=org.integratedmodelling.common.logging.Logging.safeFailure(failure);
    try {org.integratedmodelling.common.logging.Logging.INSTANCE.error(safe,location);}
    catch(RuntimeException ignored) { /* Diagnostics cannot replace the optional failure. */ }
    try {scope.warn(location,safe);}
    catch(RuntimeException ignored) { /* Diagnostic listeners cannot abort the remaining chain. */ }
  }
}
