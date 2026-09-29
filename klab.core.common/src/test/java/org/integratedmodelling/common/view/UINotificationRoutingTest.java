package org.integratedmodelling.common.view;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.function.BiConsumer;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UIView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UINotificationRoutingTest {
  private AbstractUIController controller(UIView view) {
    return mock(AbstractUIController.class,
        withSettings().useConstructor(view).defaultAnswer(CALLS_REAL_METHODS));
  }

  @Test
  void authenticatedNotificationListenerDeliversServiceAndContextMessagesToTheView() {
    var view = mock(UIView.class);
    var controller = controller(view);
    var engine = mock(Engine.class);
    var user = mock(UserScope.class);
    doReturn(engine).when(controller).createEngine();
    when(engine.authenticate()).thenReturn(user);
    controller.authenticate();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<BiConsumer<Channel, Message>> listener =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(user).onMessage(listener.capture(),
        eq(Message.Queue.Info), eq(Message.Queue.Errors), eq(Message.Queue.Warnings));

    var synchronizing = Notification.info("Synchronizing component klab.component.generators");
    listener.getValue().accept(user, Message.create("runtime-service", synchronizing));
    var warning = Notification.warning("Component download is slow");
    listener.getValue().accept(mock(ContextScope.class),
        Message.create("another-context", warning));
    var failure = Notification.error("Component installation failed");
    listener.getValue().accept(user, Message.create("runtime-service", failure));

    verify(view).handleNotification(synchronizing);
    verify(view).handleNotification(warning);
    verify(view).handleNotification(failure);
    verifyNoMoreInteractions(view);
  }

  @Test
  void silentAndNonNotificationMessagesAreNotDisplayed() {
    var view = mock(UIView.class);
    var controller = controller(view);
    controller.processNotification(null,
        Message.create("service", Notification.info("Internal detail", Notification.Mode.Silent)));
    controller.processNotification(null,
        Message.create("context", Message.MessageClass.DigitalTwin,
            Message.MessageType.ObservationSubmissionStarted));
    controller.processNotification(null, null);
    verifyNoInteractions(view);
  }

  @Test
  void headlessControllerAcceptsNotificationsWithoutAView() {
    controller(null).processNotification(null,
        Message.create("service", Notification.info("Synchronizing component")));
  }
}
