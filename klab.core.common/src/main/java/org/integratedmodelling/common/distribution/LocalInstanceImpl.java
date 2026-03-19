package org.integratedmodelling.common.distribution;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;

/** Common implementation of {@link LocalInstance} for all products. */
public abstract class LocalInstanceImpl implements LocalInstance {

  private final Distribution.Product product;
  private final Settings settings;
  private final File pidFile;
  protected final Stack.Tag tag;

  protected AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);
  protected DefaultExecutor executor;
  protected ExecuteWatchdog watchdog;
  protected ExecuteStreamHandler streamHandler;
  protected Long pid;

  /**
   * Subclasses must implement this to provide the command line to launch the product.
   *
   * @param product
   * @param settings
   * @return
   */
  protected abstract CommandLine getCommandLine(Distribution.Product product, Settings settings);

  public LocalInstanceImpl(Distribution.Product product, Settings settings, Stack.Tag tag) {
    this.product = product;
    this.settings = settings;
    this.tag = tag;

    if (product == null || product.getLocalPath() == null) {
      throw new KlabIllegalArgumentException("product must be non-null and have a local path");
    }

    File runDirectory = settings.get(Setting.RUN_DIRECTORY, File.class);
    if (runDirectory == null) {
      // Fallback to WORK_DIRECTORY/run if RUN_DIRECTORY is not set
      File workDir = settings.get(Setting.WORK_DIRECTORY, File.class);
      runDirectory = new File(workDir, "run");
    }

    if (!runDirectory.exists()) {
      runDirectory.mkdirs();
    }
    this.pidFile = new File(runDirectory, product.getType().getId() + ".pid");

    initializeState();
  }

  private void initializeState() {
    if (pidFile.exists()) {
      try {
        String content = Files.readString(pidFile.toPath(), StandardCharsets.UTF_8).trim();
        if (!content.isEmpty()) {
          String[] parts = content.split(":");
          long savedPid = Long.parseLong(parts[0]);
          String savedType = parts.length > 1 ? parts[1] : null;

          if (isProcessRunning(savedPid)) {
            if (savedType == null || savedType.equals(product.getType().getId())) {
              this.pid = savedPid;
              this.status.set(Status.RUNNING);
              monitorAlreadyRunningProcess(savedPid);
              Logging.INSTANCE.info(
                  "Connected to already running " + product.getType().getId() + " with PID " + pid);
              return;
            } else {
              Logging.INSTANCE.warn(
                  "Found PID file for "
                      + product.getType().getId()
                      + ", but it contains type "
                      + savedType
                      + ". Releasing lock.");
              Files.deleteIfExists(pidFile.toPath());
            }
          } else {
            Logging.INSTANCE.warn(
                "Found stale PID file for "
                    + product.getType().getId()
                    + ", process "
                    + savedPid
                    + " is not running");
            Files.deleteIfExists(pidFile.toPath());
          }
        }
      } catch (IOException | NumberFormatException e) {
        Logging.INSTANCE.error(
            "Error reading PID file for " + product.getType().getId() + ": " + e.getMessage());
      }
    }
    this.status.set(Status.STOPPED);
  }

  private void monitorAlreadyRunningProcess(long pid) {
    ProcessHandle.of(pid)
        .ifPresent(
            ph -> {
              ph.onExit()
                  .thenAccept(
                      p -> {
                        if (this.pid != null && this.pid.equals(p.pid())) {
                          this.status.set(Status.STOPPED);
                          cleanupState();
                        }
                      });
            });
  }

  private boolean isProcessRunning(long pid) {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  @Override
  public Distribution.Product getProduct() {
    return product;
  }

  @Override
  public Status getStatus() {
    return status.get();
  }

  /**
   * Set the stream handler to use for the launched process. If not set, a default {@link
   * PumpStreamHandler} will be used.
   *
   * @param streamHandler
   */
  public void setStreamHandler(ExecuteStreamHandler streamHandler) {
    this.streamHandler = streamHandler;
  }

  @Override
  public synchronized boolean start() {

    if (status.get() == Status.RUNNING) {
      return true;
    }

    CommandLine commandLine = getCommandLine(product, settings);
    if (commandLine == null) {
      return false;
    }

    executor =
        new DefaultExecutor() {
          @Override
          protected Process launch(
              CommandLine command, Map<String, String> env, File workingDirectory)
              throws IOException {
            Process process = super.launch(command, env, workingDirectory);
            pid = process.pid();
            persistState(pid);
            return process;
          }
        };

    watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);
    executor.setWorkingDirectory(product.getLocalPath());

    if (streamHandler == null) {
      streamHandler = new PumpStreamHandler();
    }
    executor.setStreamHandler(streamHandler);

    DefaultExecuteResultHandler resultHandler =
        new DefaultExecuteResultHandler() {
          @Override
          public void onProcessComplete(int exitValue) {
            super.onProcessComplete(exitValue);
            status.set(Status.STOPPED);
            cleanupState();
          }

          @Override
          public void onProcessFailed(ExecuteException e) {
            super.onProcessFailed(e);
            status.set(Status.ERROR);
            cleanupState();
          }
        };

    try {
      executor.execute(commandLine, resultHandler);
      this.status.set(Status.RUNNING);
      return true;
    } catch (IOException e) {
      Logging.INSTANCE.error(
          "Failed to start " + product.getType().getId() + ": " + e.getMessage());
      this.status.set(Status.ERROR);
      return false;
    }
  }

  private void persistState(long pid) {
    try {
      String data = pid + ":" + product.getType().getId();
      Files.writeString(pidFile.toPath(), data, StandardCharsets.UTF_8);
    } catch (IOException e) {
      Logging.INSTANCE.error("Failed to save PID file: " + e.getMessage());
    }
  }

  private void cleanupState() {
    try {
      Files.deleteIfExists(pidFile.toPath());
    } catch (IOException e) {
      Logging.INSTANCE.error("Failed to delete PID file: " + e.getMessage());
    }
    this.pid = null;
  }

  @Override
  public synchronized boolean stop() {
    if (watchdog != null) {
      watchdog.destroyProcess();
      watchdog = null;
      executor = null;
      return true;
    }
    if (pid != null) {
      ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
      cleanupState();
      status.set(Status.STOPPED);
      return true;
    }
    return false;
  }

  @Override
  public StartupOptions getSettings() {
    return null;
  }

  @Override
  public Stack.Tag getTag() {
    return tag;
  }
}
