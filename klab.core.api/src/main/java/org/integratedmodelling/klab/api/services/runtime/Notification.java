package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils;

import java.awt.event.WindowStateListener;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

public interface Notification extends Serializable {

//    /**
//     * Additional classification info. Can be used for display or other purposes. Will be filled as things
//     * progress.
//     *
//     * @author ferdinando.villa
//     */
//    public enum Type {
//        None, Success, Failure
//    }

    enum Mode {
        Silent, Normal, Verbose
    }

    enum Level {
        Debug, Info, Warning, Error, SystemError
    }

    /**
     * If the notification is relative to a document, return the document context to which it pertains.
     */
    interface LexicalContext {

        String getDocumentUrn();

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

//    Type getType();

    Mode getMode();

    /**
     * Notifications build with Forward policy will be sent to paired scopes through websockets.
     *
     * @return
     */
    Message.ForwardingPolicy getForwardingPolicy();

    /**
     * The document context or null.
     *
     * @return
     */
    LexicalContext getLexicalContext();

    public static Notification of(String message, Level level) {
        return new NotificationImpl(message, level);
    }

    public static Notification error(Object... objects) {
        return create(Utils.Collections.flatCollection(Level.Error, objects).toArray());
    }

    public static Notification info(Object... objects) {
        return create(Utils.Collections.flatCollection(Level.Info, objects).toArray());
    }

    public static Notification warning(Object... objects) {
        return create(Utils.Collections.flatCollection(Level.Warning, objects).toArray());
    }

    public static Notification debug(Object... objects) {
        return create(Utils.Collections.flatCollection(Level.Debug, objects).toArray());
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
//        Type type = Type.None;
        Mode mode = Mode.Normal;
        Message.ForwardingPolicy forwardingPolicy = Message.ForwardingPolicy.DoNotForward;

        if (objects != null) {
            for (Object o : objects) {
                if (o instanceof Throwable throwable) {
                    message = Utils.Exceptions.stackTrace(throwable);
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
                } else if (o instanceof Mode mod) {
                    mode = mod;
                } /*else if (o instanceof Type typ) {
                    type = typ;
                } */else if (o instanceof Message.ForwardingPolicy fwp) {
                    forwardingPolicy = fwp;
                } else if (o instanceof KlabStatement statement) {
                    var lc = new NotificationImpl.LexicalContextImpl();
                    lc.setLength(statement.getLength());
                    lc.setOffsetInDocument(statement.getOffsetInDocument());
                    lc.setDocumentUrn(statement.getNamespace());
                    lexicalContext = lc;
                }
            }
        }

        var ret = new NotificationImpl(message, level);
        ret.setLexicalContext(lexicalContext);
        ret.setTimestamp(timestamp);
        ret.setMode(mode);
//        ret.setType(type);
        ret.setForwardingPolicy(forwardingPolicy);

        return ret;
    }
}