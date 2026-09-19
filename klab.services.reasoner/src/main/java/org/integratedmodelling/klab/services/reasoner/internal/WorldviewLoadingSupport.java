package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Local authoring can compile a diagnostic-bearing snapshot without declaring it valid. */
public final class WorldviewLoadingSupport {
  private WorldviewLoadingSupport() {}

  public static boolean loadable(Worldview worldview, boolean local) {
    if (worldview == null || worldview.getOntologies().isEmpty()) return false;
    var root = worldview.getOntologies().getFirst();
    if (root.getDomain() != KimOntology.rootDomain) return false;
    return !worldview.isEmpty() || local;
  }

  public static List<Notification> diagnostics(Worldview worldview) {
    var result = new ArrayList<Notification>();
    if (worldview != null) {
      result.addAll(worldview.getNotifications());
      for (var ontology : worldview.getOntologies()) result.addAll(ontology.getNotifications());
      for (var strategy : worldview.getObservationStrategies()) result.addAll(strategy.getNotifications());
    }
    return result;
  }

  public static String errorSummary(List<Notification> diagnostics) {
    return diagnostics.stream()
        .filter(n -> n.getLevel() == Notification.Level.Error || n.getLevel() == Notification.Level.SystemError)
        .map(n -> {
          var location = n.getLexicalContext();
          return (location == null ? "" : location.getDocumentUrn() + " at "
              + location.getOffsetInDocument() + ": ") + n.getMessage();
        }).distinct().collect(java.util.stream.Collectors.joining("; "));
  }
}
