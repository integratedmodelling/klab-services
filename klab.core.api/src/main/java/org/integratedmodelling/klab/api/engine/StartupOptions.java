package org.integratedmodelling.klab.api.engine;

import java.io.File;
import java.util.Collection;

public interface StartupOptions {

  /**
   * Tied to the
   *
   * <pre>-cert</pre>
   *
   * option, provides an alternate certificate file to use when initializing. The default depends on
   * the type of server and is specified in {@link
   * org.integratedmodelling.klab.api.authentication.KlabCertificate}.
   *
   * @return a {@link java.io.File} object.
   */
  File getCertificateFile();

  /**
   * Tied to the
   *
   * <pre>-dataDir</pre>
   *
   * option, provides an alternate work directory full path. The default is
   *
   * <pre>~/.klab</pre>
   *
   * .
   *
   * @return a {@link java.io.File} object.
   */
  File getDataDirectory();

  /**
   * Tied to the
   *
   * <pre>-port</pre>
   *
   * option, establishes the REST communication port if we are implementing REST communication. It
   * also transparently links to the Spring application properties so it can be specified either
   * way.
   *
   * @return a int.
   */
  int getPort();

  /**
   * Tied to -start-local-broker. Should only be passed to local services. Will start a message
   * broker on the URL and port configured in the Channel class.
   *
   * @return
   */
  boolean isStartLocalBroker();

  /**
   * Tied to
   *
   * <pre>-help</pre>
   *
   * option. Print version and usage banner and exit.
   *
   * @return a boolean.
   */
  boolean isHelp();

  Collection<File> getComponentPaths();

  String getCertificateResource();

  String getAuthenticatingHub();

  /**
   * Tied to
   *
   * <pre>-cloudConfig</pre>
   *
   * option. Configure product as spring configuration server application.
   *
   * @return a boolean.
   */
  boolean isCloudConfig();

  String getServiceName();

  /**
   * Return all arguments that weren't parsed as predefined options.
   *
   * @param additionalArguments any additional arguments we wish to add
   * @return any remaining arguments to pass to main()
   */
  String[] getArguments(String... additionalArguments);
}
