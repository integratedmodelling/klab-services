package org.integratedmodelling.klab.api.engine.distribution;

import java.io.InputStream;
import java.io.OutputStream;
import org.integratedmodelling.klab.api.engine.StartupOptions;

public interface LocalInstance {

  enum Status {
    UNKNOWN,
    RUNNING,
    STOPPED,
    WAITING,
    ERROR
  }

  enum Option {
    PROVIDE_INPUT_STREAM,
    PROVIDE_OUTPUT_STREAM
  }

  /** The release this is an instance of */
  Distribution.Product getProduct();

  /**
   * The release tag of the distribution that defined this instance.
   *
   * @return
   */
  Stack.Tag getTag();

  /**
   * The current status of the instance.
   *
   * @return
   */
  Status getStatus();

  boolean forceRestart(Option... options);

  /**
   * Start the instance, returning immediately. A true return value means that the instance has been
   * started correctly and is either in WAITING, RUNNING or ERROR state; it does not mean that it is
   * ready for use. A false return value means that the instance could not be started, because of a
   * corrupted product or some other issue.
   *
   * @return whether the startup process has been initiated correctly
   */
  boolean start(Option... options);

  /**
   * Stop the instance, returning immediately. The instance after this is called can be in any
   * status. A false return value means the instance could not be stopped for any reason -
   * corruption, already stopped etc. True means that shutdown has correctly begun.
   *
   * @return
   */
  boolean stop();

  /**
   * The settings for this instance
   *
   * @return
   */
  StartupOptions getSettings();

  /**
   * Returns the standard input stream of the process, if running.
   *
   * @return
   */
  OutputStream getOutputStream();

  /**
   * Returns the standard output stream of the process, if running.
   *
   * @return
   */
  InputStream getInputStream();
}
