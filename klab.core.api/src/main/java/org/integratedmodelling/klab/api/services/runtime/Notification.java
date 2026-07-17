package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.UIView;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;

public interface Notification extends Serializable {

  enum Mode {
    Silent,
    Normal,
    Verbose
  }

  enum Outcome {
    Success,
    Failure,
    Ignored,
    Pending,
    Cancelled,
    Unknown;
  }

  enum Level {
    Debug(0),
    Info(1),
    Warning(2),
    Error(3),
    SystemError(4);

    public final int severity;

    Level(int severity) {
      this.severity = severity;
    }

    public static Level ofSeverity(int severity) {
      return switch (severity) {
        case 0 -> Debug;
        case 1 -> Info;
        case 2 -> Warning;
        case 3 -> Error;
        case 4 -> SystemError;
        default ->
            throw new KlabIllegalArgumentException(
                "No notification level of severity " + severity + " exists");
      };
    }
  }

  /**
   * If the notification is relative to a document, return the document context to which it
   * pertains.
   */
  interface LexicalContext {

    String getDocumentUrn();

    String getProjectUrn();

    KlabAsset.KnowledgeClass getType();

    KlabAsset.KnowledgeClass getDocumentType();

    int getOffsetInDocument();

    int getLength();

    /**
     * Use when creating notifications that pertain to a lexical element in a document.
     *
     * @param statement
     * @param document
     * @return
     */
    static LexicalContext of(Statement statement, KlabDocument<?> document) {
      var ret = new NotificationImpl.LexicalContextImpl();
      ret.setLength(statement.getLength());
      ret.setOffsetInDocument(statement.getOffsetInDocument());
      ret.setDocumentType(KlabAsset.KnowledgeClass.classify(document.getClass()));
      ret.setDocumentUrn(document.getUrn());
      ret.setProjectUrn(document.getProjectName());
      return ret;
    }
  }

  /**
   * The notifying identity
   *
   * @return
   */
  String getIdentity();

  /**
   * Outcome is used to improve the information content of the notification, and it may be null.
   *
   * @return
   */
  Outcome getOutcome();

  /**
   * This will be the string representation of the silly Java level, which was born before enums
   * existed.
   *
   * @return
   */
  Level getLevel();

  /**
   * System time of notification
   *
   * @return
   */
  long getTimestamp();

  String getMessage();

  //    Type getType();

  Mode getMode();

  /**
   * If the notification is received within a consumer that has a UI, this specified what to do with
   * it w.r.t the user interface.
   *
   * @return
   */
  UIView.Interactivity getInteractivity();

  /**
   * The document context or null.
   *
   * @return
   */
  LexicalContext getLexicalContext();

  static NotificationImpl of(String message, Level level) {
    return new NotificationImpl(message, level);
  }

  static NotificationImpl error(Object... objects) {
    return create(Utils.Collections.flatCollection(Level.Error, objects).toArray());
  }

  static NotificationImpl info(Object... objects) {
    return create(Utils.Collections.flatCollection(Level.Info, objects).toArray());
  }

  static NotificationImpl warning(Object... objects) {
    return create(Utils.Collections.flatCollection(Level.Warning, objects).toArray());
  }

  static NotificationImpl debug(Object... objects) {
    return create(Utils.Collections.flatCollection(Level.Debug, objects).toArray());
  }

  /**
   * Make the best of the passed arguments and create a notification from them.
   *
   * @param objects
   * @return
   */
  public static NotificationImpl create(Object... objects) {

    Level level = Level.Info;
    String message = "No message";
    LexicalContext lexicalContext = null;
    long timestamp = System.currentTimeMillis();
    Mode mode = Mode.Normal;
    UIView.Interactivity interactivity = UIView.Interactivity.BATCH;
    Outcome outcome = null;

    if (objects != null) {
      for (Object o : objects) {
        if (o instanceof Throwable throwable) {
          message = Utils.Exceptions.stackTrace(throwable);
          level = Level.Error;
        } else if (o instanceof String string) {
          message = string;
        } else if (o instanceof UIView.Interactivity inter) {
          interactivity = inter;
        } else if (o instanceof Instant instant) {
          timestamp = instant.toEpochMilli();
        } else if (o instanceof OffsetDateTime date) {
          timestamp = date.toInstant().toEpochMilli();
        } else if (o instanceof Level l) {
          level = l;
        } else if (o instanceof LexicalContext lc) {
          lexicalContext = lc;
        } else if (o instanceof Outcome outcome1) {
          outcome = outcome1;
        } else if (o instanceof Mode mod) {
          mode = mod;
        } /*else if (o instanceof Message.ForwardingPolicy fwp) {
              forwardingPolicy = fwp;
          } */ else if (o instanceof KlabStatement statement) {
          var lc = new NotificationImpl.LexicalContextImpl();
          lc.setLength(statement.getLength());
          lc.setOffsetInDocument(statement.getOffsetInDocument());
          lc.setDocumentUrn(statement.getNamespace());
          lc.setProjectUrn(statement.getProjectName());
          lc.setDocumentType(statement.getDocumentClass());
          lc.setType(KlabAsset.classify(statement));
          lexicalContext = lc;
        }
      }
    }

    var ret = new NotificationImpl(message, level);
    ret.setLexicalContext(lexicalContext);
    ret.setTimestamp(timestamp);
    ret.setMode(mode);
    ret.setOutcome(outcome);
    ret.setInteractivity(interactivity);

    return ret;
  }
}
