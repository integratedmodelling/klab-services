package org.integratedmodelling.klab.services.base;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailManagerTest {
  private final Map<String, Object> values = new LinkedHashMap<>();
  private final Settings settings = mock(Settings.class);
  private final CapturingSender sender = new CapturingSender();
  private final AtomicInteger created = new AtomicInteger();
  private final EmailManager manager;

  EmailManagerTest() {
    Arrays.stream(Setting.values()).filter(s -> s.page == Setting.Page.EMAIL)
        .forEach(s -> values.put(s.name(), s.defaultValue));
    when(settings.asMap()).thenAnswer(invocation -> new LinkedHashMap<>(values));
    manager = new EmailManager(settings, () -> { created.incrementAndGet(); return sender; });
  }

  private void configure() {
    values.put(Setting.EMAIL_ENABLED.name(), true);
    values.put(Setting.EMAIL_SMTP_HOST.name(), "smtp.example.org");
    values.put(Setting.EMAIL_FROM_ADDRESS.name(), "service@example.org");
    values.put(Setting.EMAIL_FROM_NAME.name(), "k.LAB Service");
    values.put(Setting.EMAIL_USERNAME.name(), "service");
    values.put(Setting.EMAIL_PASSWORD.name(), "secret");
  }

  @Test void missingConfigurationNeverCreatesSenderOrThrows() {
    assertFalse(manager.isConfigured());
    assertFalse(manager.send("user@example.org", "Subject", "Body"));
    values.put(Setting.EMAIL_ENABLED.name(), true);
    assertFalse(manager.isConfigured());
    assertTrue(manager.getConfigurationStatus().missingOrInvalidSettings().contains(Setting.EMAIL_SMTP_HOST));
    assertFalse(manager.send("user@example.org", "Subject", "Body"));
    assertEquals(0, created.get());
  }

  @Test void sendsConfiguredUtf8MimeMessageAndRequiredTls() throws Exception {
    configure();
    values.put(Setting.EMAIL_REPLY_TO.name(), "reply@example.org");
    assertTrue(manager.send("user@example.org", "Résumé", "Héllo"));
    assertEquals("Résumé", sender.message.getSubject());
    assertEquals("Héllo", sender.message.getContent());
    assertTrue(sender.message.getFrom()[0].toString().contains("service@example.org"));
    assertEquals("reply@example.org", sender.message.getReplyTo()[0].toString());
    assertEquals("smtp.example.org", sender.getHost());
    assertEquals(587, sender.getPort());
    assertEquals("secret", sender.getPassword());
    assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
    assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
    assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtp.writetimeout"));
  }

  @Test void refreshesSettingsAndSupportsSslUnauthenticatedRelayAndHtml() throws Exception {
    configure();
    assertTrue(manager.send("user@example.org", "Before", "Body"));
    values.put(Setting.EMAIL_SECURITY.name(), "SSL");
    values.put(Setting.EMAIL_SMTP_PORT.name(), 465);
    values.put(Setting.EMAIL_AUTHENTICATION.name(), false);
    values.put(Setting.EMAIL_PASSWORD.name(), "");
    values.put(Setting.EMAIL_FROM_ADDRESS.name(), "changed@example.org");
    values.put(Setting.EMAIL_READ_TIMEOUT_MS.name(), 2500);
    assertTrue(manager.send("user@example.org", "After", "<b>Hello</b>", true));
    assertEquals(465, sender.getPort());
    assertTrue(sender.message.getFrom()[0].toString().contains("changed@example.org"));
    assertTrue(sender.message.getContentType().startsWith("text/html"));
    assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
    assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
    assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
    assertNull(sender.getUsername());
    assertNull(sender.getPassword());
    assertEquals("2500", sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
    values.put(Setting.EMAIL_ENABLED.name(), false);
    assertFalse(manager.send("user@example.org", "Disabled", "Body"));
    assertEquals(2, created.get());
  }

  @Test void distinguishesInvalidConfigurationMessagesAndDeliveryFailure() {
    configure();
    values.put(Setting.EMAIL_FROM_ADDRESS.name(), "invalid");
    assertFalse(manager.isConfigured());
    values.put(Setting.EMAIL_FROM_ADDRESS.name(), "service@example.org");
    assertThrows(KlabIllegalArgumentException.class, () -> manager.send("invalid", "Title", "Body"));
    assertThrows(KlabIllegalArgumentException.class, () -> manager.send("user@example.org", "Title\r\nInjected", "Body"));
    sender.fail = true;
    assertThrows(KlabIOException.class, () -> manager.send("user@example.org", "Title", "Body"));
  }

  @Test void emailSettingsApplyToEveryServiceAndValidateRanges() {
    for (var setting : Setting.values()) {
      if (setting.page == Setting.Page.EMAIL) {
        for (var type : KlabService.Type.values()) assertTrue(setting.appliesTo(type));
      }
    }
    assertFalse(Setting.EMAIL_SMTP_PORT.validate(0));
    assertFalse(Setting.EMAIL_SMTP_PORT.validate(65536));
    assertFalse(Setting.EMAIL_CONNECTION_TIMEOUT_MS.validate(-1));
    assertFalse(Setting.EMAIL_SECURITY.validate("invalid"));
  }

  @Test void deliversThroughSmtpTransportToLocalTestServer() throws Exception {
    configure();
    try (var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(5000);
      var received = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        try (var socket = server.accept()) {
          socket.setSoTimeout(5000);
          var input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
          var output = new java.io.PrintWriter(socket.getOutputStream(), true, java.nio.charset.StandardCharsets.US_ASCII);
          output.print("220 localhost test SMTP\r\n");
          output.flush();
          var data = new StringBuilder();
          boolean inData = false;
          for (String line; (line = input.readLine()) != null; ) {
            if (inData) {
              if (line.equals(".")) {
                inData = false;
                output.print("250 accepted\r\n");
              } else data.append(line).append('\n');
            } else if (line.equals("DATA")) {
              inData = true;
              output.print("354 send message\r\n");
            } else if (line.equals("QUIT")) {
              output.print("221 goodbye\r\n");
              output.flush();
              return data.toString();
            } else output.print("250 OK\r\n");
            output.flush();
          }
          return data.toString();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
      });
      values.put(Setting.EMAIL_SMTP_HOST.name(), server.getInetAddress().getHostAddress());
      values.put(Setting.EMAIL_SMTP_PORT.name(), server.getLocalPort());
      values.put(Setting.EMAIL_AUTHENTICATION.name(), false);
      values.put(Setting.EMAIL_SECURITY.name(), "NONE");
      assertTrue(new EmailManager(settings).send("recipient@example.org", "SMTP transport test", "Test message"));
      var data = received.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(data.contains("service@example.org"));
      assertTrue(data.contains("recipient@example.org"));
      assertTrue(data.contains("SMTP transport test"));
      assertTrue(data.contains("Test message"));
    }
  }

  private static class CapturingSender extends JavaMailSenderImpl {
    MimeMessage message;
    boolean fail;
    @Override public void send(MimeMessage message) {
      if (fail) throw new MailSendException("SMTP unavailable");
      try { message.saveChanges(); } catch (Exception e) { throw new AssertionError(e); }
      this.message = message;
    }
  }
}
