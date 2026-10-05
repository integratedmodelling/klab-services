package org.integratedmodelling.klab.services.base;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Service-owned SMTP sender. Configuration is read atomically from the settings API on every
 * operation, so completed setting changes apply to the next message without a restart.
 * Construction and configuration checks never connect to an SMTP server.
 */
public class EmailManager {
  private final Settings settings;
  private final Supplier<JavaMailSenderImpl> senderFactory;

  public EmailManager(Settings settings) {
    this(settings, JavaMailSenderImpl::new);
  }

  EmailManager(Settings settings, Supplier<JavaMailSenderImpl> senderFactory) {
    this.settings = Objects.requireNonNull(settings);
    this.senderFactory = Objects.requireNonNull(senderFactory);
  }

  /** Status contains only setting names, never credentials or other configuration values. */
  public record ConfigurationStatus(boolean enabled, List<Setting> missingOrInvalidSettings) {
    public ConfigurationStatus {
      missingOrInvalidSettings = List.copyOf(missingOrInvalidSettings);
    }

    public boolean configured() {
      return enabled && missingOrInvalidSettings.isEmpty();
    }
  }

  public ConfigurationStatus getConfigurationStatus() {
    return status(settings.asMap());
  }

  public boolean isConfigured() {
    return getConfigurationStatus().configured();
  }

  private ConfigurationStatus status(Map<String, Object> values) {
    var invalid = new ArrayList<Setting>();
    for (var setting : Setting.values()) {
      if (setting.page == Setting.Page.EMAIL && !setting.validate(values.get(setting.name()))) {
        invalid.add(setting);
      }
    }
    requireText(values, Setting.EMAIL_SMTP_HOST, invalid);
    requireAddress(values, Setting.EMAIL_FROM_ADDRESS, false, invalid);
    requireAddress(values, Setting.EMAIL_REPLY_TO, true, invalid);
    if (Boolean.TRUE.equals(values.get(Setting.EMAIL_AUTHENTICATION.name()))) {
      requireText(values, Setting.EMAIL_USERNAME, invalid);
      requireText(values, Setting.EMAIL_PASSWORD, invalid);
    }
    return new ConfigurationStatus(
        Boolean.TRUE.equals(values.get(Setting.EMAIL_ENABLED.name())), invalid);
  }

  private static void requireText(Map<String, Object> values, Setting setting, List<Setting> invalid) {
    if (!(values.get(setting.name()) instanceof String text) || text.isBlank()) {
      if (!invalid.contains(setting)) invalid.add(setting);
    }
  }

  private static void requireAddress(
      Map<String, Object> values, Setting setting, boolean optional, List<Setting> invalid) {
    var value = values.get(setting.name());
    if (optional && value instanceof String text && text.isBlank()) return;
    try {
      if (!(value instanceof String text) || text.isBlank()) throw new MessagingException();
      new InternetAddress(text, true).validate();
    } catch (MessagingException e) {
      if (!invalid.contains(setting)) invalid.add(setting);
    }
  }

  /**
   * Send UTF-8 plain text from the configured address. Returns false when disabled or incomplete;
   * configured delivery failures throw {@link KlabIOException}. This method performs blocking I/O.
   */
  public boolean send(String recipient, String subject, String text) {
    return send(recipient, subject, text, false);
  }

  /** Send UTF-8 text or HTML with the same non-configuration and failure semantics as {@link #send}. */
  public boolean send(String recipient, String subject, String body, boolean html) {
    var values = settings.asMap();
    if (!status(values).configured()) return false;
    if (recipient == null || recipient.isBlank() || subject == null || body == null
        || subject.contains("\r") || subject.contains("\n")) {
      throw new KlabIllegalArgumentException("A recipient, single-line subject and body are required");
    }
    var sender = createSender(values);
    var message = sender.createMimeMessage();
    try {
      var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setValidateAddresses(true);
      helper.setFrom(new InternetAddress(
          string(values, Setting.EMAIL_FROM_ADDRESS), string(values, Setting.EMAIL_FROM_NAME),
          StandardCharsets.UTF_8.name()));
      helper.setTo(recipient);
      var replyTo = string(values, Setting.EMAIL_REPLY_TO);
      if (!replyTo.isBlank()) helper.setReplyTo(replyTo);
      helper.setSubject(subject);
      helper.setText(body, html);
    } catch (MessagingException | java.io.UnsupportedEncodingException e) {
      throw new KlabIllegalArgumentException("Invalid email message: " + e.getMessage());
    }
    try {
      sender.send(message);
      return true;
    } catch (MailException e) {
      // Keep SMTP diagnostics (which may contain server/user information) out of the public message.
      throw new KlabIOException("Email delivery failed", e);
    }
  }

  private JavaMailSenderImpl createSender(Map<String, Object> values) {
    var sender = senderFactory.get();
    sender.setHost(string(values, Setting.EMAIL_SMTP_HOST));
    sender.setPort((Integer) values.get(Setting.EMAIL_SMTP_PORT.name()));
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    sender.setProtocol("smtp");
    boolean authentication = Boolean.TRUE.equals(values.get(Setting.EMAIL_AUTHENTICATION.name()));
    sender.setUsername(authentication ? string(values, Setting.EMAIL_USERNAME) : null);
    sender.setPassword(authentication ? string(values, Setting.EMAIL_PASSWORD) : null);
    var properties = sender.getJavaMailProperties();
    properties.setProperty("mail.smtp.auth", Boolean.toString(authentication));
    var security = string(values, Setting.EMAIL_SECURITY);
    properties.setProperty("mail.smtp.starttls.enable", Boolean.toString("STARTTLS".equals(security)));
    properties.setProperty("mail.smtp.starttls.required", Boolean.toString("STARTTLS".equals(security)));
    properties.setProperty("mail.smtp.ssl.enable", Boolean.toString("SSL".equals(security)));
    properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    properties.setProperty("mail.smtp.connectiontimeout", values.get(Setting.EMAIL_CONNECTION_TIMEOUT_MS.name()).toString());
    properties.setProperty("mail.smtp.timeout", values.get(Setting.EMAIL_READ_TIMEOUT_MS.name()).toString());
    properties.setProperty("mail.smtp.writetimeout", values.get(Setting.EMAIL_WRITE_TIMEOUT_MS.name()).toString());
    return sender;
  }

  private static String string(Map<String, Object> values, Setting setting) {
    return (String) values.get(setting.name());
  }
}
