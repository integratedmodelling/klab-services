package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.services.runtime.extension.Contextualizer;

/**
 * Portable occurrence schedule declaration. Empty bounds inherit the contextual time extent;
 * calendar units retain their identity and must never be scheduled using their indicative span.
 * This declaration does not activate a clock or certify computed temporal coverage.
 */
public record OccurrenceSchedule(
    int version,
    String start,
    String end,
    double step,
    Time.Resolution.Type unit,
    boolean overridable,
    Source source)
    implements Serializable {

  public enum Source {
    MODEL,
    JAVA
  }

  /** Graph properties store structured per-computation declarations as JSON list entries. */
  public record Computation(int index, OccurrenceSchedule schedule) implements Serializable {}

  public OccurrenceSchedule {
    if (version != 1) throw invalid("Unsupported occurrence schedule version " + version);
    start = start == null ? "" : start.trim();
    end = end == null ? "" : end.trim();
    if (unit == null || source == null) throw invalid("Schedule unit and source are required");
    if (!Double.isFinite(step) || step <= 0) throw invalid("Time step must be finite and positive");
    if (!unit.isRegular() && (step != Math.rint(step) || step > Integer.MAX_VALUE)) {
      throw invalid("Calendar time step must be a positive supported integer");
    }
    if (unit.isRegular()) {
      double millis = step * unit.getMilliseconds();
      if (millis < 1 || millis >= Long.MAX_VALUE || millis != Math.rint(millis)) {
        throw invalid("Time step must fit a positive whole number of milliseconds");
      }
    }
    Long from = parseBound(start);
    Long until = parseBound(end);
    if (from != null && until != null && from >= until) {
      throw invalid("Schedule start must precede end");
    }
  }

  /** Read only the model's own @time, without inheriting unrelated observation annotations. */
  public static OccurrenceSchedule fromModel(Collection<Annotation> annotations) {
    var declarations = annotations.stream().filter(a -> "time".equals(a.getName())).toList();
    if (declarations.isEmpty()) return null;
    if (declarations.size() != 1) throw invalid("A model must have at most one @time annotation");
    var annotation = declarations.getFirst();
    for (var key : annotation.keySet()) {
      if (!key.startsWith("#") && !List.of("step", "start", "end").contains(key)) {
        throw invalid("Unsupported @time parameter: " + key);
      }
    }
    if (!(annotation.get("step") instanceof Quantity quantity)
        || quantity.getValue() == null
        || quantity.getUnit() == null
        || quantity.getCurrency() != null) {
      throw invalid("@time(step=...) requires a temporal Quantity, e.g. 1.month");
    }
    var resolution = Time.Resolution.of(quantity);
    return new OccurrenceSchedule(
        1,
        bound(annotation, "start"),
        bound(annotation, "end"),
        resolution.getMultiplier(),
        resolution.getType(),
        true,
        Source.MODEL);
  }

  public static OccurrenceSchedule fromJava(Contextualizer annotation) {
    if (annotation == null) return null;
    // A marker with no temporal declaration remains usable for static contextualizers.
    if (annotation.timeStep() == -1
        && annotation.timeStart().isBlank()
        && annotation.timeEnd().isBlank()) return null;
    return new OccurrenceSchedule(
        1,
        annotation.timeStart(),
        annotation.timeEnd(),
        annotation.timeStep(),
        annotation.timeUnit(),
        annotation.timeOverridable(),
        Source.JAVA);
  }

  /** Select one schedule for the whole computation chain, checking every Java lock. */
  public static OccurrenceSchedule select(
      OccurrenceSchedule model, Collection<OccurrenceSchedule> contextualizers) {
    OccurrenceSchedule selected = model;
    for (var declared : contextualizers) {
      if (declared == null) continue;
      if (selected == null) selected = declared;
      else if (!selected.sameTiming(declared) && (model == null || !declared.overridable())) {
        throw invalid("Conflicting contextualizer schedules or locked @time override");
      } else if (!declared.overridable() && selected.sameTiming(declared)) selected = declared;
    }
    if (selected == null)
      throw invalid("Occurrent model requires @time or a Java contextualizer schedule");
    return selected;
  }

  public boolean sameTiming(OccurrenceSchedule other) {
    return Objects.equals(start, other.start)
        && Objects.equals(end, other.end)
        && Double.compare(step, other.step) == 0
        && unit == other.unit;
  }

  /**
   * Bind against the complete context bounds; retain cadence/phase separately from its grid step.
   */
  public Bound bind(Time context) {
    if (context == null || context.getStart() == null || context.getEnd() == null) {
      throw invalid("First-stage occurrence schedules require a bounded context time extent");
    }
    long contextStart = context.getStart().getMilliseconds();
    long contextEnd = context.getEnd().getMilliseconds();
    Long declaredStart = parseBound(start);
    Long declaredEnd = parseBound(end);
    long anchor = declaredStart == null ? contextStart : declaredStart;
    long from = Math.max(contextStart, anchor);
    long until = Math.min(contextEnd, declaredEnd == null ? contextEnd : declaredEnd);
    if (from >= until) throw invalid("Schedule has no temporal support in the context");
    return new Bound(from, until, anchor, step, unit);
  }

  public record Bound(long start, long end, long anchor, double step, Time.Resolution.Type unit)
      implements Serializable {
    public Time.Resolution resolution() {
      return Time.Resolution.of(step, unit);
    }
  }

  private static String bound(Annotation annotation, String name) {
    var value = annotation.get(name);
    if (value == null) return "";
    if (value instanceof String text) return text;
    throw invalid("@time " + name + " must be an ISO-8601 instant string");
  }

  private static Long parseBound(String bound) {
    if (bound.isEmpty()) return null;
    try {
      return Instant.parse(bound).toEpochMilli();
    } catch (DateTimeParseException | ArithmeticException e) {
      throw invalid("Invalid schedule bound (expected ISO-8601 instant with offset): " + bound);
    }
  }

  private static KlabValidationException invalid(String message) {
    return new KlabValidationException(message);
  }
}
