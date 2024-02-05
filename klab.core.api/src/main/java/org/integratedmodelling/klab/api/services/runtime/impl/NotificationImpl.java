package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serial;
import java.io.Serializable;
import java.net.URL;

/**
 * Trivial bean for notifications, so these can be sent outside the validator and processed in it. The
 * constructors are messy and nasty but cleaning this is low priority.
 *
 * @author ferdinando.villa
 */
public class NotificationImpl implements Notification, Serializable {

    @Serial
    private static final long serialVersionUID = -5812547783872203517L;

    private String message;
    private Level level;
    private Type type = Type.None;
    private long timestamp = System.currentTimeMillis();
    // this will be null when parsed, identities are in the runtime
    private String identity;
    private DocumentContext documentContext;

    public static class DocumentContextImpl implements DocumentContext {
        private URL url;
        private int offsetInDocument;
        private int length;

        @Override
        public URL getUrl() {
            return url;
        }

        public void setUrl(URL url) {
            this.url = url;
        }

        @Override
        public int getOffsetInDocument() {
            return offsetInDocument;
        }

        public void setOffsetInDocument(int offsetInDocument) {
            this.offsetInDocument = offsetInDocument;
        }

        @Override
        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }
    }

    public NotificationImpl() {
    }

    public NotificationImpl(String message, Level level) {
        this.message = message;
        this.level = level;
    }

    public NotificationImpl(Pair<String, Type> message, Level level) {
        this.message = message.getFirst();
        this.type = message.getSecond();
        this.level = level;
    }

    public NotificationImpl(String message2, Level level2, long timestamp2) {
        this(message2, level2);
        this.timestamp = timestamp2;
    }

    public NotificationImpl(Pair<String, Type> message2, Level level2, long timestamp2) {
        this(message2.getFirst(), level2);
        this.timestamp = timestamp2;
        this.type = message2.getSecond();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    @Override
    public DocumentContext getDocumentContext() {
        return documentContext;
    }

    public void setDocumentContext(DocumentContext documentContext) {
        this.documentContext = documentContext;
    }

    @Override
    public String toString() {
        return "NotificationImpl{" +
                "message='" + message + '\'' +
                ", level=" + level +
                '}';
    }
}
