package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.utilities.Utils;

final class ScheduleNegotiationSupport {
  static OccurrenceNegotiation negotiate(Model model, ResolutionGraph graph, ContextScope scope, Time time) {
    if (model.getObservables().isEmpty()) return null;
    var primary = model.getObservables().getFirst();
    if (!primary.is(SemanticType.PROCESS)
        && !(primary.is(SemanticType.EVENT) && primary.getSemantics().isCollective())) return null;
    var declarations = new ArrayList<OccurrenceNegotiation.Declaration>();
    var schedule = OccurrenceSchedule.fromModel(model.getAnnotations());
    if (schedule != null) declarations.add(new OccurrenceNegotiation.Declaration(model.getUrn(), schedule));
    for (var computation : model.getComputation()) {
      if (computation.getServiceCall() == null) continue;
      var urn = computation.getServiceCall().getUrn();
      var info = graph.getServiceInfo(urn);
      if (info == null) {
        var runtime = scope.getService(RuntimeService.class);
        if (runtime != null) info = runtime.getServiceInfo(urn, scope);
        if (info != null) graph.addServiceInfo(urn, info);
      }
      if (info != null && info.getOccurrenceSchedule() != null)
        declarations.add(new OccurrenceNegotiation.Declaration(urn, info.getOccurrenceSchedule()));
    }
    var request = primary.is(SemanticType.PROCESS) ? graph.scheduleRequest : null;
    if (declarations.isEmpty() && request == null) {
      var geometry = geometryDefault(primary.is(SemanticType.PROCESS), scope);
      declarations.add(geometry);
    }
    return OccurrenceNegotiation.select(model.getUrn(), request,
        declarations, time);
  }

  static OccurrenceNegotiation.Declaration geometryDefault(boolean process, ContextScope scope) {
    var owner = process ? scope.getContextObservation() : scope.getObserver();
    if (owner == null || owner.isEmpty())
      throw new KlabValidationException("No schedule-bearing context or observer is available");
    if (!process && !owner.getObservable().is(SemanticType.AGENT))
      throw new KlabValidationException("Schedule fallback requires an agent's perceived geometry");
    var geometry = process ? owner.getGeometry()
        : owner.geometry(Observation.GeometryRelationship.PERCEIVES);
    var time = geometry == null ? null
        : org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry).getTime();
    return new OccurrenceNegotiation.Declaration((process ? "context:" : "observer:") + owner.getId(),
        OccurrenceSchedule.fromGeometry(time, process ? OccurrenceSchedule.Source.CONTEXT_GEOMETRY
            : OccurrenceSchedule.Source.OBSERVER_GEOMETRY));
  }

  static void checkReuse(Observation observation, OccurrenceNegotiation.Request request, Time time,
      ContextScope scope) {
    checkReuse(observation, request, time);
    if (request != null || observation == null || observation.isEmpty()) return;
    var encoded = observation.getMetadata().get(OccurrenceNegotiation.DATA_KEY);
    if (encoded == null) return;
    var accepted = Utils.Json.parseObject(encoded.toString(), OccurrenceNegotiation.class);
    var source = accepted.effective().source();
    if (source == OccurrenceSchedule.Source.CONTEXT_GEOMETRY
        || source == OccurrenceSchedule.Source.OBSERVER_GEOMETRY) {
      var fallback = geometryDefault(source == OccurrenceSchedule.Source.CONTEXT_GEOMETRY, scope);
      if (!OccurrenceNegotiation.equivalent(accepted.effective(), fallback.schedule(), time))
        throw new KlabValidationException("Geometry default would reschedule an existing occurrence");
      accepted.validate(time);
    }
  }

  static void checkReuse(Observation observation, OccurrenceNegotiation.Request request, Time time) {
    if (request == null || observation == null || observation.isEmpty()) return;
    var stored = observation.getMetadata().get(OccurrenceNegotiation.DATA_KEY);
    if (stored == null) throw new KlabValidationException("Cannot verify schedule of existing process for "
        + request.model() + " dependency " + request.dependency() + "; implicit rescheduling is not supported");
    Utils.Json.parseObject(stored.toString(), OccurrenceNegotiation.class).requireCompatible(request, time);
  }
}
