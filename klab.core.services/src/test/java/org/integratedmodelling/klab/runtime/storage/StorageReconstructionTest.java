package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.junit.jupiter.api.Test;

class StorageReconstructionTest {
  @Test
  void databaseOrderDoesNotChangeNativeScannerOrder() {
    var first = shard(0, 2, Storage.Type.LONG);
    var second = shard(1, 2, Storage.Type.LONG);
    var descriptors = new ArrayList<Storage.Shard>(List.of(second, first));
    StorageImpl.validateRestoredShards(descriptors, Storage.Type.LONG);
    assertEquals(List.of(first, second), descriptors);
  }

  @Test
  void missingPartitionIsNotAcceptedAsCompleteData() {
    var descriptors = new ArrayList<Storage.Shard>(List.of(shard(0, 2, Storage.Type.DOUBLE)));
    assertThrows(KlabIllegalStateException.class,
        () -> StorageImpl.validateRestoredShards(descriptors, Storage.Type.DOUBLE));
  }

  @Test
  void duplicatePartitionIsNotAcceptedAsCompleteData() {
    var descriptors = new ArrayList<Storage.Shard>(List.of(
        shard(0, 2, Storage.Type.DOUBLE), shard(0, 2, Storage.Type.DOUBLE)));
    assertThrows(KlabIllegalStateException.class,
        () -> StorageImpl.validateRestoredShards(descriptors, Storage.Type.DOUBLE));
  }

  @Test
  void persistedTypeMustMatchObservationContract() {
    var descriptors = new ArrayList<Storage.Shard>(List.of(shard(0, 1, Storage.Type.FLOAT)));
    assertThrows(KlabIllegalStateException.class,
        () -> StorageImpl.validateRestoredShards(descriptors, Storage.Type.DOUBLE));
  }

  private ShardImpl shard(int index, int count, Storage.Type type) {
    var shard = new ShardImpl();
    shard.setUrn("persisted-" + index);
    shard.setShardIndex(index);
    shard.setShardCount(count);
    shard.setNativeType(type);
    shard.setShardingStrategy(Data.ShardingStrategy.trivial(type));
    return shard;
  }
}
