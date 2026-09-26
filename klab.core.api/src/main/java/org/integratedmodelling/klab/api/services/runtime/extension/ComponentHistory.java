package org.integratedmodelling.klab.api.services.runtime.extension;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * Persistent history of the events that affected one component in a service registry.
 *
 * <p>This is a typed projection of a component in the generic service {@code info} API. Clients
 * may request the component ID to obtain all versions, or {@code id@version} for one version.
 */
public record ComponentHistory(String componentId, Version version, List<Event> events)
    implements Serializable {

  public ComponentHistory {
    events = events == null ? List.of() : List.copyOf(events);
  }

  /** Component lifecycle and synchronization events retained by the hosting service. */
  public enum EventType {
    REGISTERED,
    UPDATE_AVAILABLE,
    UPDATE_STARTED,
    UPDATED,
    UPDATE_DEFERRED,
    UNLOADED,
    ROLLED_BACK,
    FAILED
  }

  /** Outcome of the operation represented by an event. */
  public enum Outcome {
    SUCCESS,
    INFO,
    WARNING,
    FAILURE,
    PENDING
  }

  /**
   * One immutable history entry. Details are deliberately string-valued so the transport contract
   * remains stable when source-specific diagnostic data is added.
   */
  public record Event(
      String componentId,
      Version componentVersion,
      long timestamp,
      EventType type,
      Outcome outcome,
      Extensions.ComponentImportType importType,
      String sourceServiceId,
      String serviceId,
      KlabService.Type serviceType,
      String message,
      Map<String, String> details)
      implements Serializable {

    public Event {
      details = details == null ? Map.of() : Map.copyOf(details);
    }
  }
}
