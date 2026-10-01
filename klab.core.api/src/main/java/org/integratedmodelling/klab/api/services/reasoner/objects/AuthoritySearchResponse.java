package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** OK with an empty matches list is distinct from unsupported, unavailable and failed searches.
 * total counts the provider-returned candidate list, not its underlying catalog. nextOffset is -1
 * at the end of that list. Documentation is obtained through the authenticated documentation API. */
public record AuthoritySearchResponse(Status status, List<AuthorityIdentity> matches,
    int total, int nextOffset, List<Notification> notifications) {
  public enum Status { OK, UNSUPPORTED, UNAVAILABLE, FAILED }
  public AuthoritySearchResponse {
    matches = matches == null ? List.of() : List.copyOf(matches);
    notifications = notifications == null ? List.of() : List.copyOf(notifications);
  }
  public static AuthoritySearchResponse failure(Status status, String message) {
    return new AuthoritySearchResponse(status, List.of(), 0, -1, List.of(Notification.error(message)));
  }
}
