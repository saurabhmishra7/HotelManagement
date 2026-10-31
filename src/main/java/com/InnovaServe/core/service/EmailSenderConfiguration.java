package com.InnovaServe.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderConfiguration {
  @Bean
  EmailSender emailSender(
      @Value("${app.mail.provider}") String provider,
      @Value("${app.mail.from}") String from,
      @Value("${app.mail.resend-api-key}") String resendApiKey,
      @Value("${app.mail.dev-recipient:}") String devRecipient,
      @Value("${spring.mail.host:}") String smtpHost,
      ObjectProvider<JavaMailSender> smtpSenders,
      ObjectMapper mapper,
      Environment environment) {
    boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
    boolean stagingProfile = environment.acceptsProfiles(Profiles.of("staging"));
    boolean hasDevRecipient = devRecipient != null && !devRecipient.isBlank();
    boolean devResend = !environment.acceptsProfiles(Profiles.of("prod"))
        && "resend".equalsIgnoreCase(provider.trim())
        && (devProfile || (stagingProfile && hasDevRecipient));
    EmailSender sender = switch (provider.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "resend" -> new ResendEmailSender(
          devResend ? "InnovaServe <onboarding@resend.dev>" : from, resendApiKey, mapper);
      case "smtp" -> new SmtpEmailSender(
          from == null || from.isBlank() ? "no-reply@innova.example" : from,
          smtpHost, smtpSenders);
      default -> throw new IllegalArgumentException("Unsupported app.mail.provider: " + provider);
    };
    if (!devResend) return sender;
    if (!hasDevRecipient)
      throw new IllegalStateException("Set APP_MAIL_DEV_RECIPIENT to your Resend account email for the dev profile");
    return new DevResendEmailSender(sender, devRecipient.trim());
  }

  private record DevResendEmailSender(EmailSender sender, String recipient) implements EmailSender {
    @Override public boolean isAvailable() { return sender.isAvailable(); }

    @Override public void send(String intendedRecipient, String subject, String text) {
      sender.send(recipient, "[DEV] " + subject,
          "Intended recipient: " + intendedRecipient + "\n\n" + text);
    }
  }

  private record SmtpEmailSender(String from, String host,
      ObjectProvider<JavaMailSender> senders) implements EmailSender {
    @Override public boolean isAvailable() {
      return from != null && !from.isBlank() && host != null && !host.isBlank()
          && senders.getIfAvailable() != null;
    }

    @Override public void send(String recipient, String subject, String text) {
      if (!isAvailable()) throw new EmailDeliveryException("SMTP email is not configured");
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(recipient);
      message.setSubject(subject);
      message.setText(text);
      try {
        senders.getObject().send(message);
      } catch (MailException exception) {
        throw new EmailDeliveryException("SMTP email delivery failed", exception);
      }
    }
  }

  private static final class ResendEmailSender implements EmailSender {
    private final String from;
    private final String apiKey;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private ResendEmailSender(String from, String apiKey, ObjectMapper mapper) {
      this.from = from;
      this.apiKey = apiKey;
      this.mapper = mapper;
    }

    @Override public boolean isAvailable() {
      return from != null && !from.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override public void send(String recipient, String subject, String text) {
      if (!isAvailable()) throw new EmailDeliveryException("Resend email is not configured");
      try {
        String body = mapper.writeValueAsString(Map.of(
            "from", from, "to", List.of(recipient), "subject", subject, "text", text));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw new EmailDeliveryException("Resend rejected email (HTTP " + response.statusCode() + ")");
      } catch (EmailDeliveryException exception) {
        throw exception;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new EmailDeliveryException("Resend email delivery interrupted", exception);
      } catch (Exception exception) {
        throw new EmailDeliveryException("Resend email delivery failed", exception);
      }
    }
  }
}
