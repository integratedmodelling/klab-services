package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Use the real DT role-staging path and real wire serialization,
 * replacing only the remote builder result, never the staging/read guards. */
class RoleAttributionTransportRegressionTest {
  @BeforeAll static void configure(){ConsequenceRoleTransactionTest.configure();}
  private void stageWithRealBuilderSerialization(boolean notification) throws Exception {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var validation=(org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl)Notification.info("source validation");
    var lexical=new org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.LexicalContextImpl();
    lexical.setDocumentUrn("test.worldview");lexical.setOffsetInDocument(17);lexical.setLength(9);
    validation.setLexicalContext(lexical);
    if(notification)((ConceptImpl)f.before.getSemantics()).getNotifications().add(validation);
    doAnswer(call->{
      var strategy=call.getArgument(0,ObservableBuildStrategy.class);
      // Same wire mapper used by the semantic-cycle transport, with a real DTO strategy.
      var encoded=Utils.Json.asString(strategy);assertFalse(encoded.isBlank());
      var transported=Utils.Json.parseObject(encoded,ObservableBuildStrategy.class);
      if(notification) {
        var note=transported.getBaseObservable().getSemantics().getNotifications().getFirst();
        assertEquals(validation.getMessage(),note.getMessage());assertEquals(validation.getTimestamp(),note.getTimestamp());
        assertEquals(validation.getLevel(),note.getLevel());
        assertEquals("test.worldview",note.getLexicalContext().getDocumentUrn());
        assertEquals(17,note.getLexicalContext().getOffsetInDocument());assertEquals(9,note.getLexicalContext().getLength());
        ((org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl)note).setMessage("transport copy");
        assertEquals("source validation",validation.getMessage());
      }
      return f.after;
    }).when(f.f.reasoner).buildObservable(any(ObservableBuildStrategy.class),eq(f.f.scope));
    assertEquals(java.util.List.of(41L),f.stage());
    assertSame(f.before,f.recipient.getObservable());
  }
  @Test void ordinaryConcreteRoleBuilderInputMustRemainPortable() throws Exception {
    stageWithRealBuilderSerialization(false);
  }
  @Test void scientificNotificationMustNotBecomeLocalProxyInsideBuilderInput() throws Exception {
    stageWithRealBuilderSerialization(true);
  }
  @Test void notificationChangesStillInvalidateTheGuardedRecipient() throws Exception {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var note=(org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl)Notification.info("before");
    ((ConceptImpl)f.before.getSemantics()).getNotifications().add(note);
    doAnswer(call->{
      assertFalse(Utils.Json.asString(call.getArgument(0,ObservableBuildStrategy.class)).isBlank());
      note.setMessage("changed during preparation");return f.after;
    }).when(f.f.reasoner).buildObservable(any(ObservableBuildStrategy.class),eq(f.f.scope));
    assertThrows(IllegalStateException.class,f::stage);
    assertSame(f.before,f.recipient.getObservable());
    assertFalse(f.f.activity.getMetadata().containsKey("klab.consequence.roleAttributions"));
  }
  @Test void lexicalMetadataChangesStillInvalidateTheGuardedRecipient() throws Exception {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var note=(org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl)Notification.info("unchanged message");
    var lexical=new org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.LexicalContextImpl();
    lexical.setDocumentUrn("before");note.setLexicalContext(lexical);
    ((ConceptImpl)f.before.getSemantics()).getNotifications().add(note);
    doAnswer(call->{
      assertFalse(Utils.Json.asString(call.getArgument(0,ObservableBuildStrategy.class)).isBlank());
      lexical.setDocumentUrn("changed during preparation");return f.after;
    }).when(f.f.reasoner).buildObservable(any(ObservableBuildStrategy.class),eq(f.f.scope));
    assertThrows(IllegalStateException.class,f::stage);
    assertSame(f.before,f.recipient.getObservable());
    assertFalse(f.f.activity.getMetadata().containsKey("klab.consequence.roleAttributions"));
  }
  public static class UnknownNotification extends org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl {}
  @Test void arbitraryNotificationSubclassesDoNotAcquireTransportAuthority() throws Exception {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var note=new UnknownNotification();note.setMessage("unsupported DTO");
    ((ConceptImpl)f.before.getSemantics()).getNotifications().add(note);
    doAnswer(call->{
      Utils.Json.asString(call.getArgument(0,ObservableBuildStrategy.class));return f.after;
    }).when(f.f.reasoner).buildObservable(any(ObservableBuildStrategy.class),eq(f.f.scope));
    assertThrows(IllegalArgumentException.class,f::stage);
    assertSame(f.before,f.recipient.getObservable());
  }
}
