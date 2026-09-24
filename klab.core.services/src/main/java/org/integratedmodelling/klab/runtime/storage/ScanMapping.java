package org.integratedmodelling.klab.runtime.storage;

import java.util.List;
import org.integratedmodelling.klab.api.data.StorageScan;

/** Metadata-only mapping shared by exact and spatial read sessions. */
interface ScanMapping {
  List<StorageScan.Partition> partitions();

  int[][] dependencies();

  IndexedStorageReader reader(int partition, List<IndexedStorageReader> sources, int blockValues);
}
