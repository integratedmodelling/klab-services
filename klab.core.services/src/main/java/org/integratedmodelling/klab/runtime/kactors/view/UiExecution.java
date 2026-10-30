package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.lang.Annotation;

/** Immutable invocation-local mount cursor; safe to capture in asynchronous continuations. */
public record UiExecution(ApplicationUiModel model, String mount, String namespace) {
  public UiExecution group(String tag, Map<String, Object> metadata) {
    return model.group(this, tag, metadata);
  }

  public UiExecution action(String name, List<Annotation> annotations) {
    return model.action(this, name, annotations);
  }
}
