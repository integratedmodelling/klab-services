package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class IngestionNotificationsTest {
  @Test void startupErrorsKeepTheirLexicalContextAndCollectorAlwaysDetaches() {
    var channel = new ChannelImpl(mock(Identity.class)) {
      @Override public String getDispatchId() { return "reasoner"; }
    };
    var diagnostics = new ArrayList<Notification>();
    var statement = new KimConceptStatementImpl();
    statement.setNamespace("biology");
    statement.setOffsetInDocument(107);
    statement.setLength(110);
    assertThrows(IllegalStateException.class, () -> {
      try (var collector = new IngestionNotifications(channel, diagnostics)) {
        channel.error("Authority provider is unavailable: test.authority", statement);
        throw new IllegalStateException("ingestion failed");
      }
    });
    assertEquals(1, diagnostics.size());
    assertEquals(Notification.Level.Error, diagnostics.getFirst().getLevel());
    assertEquals("biology", diagnostics.getFirst().getLexicalContext().getDocumentUrn());
    assertEquals(107, diagnostics.getFirst().getLexicalContext().getOffsetInDocument());
    channel.warn("later message");
    assertEquals(1, diagnostics.size());
  }
}
