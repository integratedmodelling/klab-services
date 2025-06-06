package org.integratedmodelling.common.distribution;

import org.apache.commons.exec.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.common.configuration.Settings;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RunningInstanceImpl implements RunningInstance {

  protected AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);
  protected DefaultExecutor executor;
  protected StartupOptions startupOptions;
  protected Build build;
  private int njars;
  private int ndirs;
  private Scope scope;

  public RunningInstanceImpl(Build release, Scope scope, StartupOptions startupOptions) {
    this.startupOptions = startupOptions;
    this.build = release;
    this.scope = scope;
  }

  @Override
  public Build getBuild() {
    return build;
  }

  @Override
  public Status getStatus() {
    return status.get();
  }

  @Override
  public StartupOptions getSettings() {
    return startupOptions;
  }

  private String[] getJavaOptions(int minMemM, int maxMemM, boolean isServer) {

    ArrayList<String> ret = new ArrayList<>();

    ret.add("-Xms" + minMemM + "M");
    ret.add("-Xmx" + maxMemM + "M");
    if (isServer) {
      ret.add("-server");
    }
    return ret.toArray(new String[ret.size()]);
  }

  private int debugPort() {
    // TODO link to product
    return 8000;
  }

  protected CommandLine getCommandLine(Scope scope) {
    //            /*
    //            create JavaOptions with StartupOptions, use it for createCommandLine in a new
    //            RunningInstanceImpl
    //             */
    Settings settings = new Settings(build.getRelease());

    // load any customizations from the main k.LAB properties
    settings.initialize(
        getBuild().getRelease().getProduct(), Configuration.INSTANCE.getProperties());

    CommandLine ret = new CommandLine(JreModel.INSTANCE.getJavaExecutable());
    ret.addArguments(getJavaOptions(512, settings.getMaxEngineMemory().getValue(), isServer()));
    //
    if (settings.isUseDebugParameters().getValue()) {
      ret.addArgument(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:"
              + getBuild().getRelease().getProduct().getProductType().getDebugPort());
    }
    ret.addArgument("-Dfile.encoding=UTF-8");
    if (isServer()) {
      ret.addArgument("-Dserver-port=" + instancePort());
    }
    //
    String classpath = getClassPath();
    String mainclass = build.getExecutable();

    if (mainclass == null && njars == 1 && ndirs == 0) {
      ret.addArguments(new String[] {"-jar", classpath});
    } else if (mainclass != null) {
      ret.addArguments(new String[] {"-cp", classpath});
      ret.addArgument(mainclass);
    } else {
      scope.error(
          "Remote distribution error: main class is not defined for "
              + build.getProduct().getName()
              + " product",
          false);
      ret = null;
    }

    if (startupOptions != null && ret != null) {

      if (startupOptions.isStartLocalBroker()) {
        ret.addArgument("-startLocalBroker");
      }

      // add the remaining startup options to the command line
      for (var arg : startupOptions.getArguments()) {
        ret.addArgument(arg);
      }
    }

    return ret;
  }

  private String getClassPath() {

    String ret = "";
    this.njars = 0;
    this.ndirs = 0;
    for (File file : build.getLocalWorkspace().listFiles()) {
      if (file.toString().endsWith(".jar")) {
        this.njars++;
        ret += (ret.isEmpty() ? "" : Utils.OS.get().getClasspathSeparator()) + file.getName();
      } else if (file.isDirectory()) {
        this.ndirs++;
        ret +=
            (ret.isEmpty() ? "" : Utils.OS.get().getClasspathSeparator())
                + file.getName()
                + File.separator
                + "*";
      }
    }
    return ret;
  }

  private int instancePort() {
    return switch (build.getProduct().getProductType()) {
      case CLI, MODELER -> 0;
      case RESOURCES_SERVICE -> KlabService.Type.RESOURCES.defaultPort;
      case REASONER_SERVICE -> KlabService.Type.REASONER.defaultPort;
      case RESOLVER_SERVICE -> KlabService.Type.RESOLVER.defaultPort;
      case COMMUNITY_SERVICE -> KlabService.Type.COMMUNITY.defaultPort;
      case RUNTIME_SERVICE -> KlabService.Type.RUNTIME.defaultPort;
    };
  }

  private boolean isServer() {
    return switch (build.getProduct().getProductType()) {
      case CLI, MODELER -> false;
      default -> true;
    };
  }

  /**
   * TODO use executor.setStreamHandler to something that 1) sends the logs somewhere and 2) enables
   * showing some on stdout/err (e.g. errors) while filtering the rest and maybe enabling streaming
   * to a URL with the service tag, so that we can configure a less-like functionality in a window
   * (whose config can be saved so that).
   *
   * <p>look at https://github.com/otros-systems/otroslogviewer (socket or log appender),
   * https://github .com/tmoreno/open-log-viewer
   *
   * @return
   */
  @Override
  public boolean start() {

    CommandLine cmdLine = getCommandLine(scope);

    Logging.INSTANCE.info(
        "Starting "
            + build.getProduct().getDescription()
            + " with command line: \""
            + cmdLine.toString()
            + "\"");

    /*
     * assume error was reported
     */
    if (cmdLine == null) {
      return false;
    }

    this.executor = new DefaultExecutor();
    this.executor.setWorkingDirectory(build.getLocalWorkspace());

    Map<String, String> env = new HashMap<>();
    env.putAll(System.getenv());

    status.set(Status.WAITING);
    try {
      this.executor.execute(
          cmdLine,
          env,
          new ExecuteResultHandler() {

            @Override
            public void onProcessFailed(ExecuteException ee) {
              scope.error(ee.getMessage());
              status.set(Status.ERROR);
            }

            @Override
            public void onProcessComplete(int arg0) {
              status.set(Status.STOPPED);
            }
          });
    } catch (Exception e) {
      scope.error(e);
      status.set(Status.ERROR);
    }

    return true;
  }

  @Override
  public boolean stop() {
    // does nothing - override based on product type
    return false;
  }
}
