package org.integratedmodelling.klab.api.identities;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("klab.local.broker.uri")
class LocalFederationBrokerConfigurationTest {
  @Test void defaultRemainsUnchangedAndExplicitOperatorUriIsPreservedWithoutConnecting() {
    var previous = System.getProperty(Federation.LOCAL_BROKER_PROPERTY);
    try {
      System.clearProperty(Federation.LOCAL_BROKER_PROPERTY);
      assertEquals(Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT, Federation.local().getBroker());
      var explicit = "amqp://owned-user:owned-secret@127.0.0.1:38095/closeout.agent";
      System.setProperty(Federation.LOCAL_BROKER_PROPERTY, explicit);
      assertEquals(explicit, Federation.local().getBroker());
      assertEquals(Federation.LOCAL_FEDERATION_ID, Federation.local().getId());
      System.setProperty(Federation.LOCAL_BROKER_PROPERTY, "amqps://owned-user:owned-secret@example.invalid:5671/private");
      assertEquals("amqps://owned-user:owned-secret@example.invalid:5671/private", Federation.local().getBroker());
    } finally {
      if (previous == null) System.clearProperty(Federation.LOCAL_BROKER_PROPERTY);
      else System.setProperty(Federation.LOCAL_BROKER_PROPERTY, previous);
    }
  }

  @Test void malformedOrAmbiguousOperatorConfigurationFailsClosedWithoutCredentialDisclosure() {
    var previous = System.getProperty(Federation.LOCAL_BROKER_PROPERTY);
    try {
      for (var uri : new String[] {"", "http://localhost:38095/private", "amqp://localhost/private",
          "amqp://localhost:0/private", "amqp://localhost:65536/private", "amqp://localhost:38095/",
          "amqp://localhost:38095/private?credential=secret", "amqp://localhost:38095/private#fragment",
          "amqp://user:private secret@localhost:38095/private"}) {
        System.setProperty(Federation.LOCAL_BROKER_PROPERTY, uri);
        var failure = assertThrows(IllegalArgumentException.class, Federation::local);
        assertEquals("Invalid configured local-federation broker URI", failure.getMessage());
        assertNull(failure.getCause());
      }
    } finally {
      if (previous == null) System.clearProperty(Federation.LOCAL_BROKER_PROPERTY);
      else System.setProperty(Federation.LOCAL_BROKER_PROPERTY, previous);
    }
  }
}
