package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Server-minted managed configuration identity perspective, preserved across participant revisions.
 */
public record ConfigurationPerspective(String contextId, long observerId, long focusId) {
  public static final String METADATA = "klab.configuration.perspective";

  public ConfigurationPerspective {
    if (contextId == null || contextId.isBlank() || observerId < 0 || focusId < 0)
      throw new IllegalArgumentException("Durable configuration perspective required");
  }

  public static ConfigurationPerspective capture(ContextScope scope) {
    Objects.requireNonNull(scope);
    Long selected = scope.getObserverId();
    long observer = selected == null ? 0 : selected;
    if (observer != 0) {
      var agent = scope.getObserver();
      if (observer < 0
          || agent == null
          || agent.getId() != observer
          || agent.getObservable() == null
          || !agent.getObservable().is(SemanticType.AGENT))
        throw new IllegalStateException(
            "Selected configuration observer is not an accessible durable Agent");
    }
    var focus = scope.getContextObservation();
    if (focus != null && focus.getId() <= 0)
      throw new IllegalStateException(
          "Configuration perspective requires its durable captured focus");
    return new ConfigurationPerspective(scope.getId(), observer, focus == null ? 0 : focus.getId());
  }

  public static ConfigurationPerspective read(Observation configuration) {
    var encoded = configuration.getMetadata().get(METADATA);
    if (!(encoded instanceof String text) || text.isBlank())
      throw new IllegalStateException("Managed configuration has no canonical perspective receipt");
    try {
      var receipt = Utils.Json.parseObject(text, ConfigurationPerspective.class);
      if (receipt == null) throw new IllegalArgumentException("Missing perspective");
      // Validate even if a transport implementation ever bypasses the record constructor.
      return new ConfigurationPerspective(
          receipt.contextId(), receipt.observerId(), receipt.focusId());
    } catch (RuntimeException invalid) {
      throw new IllegalStateException(
          "Managed configuration has a malformed perspective receipt", invalid);
    }
  }
}
