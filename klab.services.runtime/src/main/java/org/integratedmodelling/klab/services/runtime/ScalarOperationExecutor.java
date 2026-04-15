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

  public ScalarOperationExecutor(
      ScalarComputation.Builder builder,
      Observation observation,
      Map<String, Observation> dependencies,
      ContextScope scope) {
    super(null, observation, scope, dependencies);
    this.scalarBuilder = builder;
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
