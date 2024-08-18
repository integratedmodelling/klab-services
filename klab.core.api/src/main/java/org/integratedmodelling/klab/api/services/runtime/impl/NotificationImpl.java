package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serial;
import java.io.Serializable;

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
    //    private Type type = Type.None;
    private long timestamp = System.currentTimeMillis();
    // this will be null when parsed, identities are in the runtime
    private String identity;
    private LexicalContext lexicalContext;
    private Mode mode;

//    private Message.ForwardingPolicy forwardingPolicy = Message.ForwardingPolicy.DoNotForward;

    public static class LexicalContextImpl implements LexicalContext {

        private String documentUrn;
        private String projectUrn;
        private int offsetInDocument;
        private int length;
        private KlabAsset.KnowledgeClass documentType;
        private KlabAsset.KnowledgeClass type;

        @Override
        public String getDocumentUrn() {
            return documentUrn;
        }

        public void setDocumentUrn(String documentUrn) {
            this.documentUrn = documentUrn;
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

        @Override
        public KlabAsset.KnowledgeClass getDocumentType() {
            return documentType;
        }

        public void setDocumentType(KlabAsset.KnowledgeClass documentType) {
            this.documentType = documentType;
        }

        @Override
        public String getProjectUrn() {
            return projectUrn;
        }

        public void setProjectUrn(String projectUrn) {
            this.projectUrn = projectUrn;
        }

        @Override
        public KlabAsset.KnowledgeClass getType() {
            return type;
        }

        public void setType(KlabAsset.KnowledgeClass type) {
            this.type = type;
        }
    }

    public NotificationImpl() {
    }

    public NotificationImpl(String message, Level level) {
        this.message = message;
        this.level = level;
    }

    //    public NotificationImpl(Pair<String, Type> message, Level level) {
    //        this.message = message.getFirst();
    //        this.type = message.getSecond();
    //        this.level = level;
    //    }

    //    public NotificationImpl(String message2, Level level2, long timestamp2) {
    //        this(message2, level2);
    //        this.timestamp = timestamp2;
    //    }
    //
    //    public NotificationImpl(Pair<String, Type> message2, Level level2, long timestamp2) {
    //        this(message2.getFirst(), level2);
    //        this.timestamp = timestamp2;
    //        this.type = message2.getSecond();
    //    }

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

    //    @Override
    //    public Type getType() {
    //        return type;
    //    }

    @Override
    public Mode getMode() {
        return mode;
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    //    public void setType(Type type) {
    //        this.type = type;
    //    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    @Override
    public LexicalContext getLexicalContext() {
        return lexicalContext;
    }

    public void setLexicalContext(LexicalContext lexicalContext) {
        this.lexicalContext = lexicalContext;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

//    @Override
//    public Message.ForwardingPolicy getForwardingPolicy() {
//        return forwardingPolicy;
//    }
//
//    public void setForwardingPolicy(Message.ForwardingPolicy forwardingPolicy) {
//        this.forwardingPolicy = forwardingPolicy;
//    }

    @Override
    public String toString() {
        return "NotificationImpl{" + "message='" + message + '\'' + ", level=" + level + '}';
    }
}
