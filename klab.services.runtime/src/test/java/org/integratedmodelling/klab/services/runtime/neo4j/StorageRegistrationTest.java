package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.junit.jupiter.api.Test;

class StorageRegistrationTest {
  @Test void registrationBeforeComputationDoesNotReportMissingProvisionalStorage() {
    var storage = mock(StorageManagerImpl.class);
    when(storage.hasAllocatedStorage(-20)).thenReturn(false);
    assertTrue(KnowledgeGraphNeo4j.finalizeAllocatedStorage(storage, -20, 42));
    verify(storage, never()).finalizeStorage(anyLong(), anyLong());
  }

  @Test void allocatedStorageMustActuallyMigrateAndFailureIsNotSuppressed() {
    var storage = mock(StorageManagerImpl.class);
    when(storage.hasAllocatedStorage(-20)).thenReturn(true);
    when(storage.finalizeStorage(-20, 42)).thenReturn(false, true);
    assertFalse(KnowledgeGraphNeo4j.finalizeAllocatedStorage(storage, -20, 42));
    assertTrue(KnowledgeGraphNeo4j.finalizeAllocatedStorage(storage, -20, 42));
    verify(storage, times(2)).finalizeStorage(-20, 42);
  }
}
