package org.integratedmodelling.klab.api.configuration;

import java.nio.file.Path;
import java.util.Properties;

/** Coordinates the process root before configuration singletons perform filesystem writes. */
public final class DataRootBootstrap {
  public static final DataRootBootstrap PROCESS = new DataRootBootstrap();
  private Path initializedRoot;

  public synchronized Path prepare(Properties properties, Path cliRoot) {
    Path selected = DataRootSelection.selectForService(properties, cliRoot);
    requireCompatible(selected);
    properties.setProperty(DataRootSelection.DATA_DIRECTORY, selected.toString());
    return selected;
  }

  public synchronized Path claim(Properties properties) {
    return requireRoot(DataRootSelection.select(properties));
  }

  /** Also guard later explicit settings relocation before a singleton changes its backing path. */
  public synchronized Path requireRoot(Path requested) {
    Path selected = requested.toAbsolutePath().normalize();
    requireCompatible(selected);
    initializedRoot = selected;
    return selected;
  }

  private void requireCompatible(Path selected) {
    if (initializedRoot != null && !initializedRoot.equals(selected)) {
      throw new IllegalStateException("Data root was already initialized at a different location");
    }
  }
}
