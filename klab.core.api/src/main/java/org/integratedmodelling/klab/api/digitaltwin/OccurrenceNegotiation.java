package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

/** Durable evidence for a schedule choice. Constraints are never replaced by higher precedence. */
public record OccurrenceNegotiation(int version, String model, Request request,
    List<Declaration> declarations, OccurrenceSchedule effective) implements Serializable {
  public static final String DATA_KEY = "klab.occurrence.negotiation";
  public static final String REQUEST_KEY = "klab.occurrence.request";
  public record Request(String model, String dependency, String observable, OccurrenceSchedule schedule)
      implements Serializable {
    public Request {
      if (model == null || dependency == null || observable == null || schedule == null
          || schedule.source() != OccurrenceSchedule.Source.DEPENDENCY
          || schedule.minStep() != null || schedule.maxStep() != null || !schedule.overridable())
        throw new KlabValidationException("Invalid dependency schedule request");
    }
  }
  public record Declaration(String origin, OccurrenceSchedule schedule) implements Serializable {
    public Declaration {
      if (origin == null || origin.isBlank() || schedule == null
          || schedule.source() == OccurrenceSchedule.Source.DEPENDENCY)
        throw new KlabValidationException("Invalid implementation schedule declaration");
    }
  }
  public OccurrenceNegotiation {
    if ((version != 1 && version != 2) || model == null || effective == null || declarations == null
        || (declarations.isEmpty() && (version == 1 || request == null)))
      throw new KlabValidationException("Incomplete occurrence negotiation");
    declarations = List.copyOf(declarations);
    if (declarations.stream().filter(d -> d.schedule().source() == OccurrenceSchedule.Source.MODEL).count() > 1)
      throw new KlabValidationException("Multiple model schedule declarations");
  }

  public static OccurrenceNegotiation select(String model, Request request,
      List<Declaration> declarations, Time context) {
    if (declarations.isEmpty() && request == null) throw new KlabValidationException("Occurrent model requires a schedule: " + model);
    var modelDefault = declarations.stream().map(Declaration::schedule)
        .filter(s -> s.source() == OccurrenceSchedule.Source.MODEL).findFirst().orElse(null);
    var chosen = request != null ? request.schedule() : modelDefault != null ? modelDefault : declarations.getFirst().schedule();
    var version = declarations.isEmpty() || declarations.stream().anyMatch(d ->
        d.schedule().source() == OccurrenceSchedule.Source.CONTEXT_GEOMETRY
            || d.schedule().source() == OccurrenceSchedule.Source.OBSERVER_GEOMETRY) ? 2 : 1;
    var result = new OccurrenceNegotiation(version, model, request, declarations, chosen);
    result.validate(context);
    return result;
  }

  public void validate(Time context) {
    effective.bind(context);
    if (request != null && !equivalent(request.schedule(), effective, context))
      throw new KlabValidationException("Effective schedule differs from dependency request");
    boolean hasModel = declarations.stream().anyMatch(d -> d.schedule().source() == OccurrenceSchedule.Source.MODEL);
    var expected = request == null ? declarations.stream().map(Declaration::schedule)
        .filter(s -> s.source() == OccurrenceSchedule.Source.MODEL).findFirst().orElse(declarations.getFirst().schedule())
        : request.schedule();
    if (!equivalent(expected, effective, context)) throw new KlabValidationException("Effective schedule violates precedence");
    for (var declaration : declarations) {
      var allowed = declaration.schedule();
      try {
        if (allowed.source() == OccurrenceSchedule.Source.DEPENDENCY)
          throw new KlabValidationException("A request cannot declare implementation constraints");
        allowed.validateRange(allowed, context);
        if (hasModel) allowed.validateRange(declarations.stream().map(Declaration::schedule)
            .filter(s -> s.source() == OccurrenceSchedule.Source.MODEL).findFirst().orElseThrow(), context);
        allowed.validateRange(effective, context);
        if ((!allowed.overridable() || (request == null && !hasModel)) && !equivalent(allowed, effective, context))
          throw new KlabValidationException("Locked or conflicting default schedule");
      } catch (KlabValidationException e) {
        throw new KlabValidationException("Schedule for " + model + (request == null ? "" : " requested by "
            + request.model() + " dependency " + request.dependency() + " " + request.schedule())
            + " rejected by " + declaration.origin() + ": " + e.getMessage());
      }
    }
  }

  public void requireCompatible(Request requested, Time context) {
    if (requested == null) return;
    select(model, requested, declarations, context);
    if (!equivalent(effective, requested.schedule(), context))
      throw new KlabValidationException("Dependency " + requested.model() + "/" + requested.dependency()
          + " would reschedule an existing occurrence; implicit rescheduling is not supported");
  }

  public static boolean equivalent(OccurrenceSchedule first, OccurrenceSchedule second, Time context) {
    var a = first.bind(context); var b = second.bind(context);
    if (a.start() != b.start() || a.end() != b.end() || a.anchor() != b.anchor()) return false;
    if (first.sameTiming(second)) return true;
    var ca = new OccurrenceSchedule.Cadence(a.step(), a.unit());
    var cb = new OccurrenceSchedule.Cadence(b.step(), b.unit());
    long anchor = a.anchor();
    for (int i = 0; anchor < a.end(); i++) {
      if (i >= 1_000_000) throw new KlabValidationException("Schedule equivalence exceeds supported anchor count");
      long next = ca.advance(anchor);
      if (next != cb.advance(anchor)) return false;
      if (next <= anchor) throw new KlabValidationException("Non-advancing cadence");
      if (a.unit().isRegular() && b.unit().isRegular()) return true;
      anchor = next;
    }
    return true;
  }
}
