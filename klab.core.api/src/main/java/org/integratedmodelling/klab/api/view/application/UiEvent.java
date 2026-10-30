package org.integratedmodelling.klab.api.view.application;

/**
 * Renderer input on an already authenticated, instance-bound channel. Sequence is positive and
 * monotonic for the lifetime of that instance, including reconnects. Value is a JSON value.
 */
public record UiEvent(long sequence, long revision, String nodeId, String name, Object value) {
  public UiEvent {
    if (sequence < 1
        || revision < 1
        || nodeId == null
        || nodeId.isBlank()
        || name == null
        || name.isBlank()) {
      throw new IllegalArgumentException("Invalid UI event envelope");
    }
    value = UiNode.immutableValue(value);
  }
}
