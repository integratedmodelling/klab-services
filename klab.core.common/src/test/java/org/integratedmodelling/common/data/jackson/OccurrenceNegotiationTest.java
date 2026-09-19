package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.*;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.runtime.extension.Contextualizer;
import org.junit.jupiter.api.Test;

class OccurrenceNegotiationTest {
  static OccurrenceSchedule model(String step, String min, String max, boolean override) {
    var a = Annotation.of("time", "step", QuantityImpl.parse(step), "overridable", override);
    if (min != null) a.put("minStep", QuantityImpl.parse(min));
    if (max != null) a.put("maxStep", QuantityImpl.parse(max));
    return OccurrenceSchedule.fromModel(List.of(a));
  }
  static OccurrenceNegotiation.Request request(String step) {
    return new OccurrenceNegotiation.Request("regionModel", "erosion", "test:Erosion",
        OccurrenceSchedule.fromDependency(List.of(Annotation.of("time", "step", QuantityImpl.parse(step)))));
  }
  static Time context() {
    return TimePeriod.create(TimeInstant.create(2024).getMilliseconds(), TimeInstant.create(2025).getMilliseconds());
  }
  static OccurrenceNegotiation choose(OccurrenceNegotiation.Request r, OccurrenceSchedule... limits) {
    return OccurrenceNegotiation.select("erosionModel", r,
        java.util.Arrays.stream(limits).map(s -> new OccurrenceNegotiation.Declaration(s.source().name(), s)).toList(), context());
  }

  @Test void precedenceInclusiveRangesAndModelLocks() {
    var m = model("1.month", "1.day", "1.month", true);
    assertEquals(m, choose(null, m).effective());
    assertEquals(Time.Resolution.Type.DAY, choose(request("1.day"), m).effective().unit());
    assertDoesNotThrow(() -> choose(request("1.month"), m));
    assertThrows(KlabValidationException.class, () -> choose(request("1.hour"), m));
    assertThrows(KlabValidationException.class, () -> choose(request("2.month"), m));
    assertThrows(KlabValidationException.class, () -> choose(request("1.day"), model("1.month", "1.day", "1.month", false)));
    assertDoesNotThrow(() -> choose(request("24.hour"), model("1.day", null, null, false)));
    assertThrows(KlabValidationException.class, () -> choose(request("1.day")));
  }

  @Contextualizer(timeStep=1, timeUnit=Time.Resolution.Type.DAY,
      timeMinStep=12, timeMinStepUnit=Time.Resolution.Type.HOUR,
      timeMaxStep=2, timeMaxStepUnit=Time.Resolution.Type.DAY)
  static class Flexible {}
  @Contextualizer(timeStep=1, timeUnit=Time.Resolution.Type.DAY, timeOverridable=false)
  static class Locked {}

  @Test void javaRangesAndLocksSurviveEveryHigherPrecedenceSource() throws Exception {
    var java = OccurrenceSchedule.fromJava(Flexible.class.getAnnotation(Contextualizer.class));
    var m = model("1.day", "1.hour", "3.day", true);
    assertDoesNotThrow(() -> choose(request("12.hour"), m, java));
    assertThrows(KlabValidationException.class, () -> choose(request("3.hour"), m, java));
    assertThrows(KlabValidationException.class, () -> choose(request("3.day"), m, java));
    var lock = OccurrenceSchedule.fromJava(Locked.class.getAnnotation(Contextualizer.class));
    assertThrows(KlabValidationException.class, () -> choose(request("2.day"), m, lock, java));
    assertDoesNotThrow(() -> choose(request("24.hour"), m, lock, java));
    var service = new ServiceInfoImpl(); service.setOccurrenceSchedule(java);
    var mapper = JacksonConfiguration.newObjectMapper();
    assertEquals(java, mapper.readValue(mapper.writeValueAsString(service), ServiceInfo.class).getOccurrenceSchedule());
  }

