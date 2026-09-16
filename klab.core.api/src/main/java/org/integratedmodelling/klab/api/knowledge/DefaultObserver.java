package org.integratedmodelling.klab.api.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;

/** Shared metadata selection and stable identity contract for default user observers. */
public final class DefaultObserver {
  /** Marks connection-created agents; explicit user submissions remove this marker. */
  public static final String AUTOMATIC = "klab.observer.automatic";

  public static final String EXPLICIT = "klab.observer.explicit";

  private DefaultObserver() {}

  /** Blank declarations do not override an inherited value. Invalid declarations are ignored. */
  public static String semantics(Object value, Consumer<String> warning) {
    if (value == null) return null;
    if (!(value instanceof String text)) {
      warning.accept("Ignoring non-text default observer semantics: " + value);
      return null;
    }
    return text.isBlank() ? null : text.trim();
  }

  /**
   * Merge project metadata in caller-defined order, warning before replacing observer semantics.
   */
  public static void mergeMetadata(
      Map<String, Object> target,
      Map<String, ?> source,
      String contributor,
      Consumer<String> warning) {
    if (source == null) return;
    source.forEach(
        (key, value) -> {
          if (Worldview.USER_OBSERVER_SEMANTICS.equals(key)) {
            String next = semantics(value, warning);
            if (next == null) return;
            String previous = semantics(target.get(key), warning);
            if (previous != null && !previous.equals(next)) {
              warning.accept(
                  "Conflicting default observer semantics: "
                      + contributor
                      + " replaces '"
                      + previous
                      + "' with '"
                      + next
                      + "'");
            }
            target.put(key, next);
          } else {
            target.put(key, value);
          }
        });
  }

  /** Groups override worldview defaults; lexically last group (ID, name, semantics) wins ties. */
  public static String select(
      Map<String, ?> worldviewMetadata,
      Collection<? extends Group> groups,
      Consumer<String> warning) {
    String selected =
        semantics(
            worldviewMetadata == null
                ? null
                : worldviewMetadata.get(Worldview.USER_OBSERVER_SEMANTICS),
            warning);
    String groupSelection = null;
    if (groups != null) {
      var ordered =
          groups.stream()
              .filter(Objects::nonNull)
              .sorted(
                  Comparator.comparing((Group g) -> Objects.toString(g.getId(), ""))
                      .thenComparing(g -> Objects.toString(g.getName(), ""))
                      .thenComparing(g -> Objects.toString(g.getObserverSemantics(), "")))
              .toList();
      for (Group group : ordered) {
        String next = semantics(group.getObserverSemantics(), warning);
        if (next == null) continue;
        if (groupSelection != null && !groupSelection.equals(next)) {
          warning.accept(
              "Conflicting group observer semantics: group "
                  + group.getName()
                  + " replaces '"
                  + groupSelection
                  + "' with '"
                  + next
                  + "'");
        }
        groupSelection = next;
      }
    }
    return groupSelection == null ? selected : groupSelection;
  }

  /** Stable two-part substantial identity; independent of session, twin and selected semantics. */
  public static String identity(UserIdentity user) {
    Objects.requireNonNull(user, "user");
    if (user.isAnonymous() || user.getUsername() == null || user.getUsername().isBlank()) {
      throw new IllegalArgumentException("A default observer requires an authenticated username");
    }
    String authority = Objects.toString(user.getServerURL(), "");
    String key = authority.length() + ":" + authority + user.getUsername();
    return "klab.user:" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }
}
