package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;

class ComponentIOLibraryTest {

    @Mock
    private BaseService service = Mockito.mock(BaseService.class);

    @BeforeEach
    void init() {
        Mockito.when(service.getComponentRegistry()).thenReturn(null);
    }

    @Test
    void importComponentDirectKar() {
        Parameters params = Parameters.create();
        File karFile = new File("src/test/resources/klab.component.geospatial-1.0-SNAPSHOT-component.kar");

        ComponentIOLibrary.importComponentDirectKar(params, karFile, service, null);

    }

    @Test
    void importComponentMaven() {
    }
}