  @Test void calendarComparisonsCheckAllAnchorsAndUnclippedLastStep() {
    // A month is at least 29 days in leap-year February, but not at least 30 days.
    assertDoesNotThrow(() -> choose(request("1.month"), model("1.month", "29.day", "31.day", true)));
    assertThrows(KlabValidationException.class, () -> choose(request("1.month"), model("1.month", "30.day", "31.day", true)));
    var feb = TimePeriod.create(TimeInstant.create(2024, 2, 1).getMilliseconds(), TimeInstant.create(2024, 2, 2).getMilliseconds());
    var declaration = model("1.day", null, "2.day", true);
    assertThrows(KlabValidationException.class, () -> OccurrenceNegotiation.select("m", request("1.month"),
        List.of(new OccurrenceNegotiation.Declaration("m", declaration)), feb));
    assertFalse(OccurrenceNegotiation.equivalent(request("1.month").schedule(), request("30.day").schedule(), context()));
  }

  @Test void invalidDeclarationsAndRangeWideningAreRejected() {
    assertThrows(KlabValidationException.class, () -> model("1.day", "0.day", null, true));
    assertThrows(KlabValidationException.class, () -> choose(null, model("1.day", "2.day", "1.day", true)));
    var a = Annotation.of("time", "step", QuantityImpl.parse("1.day"), "minStep", QuantityImpl.parse("1.hour"));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromDependency(List.of(a)));
    assertThrows(KlabValidationException.class, () -> OccurrenceSchedule.fromModel(List.of(a, a)));
  }

  @Test void wholeScheduleReplacementAndMonthEndBounds() {
    var bounded = OccurrenceSchedule.fromModel(List.of(Annotation.of("time", "step", QuantityImpl.parse("1.month"),
        "start", "2024-02-01T00:00:00Z", "end", "2024-03-01T00:00:00Z")));
    var replacement = choose(request("1.day"), bounded).effective().bind(context());
    assertEquals(context().getStart().getMilliseconds(), replacement.start());
    assertEquals(context().getEnd().getMilliseconds(), replacement.end());
    var locked = new OccurrenceSchedule(2, bounded.start(), bounded.end(), bounded.step(), bounded.unit(),
        false, bounded.source());
    assertThrows(KlabValidationException.class, () -> choose(request("1.month"), locked));
    var monthEnd = TimePeriod.create(TimeInstant.create(2024, 1, 31).getMilliseconds(),
        TimeInstant.create(2024, 4, 1).getMilliseconds());
    var allowed = model("1.month", "29.day", "31.day", true);
    assertDoesNotThrow(() -> OccurrenceNegotiation.select("m", request("1.month"),
        List.of(new OccurrenceNegotiation.Declaration("m", allowed)), monthEnd));
    var disjoint = OccurrenceSchedule.fromJava(Flexible.class.getAnnotation(Contextualizer.class));
    assertThrows(KlabValidationException.class, () -> choose(request("1.day"),
        model("3.day", "3.day", "4.day", true), disjoint));
  }

  @Test void acceptedEvidenceRoundTripsAndExistingRegistrationsCannotBeRescheduled() throws Exception {
    var accepted = choose(request("1.day"), model("1.month", "1.day", "1.month", true));
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(accepted), OccurrenceNegotiation.class);
    assertEquals(accepted, restored);
    assertDoesNotThrow(() -> restored.requireCompatible(request("24.hour"), context()));
    assertThrows(KlabValidationException.class, () -> restored.requireCompatible(request("1.month"), context()));
    var legacy = new OccurrenceSchedule(1, "", "", 1, Time.Resolution.Type.DAY, true, OccurrenceSchedule.Source.JAVA);
    assertEquals(legacy, mapper.readValue(mapper.writeValueAsString(legacy).replace(",\"minStep\":null", "").replace(",\"maxStep\":null", ""), OccurrenceSchedule.class));
  }
}
