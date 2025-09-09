package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

import java.util.Map;

public class ScalarOperationExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ScalarComputation.Builder scalarBuilder;
  private ScalarComputation scalarMapper;

  public ScalarOperationExecutor(
      ScalarComputation.Builder builder,
      Observation observation,
      Map<String, Observable> localNames,
      ContextScope scope) {
    super(null, observation, scope, localNames);
    this.scalarBuilder = builder;
  }

  @Override
  protected boolean run(Scheduler.Event event, Storage.Scanner scanner) {

    return scalarMapper.execute(scanner.shard().getGeometry(), event, scope);
  }

  @Override
  public boolean validate() {
    if (scalarMapper == null) {
      try {
        scalarMapper = scalarBuilder.build();
      } catch (Exception e) {
        cause = e;
        return false;
      }
    }
    return true;
  }
}
