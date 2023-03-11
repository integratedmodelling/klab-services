package org.integratedmodelling.klab.authentication.impl;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.integratedmodelling.klab.api.auth.IRuntimeIdentity;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessageBus;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.Notification.Type;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.logging.Logging;

public class Monitor implements Channel {

    private int errorCount = 0;
    private AtomicBoolean isInterrupted = new AtomicBoolean(false);
    private int waitTime;
    private Identity identity;
    
    transient MessageBus messageBus;

    protected Monitor() {}
    
    public Monitor(Identity identity) {
        this.identity = identity;
    }

    public Monitor(Identity identity, MessageBus messageBus) {
        this.identity = identity;
        this.messageBus = messageBus;
    }

    protected Monitor(Monitor monitor) {
        this.identity = monitor.identity;
        this.errorCount = monitor.errorCount;
    }

    public void setError(Throwable e) {
        this.errorCount++;
    }

    @Override
    public void info(Object... info) {
        Pair<String, Type> message = Utils.Notifications.getMessage(info);
        Consumer<String> infoWriter = Logging.INSTANCE.getInfoWriter();
        if (infoWriter != null) {
            infoWriter.accept(message.getFirst());
        }
        send(new NotificationImpl(message, Level.INFO));
    }

    @Override
    public void warn(Object... o) {
        Pair<String, Type> message = Utils.Notifications.getMessage(o);
        Consumer<String> warningWriter = Logging.INSTANCE.getWarningWriter();
        if (warningWriter != null) {
            warningWriter.accept(message.getFirst());
        }
        send(new NotificationImpl(message, Level.WARNING));
    }

    @Override
    public void error(Object... o) {
        Pair<String, Type> message = Utils.Notifications.getMessage(o);
        Consumer<String> errorWriter = Logging.INSTANCE.getErrorWriter();
        if (errorWriter != null) {
            errorWriter.accept(message.getFirst());
        }
        send(new NotificationImpl(message, Level.SEVERE));
        errorCount++;
    }

    @Override
    public void debug(Object... o) {
        Pair<String, Type> message = Utils.Notifications.getMessage(o);
        Consumer<String> debugWriter = Logging.INSTANCE.getDebugWriter();
        if (debugWriter != null) {
            debugWriter.accept(message.getFirst());
        }
        send(new NotificationImpl(message, Level.FINE));
    }

    @Override
    public void send(Object... o) {

        Message message = null;

        if (o != null && o.length > 0) {
            if (messageBus != null) {
                if (o.length == 1 && o[0] instanceof Message) {
                    messageBus.post(message = (Message) o[0]);
                } else if (o.length == 1 && o[0] instanceof Notification) {
                    messageBus.post(message = Message.create((Notification) o[0], this.identity.getId()));
                } else {
                    messageBus.post(message = Message.create(this.identity.getId(), o));
                }
            }
        }
    }

//    @Override
    public Future<Message> ask(Object... o) {
        if (o != null && o.length > 0) {
            if (messageBus != null) {
                if (o.length == 1 && o[0] instanceof Message) {
                    return messageBus.ask((Message) o[0]);
                } else if (o.length == 1 && o[0] instanceof Notification) {
                    return messageBus.ask(Message.create((Notification) o[0], this.identity.getId()));
                } else {
                    return messageBus.ask(Message.create(this.identity.getId(), o));
                }
            }
        }
        return null;
    }

    @Override
    public void post(Consumer<Message> handler, Object... o) {
        if (o != null && o.length > 0) {
            if (messageBus != null) {
                if (o.length == 1 && o[0] instanceof Message) {
                    messageBus.post((Message) o[0], handler);
                } else if (o.length == 1 && o[0] instanceof Notification) {
                    messageBus.post(Message.create((Notification) o[0], this.identity.getId()), handler);
                } else {
                    messageBus.post(Message.create(this.identity.getId(), o), handler);
                }
            }
        }
    }

    @Override
    public Identity getIdentity() {
        return identity;
    }

    @Override
    public boolean hasErrors() {
        return errorCount > 0;
    }

    public Monitor get(Identity identity) {
        Monitor ret = new Monitor(identity);
        return ret;
    }

    /**
     * Called to notify the start of any runtime job pertaining to our identity (always a
     * {@link IRuntimeIdentity} such as a task or script).
     */
    public void notifyStart() {
        System.out.println(identity + " started");
    }

    /**
     * Called to notify the start of any runtime job pertaining to our identity (always a
     * {@link IRuntimeIdentity} such as a task or script).
     * 
     * @param error true for abnormal exit
     */
    public void notifyEnd(boolean error) {
        ((errorCount > 0 || error) ? System.err : System.out)
                .println(identity + ((errorCount > 0 || error) ? " finished with errors" : " finished with no errors"));
    }

    public void interrupt() {
        isInterrupted.set(true);
        // IIdentity id = getIdentity();
        // // interrupt any parents that are the same class as ours (i.e. tasks)
        // while (id != null &&
        // id.getClass().isAssignableFrom(id.getParentIdentity().getClass())) {
        // id = id.getParentIdentity();
        // ((Monitor)((IRuntimeIdentity)id).getMonitor()).interrupt();
        // }
    }

    @Override
    public boolean isInterrupted() {
        return isInterrupted.get();
    }

    @Override
    public void addWait(int seconds) {
        this.waitTime = seconds;
        warn("Please try this operation again in " + seconds + " seconds");
    }

    @Override
    public int getWaitTime() {
        return this.waitTime;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public AtomicBoolean getIsInterrupted() {
        return isInterrupted;
    }

    public void setIsInterrupted(AtomicBoolean isInterrupted) {
        this.isInterrupted = isInterrupted;
    }

    public MessageBus getMessageBus() {
        return messageBus;
    }

    public void setMessageBus(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }
    
    
}
