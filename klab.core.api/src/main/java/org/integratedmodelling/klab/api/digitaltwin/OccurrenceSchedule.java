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
    Source source,
    Cadence minStep,
    Cadence maxStep)
    implements Serializable {

  public enum Source {
    MODEL,
    JAVA,
    DEPENDENCY
  }

  public record Cadence(double step, Time.Resolution.Type unit) implements Serializable {
    public Cadence { new OccurrenceSchedule(1, "", "", step, unit, true, Source.MODEL); }
    public long advance(long anchor) {
      if (unit.isRegular()) {
        try { return Math.addExact(anchor, (long) (step * unit.getMilliseconds())); }
        catch (ArithmeticException e) { throw invalid("Cadence exceeds supported temporal bounds"); }
      }
      double factor = switch (unit) { case MILLENNIUM -> 1000; case CENTURY -> 100; case DECADE -> 10; default -> 1; };
      if (step * factor > Integer.MAX_VALUE) throw invalid("Calendar cadence exceeds native Time capacity");
      try {
        return org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(anchor)
            .plus(1, Time.Resolution.of(step, unit)).getMilliseconds();
      } catch (java.time.DateTimeException e) { throw invalid("Calendar cadence exceeds supported temporal bounds"); }
    }
  }

  /** Source compatibility for legacy declarations and callers. */
  public OccurrenceSchedule(int version, String start, String end, double step,
      Time.Resolution.Type unit, boolean overridable, Source source) {
    this(version, start, end, step, unit, overridable, source, null, null);
  }

  /** Graph properties store structured per-computation declarations as JSON list entries. */
  public record Computation(int index, OccurrenceSchedule schedule) implements Serializable {}

  public OccurrenceSchedule {
    if (version != 1 && version != 2) throw invalid("Unsupported occurrence schedule version " + version);
    if (version == 1 && (minStep != null || maxStep != null || source == Source.DEPENDENCY))
      throw invalid("Ranges and dependency requests require schedule version 2");
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
    return fromAnnotations(annotations, Source.MODEL);
  }

  public static OccurrenceSchedule fromDependency(Collection<Annotation> annotations) {
    return fromAnnotations(annotations, Source.DEPENDENCY);
  }

  private static OccurrenceSchedule fromAnnotations(Collection<Annotation> annotations, Source source) {
    var declarations = annotations.stream().filter(a -> "time".equals(a.getName())).toList();
    if (declarations.isEmpty()) return null;
    if (declarations.size() != 1) throw invalid("A model must have at most one @time annotation");
    var annotation = declarations.getFirst();
    for (var key : annotation.keySet()) {
      if (!key.startsWith("#") && !(source == Source.DEPENDENCY
          ? List.of("step", "start", "end") : List.of("step", "start", "end", "minStep", "maxStep", "overridable")).contains(key)) {
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
    var override = annotation.get("overridable");
    if (override != null && !(override instanceof Boolean)) throw invalid("@time overridable must be boolean");
    return new OccurrenceSchedule(
        2,
        bound(annotation, "start"),
        bound(annotation, "end"),
        resolution.getMultiplier(),
        resolution.getType(),
        override == null || (Boolean) override,
        source,
        cadence(annotation.get("minStep")),
        cadence(annotation.get("maxStep")));
  }

  private static Cadence cadence(Object value) {
    if (value == null) return null;
    if (!(value instanceof Quantity q) || q.getValue() == null || q.getUnit() == null || q.getCurrency() != null)
      throw invalid("@time acceptance endpoints require temporal Quantities");
    var resolution = Time.Resolution.of(q);
    return new Cadence(resolution.getMultiplier(), resolution.getType());
  }

  public static OccurrenceSchedule fromJava(Contextualizer annotation) {
    if (annotation == null) return null;
    // A marker with no temporal declaration remains usable for static contextualizers.
    if (annotation.timeStep() == -1
        && annotation.timeStart().isBlank()
        && annotation.timeEnd().isBlank()
        && annotation.timeMinStep() == -1 && annotation.timeMaxStep() == -1) return null;
    return new OccurrenceSchedule(
        2,
        annotation.timeStart(),
        annotation.timeEnd(),
        annotation.timeStep(),
        annotation.timeUnit(),
        annotation.timeOverridable(),
        Source.JAVA,
        annotation.timeMinStep() == -1 ? null : new Cadence(annotation.timeMinStep(), annotation.timeMinStepUnit()),
        annotation.timeMaxStep() == -1 ? null : new Cadence(annotation.timeMaxStep(), annotation.timeMaxStepUnit()));
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
        && ((unit.isRegular() && other.unit.isRegular())
            ? Double.compare(step * unit.getMilliseconds(), other.step * other.unit.getMilliseconds()) == 0
            : Double.compare(step, other.step) == 0 && unit == other.unit);
  }

  /** Validate full recurrence steps, including the unclipped final step, at native anchors. */
  public void validateRange(OccurrenceSchedule candidate, Time context) {
    if (minStep == null && maxStep == null) return;
    var bound = candidate.bind(context);
    var cadence = new Cadence(candidate.step(), candidate.unit());
    boolean fixed = candidate.unit().isRegular() && (minStep == null || minStep.unit().isRegular())
        && (maxStep == null || maxStep.unit().isRegular());
    long anchor = fixed ? bound.start() : bound.anchor();
    for (int checks = 0; anchor < bound.end(); checks++) {
      if (checks >= 1_000_000) throw invalid("Calendar range validation exceeds supported anchor count");
      try {
        long next = cadence.advance(anchor);
        if (next <= anchor) throw invalid("Cadence does not advance time");
        if (next > bound.start() && ((minStep != null && next < minStep.advance(anchor))
            || (maxStep != null && next > maxStep.advance(anchor))))
          throw invalid("Requested cadence " + candidate.step() + "." + candidate.unit()
              + " outside inclusive range " + minStep + " .. " + maxStep + " at " + anchor);
        if (fixed) return;
        anchor = next;
      } catch (ArithmeticException e) { throw invalid("Time range comparison overflow"); }
    }
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
