package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Contextualizer {

  /**
   * Start time where the contextualizer is valid, as an ISO-8601 instant with offset.
   * Empty means the contextual extent's start.
   *
   * @return
   */
  String timeStart() default "";

  /**
   * End time where the contextualizer is valid, as an ISO-8601 instant with offset.
   * Empty means the contextual extent's end.
   *
   * @return
   */
  String timeEnd() default "";

  /**
   * Time step in {@link #timeUnit()} unit.
   *
   * @return
   */
  long timeStep() default -1L;

  /**
   * Time unit for {@link #timeStep()}.
   *
   * @return
   */
  Time.Resolution.Type timeUnit() default Time.Resolution.Type.MILLISECOND;

  /** Whether model or dependency @time may replace this schedule. Ranges never remove a lock. */
  boolean timeOverridable() default true;

  /** Inclusive shortest accepted interval; -1 leaves this endpoint unconstrained. */
  long timeMinStep() default -1L;
  /** Native unit for the minimum, independent of the default cadence unit. */
  Time.Resolution.Type timeMinStepUnit() default Time.Resolution.Type.MILLISECOND;
  /** Inclusive longest accepted interval; -1 leaves this endpoint unconstrained. */
  long timeMaxStep() default -1L;
  /** Native unit for the maximum, independent of the default cadence unit. */
  Time.Resolution.Type timeMaxStepUnit() default Time.Resolution.Type.MILLISECOND;
}
