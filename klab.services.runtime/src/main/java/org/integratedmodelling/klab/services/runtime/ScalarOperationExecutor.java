package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.Map;

public class ScalarOperationExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ScalarComputation.Builder scalarBuilder;
  private ScalarComputation scalarMapper;

  public ScalarOperationExecutor(
      ScalarComputation.Builder builder,
      Observation observation,
      Map<String, Observation> dependencies,
      ContextScope scope) {
    super(null, observation, scope, dependencies);
    this.scalarBuilder = builder;
  }

  @Override
  protected boolean run(Scheduler.Event event, Map<String,Storage.Scanner> scanners, ContextScope scope) {
    return scalarMapper.execute(scanners.get(Dataflow.SELF_ID).shard().getGeometry(), event, scope);
  }

  @Override
  public boolean validate() {
    if (scalarMapper == null) {
      try {
        scalarMapper = scalarBuilder.build();
      } catch (Throwable e) {
        cause = e;
        return false;
      }
    }
    return true;
  }
}
