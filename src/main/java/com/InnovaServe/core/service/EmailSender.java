package com.InnovaServe.core.service;

/** Provider-neutral delivery for transactional email. */
public interface EmailSender {
  boolean isAvailable();

  void send(String recipient, String subject, String text);
}
