package org.integratedmodelling.klab.services.base;

import java.util.Objects;

/** Explicit opt-in adapter; construction performs no I/O and never changes email settings. */
public final class ReviewEmailTransport implements ReviewNotificationOutbox.Transport {
  private final EmailManager email;

  public ReviewEmailTransport(EmailManager email) { this.email = Objects.requireNonNull(email); }

  @Override public ReviewNotificationOutbox.Result send(String mailbox, String subject, String body) {
    try {
      return email.send(mailbox, subject, body)
          ? ReviewNotificationOutbox.Result.ACCEPTED : ReviewNotificationOutbox.Result.DISABLED;
    } catch (RuntimeException e) {
      // EmailManager cannot distinguish pre-acceptance failure from lost SMTP acknowledgement.
      // Never auto-retry it: the server may already have accepted the message.
      return ReviewNotificationOutbox.Result.UNCERTAIN;
    }
  }
}
