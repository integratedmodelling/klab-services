package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

/** Locality belongs in the Resources module; core services must not depend on its consumer. */
class ComponentLocalityRegressionTest {
  static final String ID = "regression.sync.component";
  static final Version VERSION = Version.create("1.0-SNAPSHOT");

  @Test void ordinaryComponentResponsesPreserveLocalityWhenMergedWithSameVersionRemote() throws Exception {
    var method=ResourcesProvider.class.getDeclaredMethod("componentResource",String.class,Version.class,
        long.class,Extensions.ComponentDescriptor.class); method.setAccessible(true);
    var local=mock(ResourcesProvider.class,CALLS_REAL_METHODS);
    var remote=mock(ResourcesProvider.class,CALLS_REAL_METHODS);
    doReturn("local-resources").when(local).serviceId(); doReturn(true).when(local).isLocal();
    doReturn("remote-resources").when(remote).serviceId(); doReturn(false).when(remote).isLocal();
    var a=(ResourceSet.Resource)method.invoke(local,ID,VERSION,1000L,null);
    var b=(ResourceSet.Resource)method.invoke(remote,ID,VERSION,2000L,null);
    var merged=org.integratedmodelling.klab.api.utils.Utils.Resources.merge(ResourceSet.of(a),ResourceSet.of(b));
    var selected=merged.getResults().iterator().next();
    // Control: the actual merger honors locality when it is supplied.
    a.setLocal(true);
    var control=org.integratedmodelling.klab.api.utils.Utils.Resources.merge(ResourceSet.of(a),ResourceSet.of(b));
    assertEquals("local-resources",control.getResults().iterator().next().getServiceId());
    System.out.println("LOCALITY_OBSERVATION selected="+selected.getServiceId()+" remoteTimestamp=2000 localTimestamp=1000");
    assertEquals("local-resources",selected.getServiceId(),"Ordinary component response lost its local-service provenance");
  }

}
