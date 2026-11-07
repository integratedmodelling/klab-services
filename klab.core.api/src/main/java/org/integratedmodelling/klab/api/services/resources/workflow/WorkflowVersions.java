package org.integratedmodelling.klab.api.services.resources.workflow;

import java.util.Objects;
import org.integratedmodelling.klab.api.data.Version;

/** Workflow discovery ordering. Numeric versions sort numerically; a release follows its prereleases. */
public final class WorkflowVersions {
  private WorkflowVersions() {}

  public static int compare(String left, String right) {
    if (Objects.equals(left, right)) return 0;
    if (left == null) return -1;
    if (right == null) return 1;
    try {
      var a = Version.create(left); var b = Version.create(right);
      int order = Integer.compare(a.getMajor(), b.getMajor());
      if (order == 0) order = Integer.compare(a.getMinor(), b.getMinor());
      if (order == 0) order = Integer.compare(a.getBuild(), b.getBuild());
      if (order != 0) return order;
      String am = Objects.toString(a.getModifier(), ""), bm = Objects.toString(b.getModifier(), "");
      if (am.isBlank() != bm.isBlank()) return am.isBlank() ? 1 : -1;
      return am.compareToIgnoreCase(bm);
    } catch (IllegalArgumentException invalid) {
      return left.compareToIgnoreCase(right);
    }
  }
}
