package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

public class ScalarOperationExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ScalarComputation.Builder scalarBuilder;
  private ScalarComputation scalarMapper;

  public ScalarOperationExecutor(
      ScalarComputation.Builder builder, Observation observation, ContextScope scope) {
    super(null, observation, scope);
    this.scalarBuilder = builder;
  }

  @Override
  protected boolean run(Scheduler.Event event, Storage.Scanner scanner) {
    if (scalarMapper == null) {
      scalarMapper = scalarBuilder.build();
    }
    return scalarMapper.execute(scanner.shard().getGeometry(), event, scope);
  }
}
