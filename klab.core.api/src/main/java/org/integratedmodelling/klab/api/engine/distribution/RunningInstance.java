package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.engine.StartupOptions;

import java.util.function.Consumer;

public interface RunningInstance {

    enum Status {
        UNKNOWN, RUNNING, STOPPED, WAITING, ERROR
    }

    public record Result(boolean result, Throwable error, String log) {}

    /**
     * The release this is an instance of
     */
    Build getBuild();

    /**
     * The current status of the instance.
     *
     * @return
     */
    Status getStatus();

    /**
     * The settings for this instance
     * @return
     */
    StartupOptions getSettings();

    /**
     * Start the instance, returning immediately. A true return value means that the
     * instance has been started correctly and is either in WAITING, RUNNING or
     * ERROR state; it does not mean that it is running. A false return value means
     * that the instance could not be started, because of a corrupted product or
     * some other issue.
     *
     * @param listener
     *
     * @return
     */
    boolean start();

    /**
     * Stop the instance, returning immediately. The instance after this is called
     * can be in any status. A false return value means the instance could not be
     * stopped for any reason - corruption, already stopped etc. True means that
     * shutdown has correctly begun.
     *
     * @return
     */
    boolean stop();

    /**
     * Start listening for changes in status. Can be called at any time. For now
     * there is no corresponding stop() method.
     *
     * @param listener
     */
    void pollStatus(Consumer<Status> listener);
}
