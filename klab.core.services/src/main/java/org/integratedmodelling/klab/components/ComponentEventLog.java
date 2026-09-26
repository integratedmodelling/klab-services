package org.integratedmodelling.klab.components;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.runtime.extension.ComponentHistory;
import org.integratedmodelling.klab.utilities.Utils;

/** Append-only, restart-safe component event store. One JSON object is written per line. */
final class ComponentEventLog {

  private final File eventFile;

  ComponentEventLog(File eventFile) {
    this.eventFile = eventFile;
  }

  synchronized void append(ComponentHistory.Event event) {
    if (eventFile == null || event == null) {
      return;
    }
    try {
      var parent = eventFile.toPath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(
          eventFile.toPath(),
          Utils.Json.asString(event) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException e) {
      // Component operations must not fail because their audit trail cannot be written.
      Logging.INSTANCE.error("Unable to persist component history in " + eventFile, e);
    }
  }

  synchronized ComponentHistory history(String componentId, Version version) {
    var events = new ArrayList<ComponentHistory.Event>();
    if (eventFile != null && eventFile.isFile()) {
      var mapper = Utils.Json.newObjectMapper();
      try (var lines = Files.lines(eventFile.toPath(), StandardCharsets.UTF_8)) {
        lines
            .filter(line -> !line.isBlank())
            .forEach(
                line -> {
                  try {
                    var event = mapper.readValue(line, ComponentHistory.Event.class);
                    if (Objects.equals(componentId, event.componentId())
                        && (version == null
                            || Version.isAny(version)
                            || Objects.equals(version, event.componentVersion()))) {
                      events.add(event);
                    }
                  } catch (Exception malformedEntry) {
                    Logging.INSTANCE.warn(
                        "Ignoring malformed component history entry in " + eventFile,
                        malformedEntry);
                  }
                });
      } catch (IOException e) {
        Logging.INSTANCE.error("Unable to read component history from " + eventFile, e);
      }
    }
    events.sort(Comparator.comparingLong(ComponentHistory.Event::timestamp).reversed());
    return new ComponentHistory(
        componentId, version == null || Version.isAny(version) ? null : version, List.copyOf(events));
  }
}
