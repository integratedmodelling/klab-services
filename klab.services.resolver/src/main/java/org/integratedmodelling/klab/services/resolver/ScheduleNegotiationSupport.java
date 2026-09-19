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
    return OccurrenceNegotiation.select(model.getUrn(), primary.is(SemanticType.PROCESS) ? graph.scheduleRequest : null,
        declarations, time);
  }

  static void checkReuse(Observation observation, OccurrenceNegotiation.Request request, Time time) {
    if (request == null || observation == null || observation.isEmpty()) return;
    var stored = observation.getMetadata().get(OccurrenceNegotiation.DATA_KEY);
    if (stored == null) throw new KlabValidationException("Cannot verify schedule of existing process for "
        + request.model() + " dependency " + request.dependency() + "; implicit rescheduling is not supported");
    Utils.Json.parseObject(stored.toString(), OccurrenceNegotiation.class).requireCompatible(request, time);
  }
}
