package org.integratedmodelling.klab.api.lang.dataflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.lang.dataflow.DataflowDocument.*;
import org.junit.jupiter.api.Test;

class DataflowDocumentBuilderTest {
  private static ObservationDefinition context() {
    return new ObservationDefinition(
        "region",
        "earth:Terrestrial earth:Region",
        "input:region",
        null,
        "geometry-placeholder",
        null);
  }

  private static Apply call(String output, Value input) {
    return new Apply("test.compute", "1.0.0", List.of(new Argument("input", input)), output);
  }

  private static ObservationStep step(String name, List<Apply> calls) {
    return new ObservationStep(
        name, "geography:Elevation in m", "region", "test.strategy", null, calls);
  }

  private static DataflowDocumentBuilder builder() {
    return new DataflowDocumentBuilder("test.replay", "1.0", Mode.REPLAY).add(context());
  }

  @Test
  void preservesOrderAndDetachesListsAcrossBuilds() {
    var calls = new ArrayList<Apply>();
    calls.add(call("raw", new PortReference("region", null)));
    calls.add(call("normalized", new PortReference("elevation", "raw")));
    var elevation = step("elevation", calls);
    calls.clear();
    var builder = builder().add(elevation);
    var first = builder.build();
    builder.add(new ReferenceStep("external", "input:other", "geography:Slope"));
    assertEquals(
        List.of("region", "elevation"),
        first.declarations().stream().map(Declaration::name).toList());
    assertEquals(2, elevation.computation().size());
    assertEquals(3, builder.build().declarations().size());
    assertThrows(UnsupportedOperationException.class, () -> first.declarations().clear());
  }

  @Test
  void rejectsUnknownAndForwardReferencesAndPorts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(step("elevation", List.of(call("out", new PortReference("later", null)))))
                .add(step("later", List.of()))
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(step("elevation", List.of(call("out", new PortReference("elevation", "out")))))
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(
                    step("elevation", List.of(call("out", new PortReference("region", "missing")))))
                .build());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(
                    new ObservationDefinition(
                        "child", "geography:Elevation", "input:child", "absent", null, null))
                .build());
  }

  @Test
  void rejectsDuplicateSymbolsAndOutputsAndMissingImplementationVersions() {
    assertThrows(IllegalArgumentException.class, () -> builder().add(context()).build());
    var call = call("out", new PortReference("region", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder().add(step("elevation", List.of(call, call))).build());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(step("elevation", List.of(new Apply("test.compute", null, List.of(), "out"))))
                .build());
  }

  @Test
  void rejectsIncompletePayloadAndDuplicateArguments() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(
                    step(
                        "elevation",
                        List.of(call("out", new Payload("resource:input", null, "x/test")))))
                .build());
    var arg = new Argument("input", new Text("value"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            builder()
                .add(
                    step(
                        "elevation",
                        List.of(new Apply("test.compute", "1.0", List.of(arg, arg), "out"))))
                .build());
  }
}
