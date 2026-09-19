package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class OccurrenceScheduleTest {
  private static OccurrenceSchedule model(String step) {
    return OccurrenceSchedule.fromModel(List.of(Annotation.of("time", "step", QuantityImpl.parse(step))));
  }

  @Test
  void monthlyQuantityKeepsCalendarResolutionAndWholeContextBounds() {
    var schedule = model("1.month");
    var start = TimeInstant.create(2014, 1, 1);
    var end = TimeInstant.create(2015, 1, 1);
    var bound = schedule.bind(TimePeriod.create(start.getMilliseconds(), end.getMilliseconds()));
    assertEquals(Time.Resolution.Type.MONTH, bound.unit());
    assertEquals(start.getMilliseconds(), bound.anchor());
    assertEquals(start.getMilliseconds(), bound.start());
    assertEquals(end.getMilliseconds(), bound.end());
    // Calendar addition, not the indicative 30-day millisecond span.
    assertEquals(TimeInstant.create(2014, 2, 1).getMilliseconds(),
        start.plus(1, bound.resolution()).getMilliseconds());
  }

  @Test
  void precedenceAndEveryLockInAChainAreValidated() {
    var monthly = model("1.month");
    var javaDaily = new OccurrenceSchedule(1, "", "", 1, Time.Resolution.Type.DAY, true, OccurrenceSchedule.Source.JAVA);
    var locked = new OccurrenceSchedule(1, "", "", 1, Time.Resolution.Type.DAY, false, OccurrenceSchedule.Source.JAVA);
    assertSame(monthly, OccurrenceSchedule.select(monthly, List.of(javaDaily)));
    assertSame(javaDaily, OccurrenceSchedule.select(null, List.of(javaDaily)));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.select(monthly, List.of(javaDaily, locked)));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.select(null, List.of()));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.select(null, List.of(javaDaily, monthly)));
    assertDoesNotThrow(() -> OccurrenceSchedule.select(model("1.day"), List.of(locked)));
  }

  @Test
  void malformedSchedulesFailInsteadOfBecomingStaticOrApproximate() {
    for (String quantity : List.of("0.day", "-1.day", "1.km", "NaN.day", "Infinity.day")) {
      assertThrows(RuntimeException.class, () -> model(quantity), quantity);
    }
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromModel(List.of(Annotation.of("time", "step", 1))));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromModel(List.of(Annotation.of("time"))));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromModel(List.of(
        Annotation.of("time", "step", QuantityImpl.parse("1.day"), "stepp", 2))));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromModel(List.of(Annotation.of("time"), Annotation.of("time"))));
    assertThrows(KlabValidationException.class, () -> new OccurrenceSchedule(1, "", "", 0.5, Time.Resolution.Type.MONTH, true, OccurrenceSchedule.Source.MODEL));
    assertThrows(KlabValidationException.class, () -> new OccurrenceSchedule(1, "", "", Double.MAX_VALUE, Time.Resolution.Type.DAY, true, OccurrenceSchedule.Source.MODEL));
    assertThrows(KlabValidationException.class, () -> new OccurrenceSchedule(1, "2014", "", 1, Time.Resolution.Type.DAY, true, OccurrenceSchedule.Source.MODEL));
    assertThrows(KlabValidationException.class, () -> new OccurrenceSchedule(1, "2015-01-01T00:00:00Z", "2014-01-01T00:00:00Z", 1, Time.Resolution.Type.DAY, true, OccurrenceSchedule.Source.MODEL));
    assertThrows(KlabValidationException.class, () -> model("1.day").bind(null));
  }

  @Test
  void serviceAndActuatorRoundTripsPreserveSchedulesAndLegacyDefaults() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var schedule = model("1.month");
    var service = new ServiceInfoImpl();
    service.setOccurrenceSchedule(schedule);
    var remoteService = mapper.readValue(mapper.writeValueAsString(service), ServiceInfo.class);
    assertEquals(schedule, remoteService.getOccurrenceSchedule());
    var actuator = new ActuatorImpl();
    actuator.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    actuator.getOccurrenceSchedules().put(0, schedule);
    var remote = mapper.readValue(mapper.writeValueAsString(actuator), Actuator.class);
    assertEquals(Actuator.ExecutionRole.PROCESS, remote.getExecutionRole());
    assertEquals(schedule, remote.getOccurrenceSchedules().get(0));
    var legacyJson = (com.fasterxml.jackson.databind.node.ObjectNode)
        mapper.readTree(mapper.writeValueAsString(actuator));
    legacyJson.remove(List.of("executionRole", "occurrenceSchedules"));
    var legacy = mapper.treeToValue(legacyJson, Actuator.class);
    assertEquals(Actuator.ExecutionRole.INITIALIZATION, legacy.getExecutionRole());
    assertTrue(legacy.getOccurrenceSchedules().isEmpty());
  }

  @Test
  void modelAnnotationTransportRetainsQuantityAndBoundsRejectEmptyIntersection() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var annotation = Annotation.of("time", "step", QuantityImpl.parse("1.month"));
    var remote = mapper.readValue(mapper.writeValueAsString(annotation), Annotation.class);
    assertEquals(model("1.month"), OccurrenceSchedule.fromModel(List.of(remote)));
    var schedule = new OccurrenceSchedule(1, "2014-01-01T00:00:00Z", "2015-01-01T00:00:00Z",
        1, Time.Resolution.Type.MONTH, true, OccurrenceSchedule.Source.JAVA);
    assertThrows(KlabValidationException.class, () -> schedule.bind(TimePeriod.create(
        TimeInstant.create(2016).getMilliseconds(), TimeInstant.create(2017).getMilliseconds())));
    var clipped = schedule.bind(TimePeriod.create(TimeInstant.create(2014, 2, 1).getMilliseconds(),
        TimeInstant.create(2016).getMilliseconds()));
    assertEquals(TimeInstant.create(2014, 1, 1).getMilliseconds(), clipped.anchor());
    assertEquals(TimeInstant.create(2015).getMilliseconds(), clipped.end());
  }
}
