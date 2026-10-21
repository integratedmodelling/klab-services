/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.common.logging;

import java.util.function.Consumer;
import java.util.logging.Level;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging functions. Most of the actual logging happens through the {@link
 * org.integratedmodelling.klab.api.services.runtime.Channel} mechanism, so this is only used
 * explicitly in services and the like.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public enum Logging {
  INSTANCE;

  private Logger logger;
  private String systemIdentifier = "";
  private Identity rootIdentity;

  Consumer<String> infoWriter = (message) -> System.out.println("INFO: " + message);
  Consumer<String> warningWriter = (message) -> System.err.println("WARN: " + message);
  Consumer<String> errorWriter = (message) -> System.err.println("ERROR: " + message);
  Consumer<String> debugWriter = (message) -> System.err.println("DEBUG: " + message);

  Logging() {
    try {
      logger = (Logger) LoggerFactory.getLogger(this.getClass());
    } catch (Throwable e) {
      System.err.println(
          "--------------------------------------------------------------------------------------------------");
      System.err.println(
          "Error initializing logger: please spend the rest of your life checking dependencies "
              + "and excluding jars");
      System.err.println(
          "--------------------------------------------------------------------------------------------------");
    }
  }

  public void info(Object... o) {

    Notification payload = Notification.create(o);

    if (payload.getMode() == Notification.Mode.Silent) {
      return;
    }

    if (Configuration.INSTANCE.getLoggingLevel().intValue() >= Level.INFO.intValue()) {
      if (infoWriter != null) {
        infoWriter.accept(systemIdentifier + payload.getMessage());
      }
      if (logger != null) {
        logger.info(systemIdentifier + payload.getMessage());
      }
    }
  }

  public void warn(Object... o) {
    Notification payload = Notification.warning(o);
    if (org.integratedmodelling.klab.api.lang.NotificationSuppression.suppresses(payload.getLevel(),o)) return;

    if (payload.getMode() == Notification.Mode.Silent) {
      return;
    }

    if (Configuration.INSTANCE.getLoggingLevel().intValue() >= Level.WARNING.intValue()) {
      if (warningWriter != null) {
        warningWriter.accept(systemIdentifier + payload.getMessage());
      }
      if (logger != null) {
        logger.warn(systemIdentifier + payload.getMessage());
      }
    }
  }

  public void error(Object... o) {

    Object[] safe = o.clone();
    for (int i = 0; i < safe.length; i++) {
      if (safe[i] instanceof Throwable failure) safe[i] = safeFailure(failure);
    }
    Notification payload = Notification.create(safe);

    if (payload.getMode() == Notification.Mode.Silent) {
      return;
    }

    if (Configuration.INSTANCE.getNotificationLevel().intValue() <= Level.SEVERE.intValue()) {
      if (errorWriter != null) {
        errorWriter.accept(systemIdentifier + payload.getMessage()
            + (payload.getStackTrace() == null ? "" : "\n" + payload.getStackTrace()));
      }
      if (logger != null) {
        logger.error(systemIdentifier + payload.getMessage()
            + (payload.getStackTrace() == null ? "" : "\n" + payload.getStackTrace()));
      }
    }
  }

  /** Snapshot diagnostic types, frames and causes without retaining payload-bearing messages. */
  public static Throwable safeFailure(Throwable failure) {
    return failure instanceof SafeFailure ? failure : snapshot(failure, new java.util.IdentityHashMap<>(), 0);
  }

  private static final class SafeFailure extends RuntimeException {
    SafeFailure(String type) { super(type + ": message redacted"); }
  }

  private static Throwable snapshot(Throwable failure,
      java.util.IdentityHashMap<Throwable, Throwable> seen, int depth) {
    if (failure == null || depth >= 16 || seen.size() >= 128) return null;
    if (seen.containsKey(failure)) return null;
    Throwable safe = new SafeFailure(failure.getClass().getName());
    seen.put(failure, safe);
    var frames = failure.getStackTrace();
    safe.setStackTrace(java.util.Arrays.copyOf(frames, Math.min(frames.length, 128)));
    var cause = snapshot(failure.getCause(), seen, depth + 1);
    if (cause != null) safe.initCause(cause);
    var suppressed = failure.getSuppressed();
    for (int i = 0; i < Math.min(suppressed.length, 16); i++) {
      var child = snapshot(suppressed[i], seen, depth + 1);
      if (child != null) safe.addSuppressed(child);
    }
    return safe;
  }

  public void debug(Object... o) {

    Notification payload = Notification.create(o);

    if (payload.getMode() == Notification.Mode.Silent) {
      return;
    }

    if (Configuration.INSTANCE.getNotificationLevel().intValue() <= Level.FINE.intValue()) {
      if (debugWriter != null) {
        debugWriter.accept(systemIdentifier + payload.getMessage());
      }
      if (logger != null) {
        logger.debug(systemIdentifier + payload.getMessage());
      }
    }
  }

  public void setRootIdentity(Identity identity) {
    this.rootIdentity = identity;
  }

  public Consumer<String> getInfoWriter() {
    return infoWriter;
  }

  public void setInfoWriter(Consumer<String> infoWriter) {
    this.infoWriter = infoWriter;
  }

  public Consumer<String> getWarningWriter() {
    return warningWriter;
  }

  public void setWarningWriter(Consumer<String> warningWriter) {
    this.warningWriter = warningWriter;
  }

  public Consumer<String> getErrorWriter() {
    return errorWriter;
  }

  public void setErrorWriter(Consumer<String> errorWriter) {
    this.errorWriter = errorWriter;
  }

  public Consumer<String> getDebugWriter() {
    return debugWriter;
  }

  public void setDebugWriter(Consumer<String> debugWriter) {
    this.debugWriter = debugWriter;
  }

  public void setSystemIdentifier(String systemIdentifier) {
    this.systemIdentifier = systemIdentifier;
  }

  public void notifications(Notification... notifications) {
    if (notifications != null) {
      for (Notification notification : notifications) {
        if (notification.getMode() != Notification.Mode.Silent) {
          switch (notification.getLevel()) {
            case Warning -> warn(notification.getMessage());
            case Error -> error(notification.getMessage());
            case Info -> info(notification.getMessage());
            case Debug -> debug(notification.getMessage());
          }
        }
      }
    }
  }
}
