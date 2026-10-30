package org.integratedmodelling.klab.services.runtime;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

public class ScalarOperationExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ScalarComputation.Builder scalarBuilder;
  private ScalarComputation scalarMapper;
  private final Map<String,Observation> compiledDependencies;

  public ScalarOperationExecutor(
      ScalarComputation.Builder builder,
      Observation observation,
      Map<String, Observation> dependencies,
      ContextScope scope,
      ShardExecution shardExecution) {
    super(null, observation, scope, dependencies, shardExecution);
    this.scalarBuilder = builder;
    this.compiledDependencies = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(dependencies));
  }

  java.util.Optional<java.util.List<Observation>> semanticInputs(
      org.integratedmodelling.klab.services.scopes.ServiceContextScope executionScope) {
    if (getClass() != ScalarOperationExecutor.class || !compiledDependencies.keySet().equals(dependencies.keySet()))
      return java.util.Optional.empty();
    for (var entry : compiledDependencies.entrySet())
      if (entry.getValue() != dependencies.get(entry.getKey())) return java.util.Optional.empty();
    var proof = org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.semanticInputs(
        scalarBuilder, scalarMapper, input -> {
          if (input == null) return false;
          if (input.getClass() == org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.class) {
            var observable = input.getObservable();
            return observable != null && observable.getClass() == org.integratedmodelling.common.knowledge.ObservableImpl.class
                && observable.getSemantics() != null
                && observable.getSemantics().getClass() == org.integratedmodelling.common.knowledge.ConceptImpl.class;
          }
          return executionScope.getCurrentTransaction() instanceof
              org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl transaction
              && transaction.ownsObservationReadView(input);
        }, dependencies, observation);
    if (proof.isEmpty()) return proof;
    for (var input : proof.get()) if (input != observation
        && compiledDependencies.values().stream().noneMatch(candidate -> candidate == input)) return java.util.Optional.empty();
    return proof;
  }

  @Override
  public boolean execute(Scheduler.Event event,
      org.integratedmodelling.klab.services.scopes.ServiceContextScope scope,
      RuntimeService.ContextualizationScope results) {
    if (event.getType()!=Scheduler.Event.Type.INITIALIZATION)
      return TemporalScalarExecution.run(scalarMapper,observation,dependencies,event,scope,false,shardExecution);
    return super.execute(event,scope,results);
  }

  @Override
  protected boolean run(
      Scheduler.Event event,
      Map<String, Storage.Scanner> scanners,
      ContextScope scope,
      RuntimeService.ContextualizationScope contextualizationScope) {
    // TODO check if we need the ctxScope
    return scalarMapper.execute(scanners, event, scope);
  }

  @Override
  public boolean validate() {
    if (scalarMapper == null) {
      try {
        scalarMapper = scalarBuilder.build();
        if (scalarMapper == null) {
          cause = new KlabIllegalStateException("Scalar operation failed to compile");
          return false;
        }
      } catch (Throwable e) {
        cause = e;
        return false;
      }
    }
    return true;
  }
}
