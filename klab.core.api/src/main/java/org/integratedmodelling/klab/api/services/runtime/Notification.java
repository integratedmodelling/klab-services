package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;

import java.awt.event.WindowStateListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;

public interface Notification {

    /**
     * Additional classification info. Can be used for display or other purposes. Will be filled as things
     * progress.
     *
     * @author ferdinando.villa
     */
    public enum Type {
        None, Success, Failure
    }

    public enum Mode {
        Silent, Normal, Verbose
    }

    public enum Level {
        Debug, Info, Warning, Error, SystemError
    }

    /**
     * If the notification is relative to a document, return the document context to which it pertains.
     */
    public interface LexicalContext {
        URL getUrl();

        int getOffsetInDocument();

        int getLength();
    }

    /**
     * The notifying identity
     *
     * @return
     */
    String getIdentity();

    /**
     * This will be the string representation of the silly Java level, which was born before enums existed.
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

    Type getType();

    /**
     * The document context or null.
     *
     * @return
     */
    LexicalContext getLexicalContext();

    public static Notification of(String message, Level level) {
        return new NotificationImpl(message, level);
    }

    /**
     * Make the best of the passed arguments and create a notification from them.
     *
     * @param objects
     * @return
     */
    public static Notification create(Object... objects) {
        Level level = Level.Info;
        String message = "No message";
        LexicalContext lexicalContext = null;
        long timestamp = System.currentTimeMillis();

        if (objects != null) {
            for (Object o : objects) {
                if (o instanceof Throwable throwable) {
                    var writer = new StringWriter();
                    throwable.printStackTrace(new PrintWriter(writer));
                    message = writer.toString();
                    level = Level.Error;
                } else if (o instanceof String string) {
                    message = string;
                } else if (o instanceof Instant instant) {
                    timestamp = instant.toEpochMilli();
                } else if (o instanceof OffsetDateTime date) {
                    timestamp = date.toInstant().toEpochMilli();
                } else if (o instanceof Level l) {
                    level = l;
                } else if (o instanceof LexicalContext lc) {
                    lexicalContext = lc;
                }
            }
        }

        var ret = new NotificationImpl(message, level);
        ret.setLexicalContext(lexicalContext);
        ret.setTimestamp(timestamp);

        return ret;
    }
}