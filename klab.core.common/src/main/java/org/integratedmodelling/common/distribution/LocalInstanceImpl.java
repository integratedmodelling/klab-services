package org.integratedmodelling.common.distribution;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
  protected AtomicBoolean stopRequested = new AtomicBoolean(false);
  protected DefaultExecutor executor;
  protected ExecuteWatchdog watchdog;
  protected ExecuteStreamHandler streamHandler;
  protected boolean customStreamHandler;
  protected Process process;
  protected OutputStream outputStream;
  protected InputStream inputStream;
  protected Long pid;
  protected final Map<String, String> environmentOverrides = new ConcurrentHashMap<>();

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
              //              this.process = new ExternalProcess(savedPid);
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
                          markStopped();
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

  public void setEnvironmentOverride(String key, String value) {
    if (key == null || key.isBlank()) {
      return;
    }
    if (value == null) {
      environmentOverrides.remove(key);
    } else {
      environmentOverrides.put(key, value);
    }
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
    this.customStreamHandler = streamHandler != null;
  }

  @Override
  public boolean forceRestart(Option... options) {
    stop();
    waitForStop();
    return start(options);
  }

  @Override
  public synchronized boolean start(Option... options) {

    if (status.get() == Status.RUNNING) {
      return true;
    }
    if (status.get() == Status.WAITING) {
      return false;
    }

    CommandLine commandLine = getCommandLine(product, settings);
    if (commandLine == null) {
      return false;
    }
    stopRequested.set(false);

    EnumSet<Option> startOptions = EnumSet.noneOf(Option.class);
    if (options != null) {
      for (Option option : options) {
        if (option != null) {
          startOptions.add(option);
        }
      }
    }

    boolean provideOutputStream = startOptions.contains(Option.PROVIDE_OUTPUT_STREAM);
    boolean provideInputStream = startOptions.contains(Option.PROVIDE_INPUT_STREAM);

    executor =
        new DefaultExecutor() {
          @Override
          protected Process launch(
              CommandLine command, Map<String, String> env, File workingDirectory)
              throws IOException {
            process = super.launch(command, env, workingDirectory);
            pid = process.pid();
            if (provideOutputStream) {
              outputStream = process.getOutputStream();
            }
            if (provideInputStream) {
              inputStream = process.getInputStream();
            }
            persistState(pid);
            return process;
          }
        };

    watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);
    executor.setWorkingDirectory(product.getLocalPath());
    if (!customStreamHandler) {
      streamHandler = new LocalStreamHandler(provideInputStream, provideOutputStream);
    }
    executor.setStreamHandler(streamHandler);

    DefaultExecuteResultHandler resultHandler =
        new DefaultExecuteResultHandler() {
          @Override
          public void onProcessComplete(int exitValue) {
            super.onProcessComplete(exitValue);
            markStopped();
          }

          @Override
          public void onProcessFailed(ExecuteException e) {
            super.onProcessFailed(e);
            if (stopRequested.get()) {
              markStopped();
            } else {
              markError();
            }
          }
        };

    try {
      if (environmentOverrides.isEmpty()) {
        executor.execute(commandLine, resultHandler);
      } else {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.putAll(environmentOverrides);
        executor.execute(commandLine, environment, resultHandler);
      }
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
    this.inputStream = null;
    this.outputStream = null;
  }

  @Override
  public synchronized boolean stop() {
    if (watchdog != null) {
      stopRequested.set(true);
      status.set(Status.WAITING);
      if (pid != null) {
        monitorAlreadyRunningProcess(pid);
      }
      watchdog.destroyProcess();
      watchdog = null;
      executor = null;
      process = null;
      inputStream = null;
      outputStream = null;
      return true;
    }
    if (pid != null) {
      var stoppedPid = pid;
      var processHandle = ProcessHandle.of(stoppedPid);
      if (processHandle.isPresent() && processHandle.get().isAlive()) {
        stopRequested.set(true);
        status.set(Status.WAITING);
        monitorAlreadyRunningProcess(stoppedPid);
        if (!processHandle.get().destroy()) {
          processHandle.get().destroyForcibly();
        }
      } else {
        markStopped();
      }
      process = null;
      inputStream = null;
      outputStream = null;
      return true;
    }
    return false;
  }

  private void waitForStop() {
    long deadline = System.currentTimeMillis() + 10000;
    while (status.get() == Status.WAITING && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private synchronized void markStopped() {
    stopRequested.set(false);
    status.set(Status.STOPPED);
    cleanupState();
    watchdog = null;
    executor = null;
    process = null;
  }

  private synchronized void markError() {
    stopRequested.set(false);
    status.set(Status.ERROR);
    cleanupState();
    watchdog = null;
    executor = null;
    process = null;
  }

  @Override
  public OutputStream getOutputStream() {
    return process != null ? outputStream : null;
  }

  @Override
  public InputStream getInputStream() {
    return process != null ? inputStream : null;
  }

  @Override
  public StartupOptions getSettings() {
    return null;
  }

  @Override
  public Stack.Tag getTag() {
    return tag;
  }

  /** Default stream handler that only drains streams that are not exposed through start options. */
  private static class LocalStreamHandler implements ExecuteStreamHandler {

    private final boolean exposeStdout;
    private final boolean exposeStdin;
    private InputStream processOutputStream;
    private InputStream processErrorStream;
    private OutputStream processInputStream;
    private Thread stdoutThread;
    private Thread stderrThread;

    private LocalStreamHandler(boolean exposeStdout, boolean exposeStdin) {
      this.exposeStdout = exposeStdout;
      this.exposeStdin = exposeStdin;
    }

    @Override
    public void setProcessInputStream(OutputStream os) {
      this.processInputStream = os;
    }

    @Override
    public void setProcessOutputStream(InputStream is) {
      this.processOutputStream = is;
    }

    @Override
    public void setProcessErrorStream(InputStream is) {
      this.processErrorStream = is;
    }

    @Override
    public void start() throws IOException {
      if (!exposeStdout && processOutputStream != null) {
        stdoutThread = createDrainer(processOutputStream);
        stdoutThread.start();
      }
      if (processErrorStream != null) {
        stderrThread = createDrainer(processErrorStream);
        stderrThread.start();
      }
      if (!exposeStdin && processInputStream != null) {
        processInputStream.close();
      }
    }

    @Override
    public void stop() throws IOException {
      join(stdoutThread);
      join(stderrThread);
    }

    private static Thread createDrainer(InputStream input) {
      Thread thread =
          new Thread(
              () -> {
                byte[] buffer = new byte[8192];
                try {
                  while (input.read(buffer) != -1) {
                    // Drain stream to avoid subprocess blocking.
                  }
                } catch (IOException ignored) {
                  // Stream may close on process termination.
                } finally {
                  try {
                    input.close();
                  } catch (IOException ignored) {
                    // Ignore cleanup exceptions.
                  }
                }
              },
              "local-instance-stream-drainer");
      thread.setDaemon(true);
      return thread;
    }

    private static void join(Thread thread) {
      if (thread != null) {
        try {
          thread.join(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
