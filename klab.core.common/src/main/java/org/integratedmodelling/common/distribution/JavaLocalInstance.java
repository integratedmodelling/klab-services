package org.integratedmodelling.common.distribution;

import org.apache.commons.exec.CommandLine;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public class JavaLocalInstance extends LocalInstanceImpl {

  private int njars;
  private int ndirs;

  public JavaLocalInstance(Distribution.Product product, Settings settings, Stack.Tag tag) {
    super(product, settings, tag);
  }

  @Override
  protected CommandLine getCommandLine(Distribution.Product product, Settings settings) {

    CommandLine ret = new CommandLine(JreModel.INSTANCE.getJavaExecutable());
    ret.addArguments(
        getJavaOptions(
            getProduct(),
            512,
            getProduct().getType().defaultMaxMemoryLimitMB(),
            getProduct().getType().isService()));

    if (isUseDebugParameters(settings)) {
      ret.addArgument(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:"
              + getProduct().getType().getDebugPort());
    }
    ret.addArgument("-Dfile.encoding=UTF-8");
    if (getProduct().getType().isService()) {
      ret.addArgument("-Dserver-port=" + instancePort());
    }
    //
    String classpath = getClassPath();
    String mainclass = getProduct().getExecutable();

    if (mainclass == null && njars == 1 && ndirs == 0) {
      ret.addArguments(new String[] {"-jar", classpath});
    } else if (mainclass != null) {
      ret.addArguments(new String[] {"-cp", classpath});
      ret.addArgument(mainclass);
    } else {
      throw new KlabIllegalStateException(
          "Main class is not defined for " + product.getName() + " product");
    }

    //    if (startupOptions != null && ret != null) {
    //
    //      if (startupOptions.isStartLocalBroker()) {
    //        ret.addArgument("-startLocalBroker");
    //      }
    //
    //      // add the remaining startup options to the command line
    //      for (var arg : startupOptions.getArguments()) {
    //        ret.addArgument(arg);
    //      }
    //    }

    return ret;
  }

  private boolean isUseDebugParameters(Settings settings) {
    return switch (getProduct().getType()) {
      case RESOURCES_SERVICE ->
          settings.get(Setting.START_RESOURCES_SERVICE_IN_DEBUG_MODE, Boolean.class);
      case REASONER_SERVICE ->
          settings.get(Setting.START_REASONER_SERVICE_IN_DEBUG_MODE, Boolean.class);
      case RESOLVER_SERVICE ->
          settings.get(Setting.START_RESOLVER_SERVICE_IN_DEBUG_MODE, Boolean.class);
      case RUNTIME_SERVICE ->
          settings.get(Setting.START_RUNTIME_SERVICE_IN_DEBUG_MODE, Boolean.class);
      case LANGUAGE_SERVER, CLI, DATABASE_SERVER, AMQP_BROKER -> false;
    };
  }

  /**
   * FIXME incorporate in main code and enable redefinition through settings
   *
   * @return
   */
  private int instancePort() {
    return switch (getProduct().getType()) {
      case CLI, LANGUAGE_SERVER, AMQP_BROKER -> 0;
      case DATABASE_SERVER -> KlabService.Type.DATABASE.defaultPort;
      case RESOURCES_SERVICE -> KlabService.Type.RESOURCES.defaultPort;
      case REASONER_SERVICE -> KlabService.Type.REASONER.defaultPort;
      case RESOLVER_SERVICE -> KlabService.Type.RESOLVER.defaultPort;
      case RUNTIME_SERVICE -> KlabService.Type.RUNTIME.defaultPort;
    };
  }

  private String getClassPath() {

    StringBuilder ret = new StringBuilder();
    this.njars = 0;
    this.ndirs = 0;
    for (File file : Objects.requireNonNull(getProduct().getLocalPath().listFiles())) {
      if (file.toString().endsWith(".jar")) {
        this.njars++;
        ret.append((ret.isEmpty()) ? "" : Utils.OS.get().getClasspathSeparator())
            .append(file.getName());
      } else if (file.isDirectory()) {
        this.ndirs++;
        ret.append((ret.isEmpty()) ? "" : Utils.OS.get().getClasspathSeparator())
            .append(file.getName())
            .append(File.separator)
            .append("*");
      }
    }
    return ret.toString();
  }

  private String[] getJavaOptions(
      Distribution.Product product, int minMemM, int maxMemM, boolean isServer) {

    var ret = new ArrayList<String>();

    if (product.getProperty(Distribution.PRODUCT_JAVA_OPTIONS_PROPERTY) != null) {
      ret.add(product.getProperty(Distribution.PRODUCT_JAVA_OPTIONS_PROPERTY));
    }

    ret.add("-Xms" + minMemM + "M");
    ret.add("-Xmx" + maxMemM + "M");
    if (isServer) {
      ret.add("-server");
    }
    return ret.toArray(new String[0]);
  }

  @Override
  public Path getConfigurationPath() {
    // TODO negotiate a possibly reconfigured configuration path (I guess through clients?) Also
    //  the naming of the subdirectories should be part of the enums
    return Configuration.INSTANCE
        .getDataPath()
        .toPath()
        .resolve("services")
        .resolve(getProduct().getType().relativeConfigurationPath());
  }
}
