package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Holder of descriptive records for extensions of all kinds. */
public interface Extensions {

  /**
   * Describes an adapter from a client's perspective. Included in component descriptor which is
   * part of the common service capabiities..
   *
   * @param name
   * @param version
   * @param universal
   * @param reentrant
   * @param contextualizing
   * @param sanitizing
   * @param inspecting
   * @param publishing
   * @param validatedPhases
   * @param importSchemata
   * @param exportSchemata
   */
  record AdapterDescriptor(
      String name,
      Version version,
      boolean universal,
      boolean reentrant,
      boolean contextualizing,
      boolean sanitizing,
      boolean inspecting,
      boolean publishing,
      Set<ResourceAdapter.Validator.LifecyclePhase> validatedPhases,
      List<ResourceTransport.Schema> importSchemata,
      List<ResourceTransport.Schema> exportSchemata) {}

  /**
   * Descriptor of an extension library with its services, annotations and verbs.
   *
   * @param name
   * @param description
   * @param services
   * @param annotations
   * @param verbs
   */
  record LibraryDescriptor(
      String name,
      String description,
      List<Pair<ServiceInfo, FunctionDescriptor>> services,
      List<Pair<ServiceInfo, FunctionDescriptor>> annotations,
      List<Pair<ServiceInfo, FunctionDescriptor>> verbs) {}

  /**
   * Describes a component which may bring with itself libraries and adapters with their content.
   *
   * @param id
   * @param version
   * @param description
   * @param sourceArchive
   * @param permissions
   * @param mavenCoordinates
   * @param libraries
   * @param adapters
   * @param services
   * @param annotations
   * @param verbs
   */
  record ComponentDescriptor(
      String id,
      Version version,
      String description,
      File sourceArchive,
      ResourcePrivileges permissions,
      String mavenCoordinates,
      List<LibraryDescriptor> libraries,
      List<AdapterDescriptor> adapters,
      Map<String, FunctionDescriptor> services,
      Map<String, FunctionDescriptor> annotations,
      Map<String, FunctionDescriptor> verbs) {

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ComponentDescriptor that = (ComponentDescriptor) o;
      return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, version);
    }

    public Notification extractInfo() {
      return Notification.info(
          "Component "
              + id()
              + " ["
              + version
              + "]: "
              + services().size()
              + "services, "
              + adapters.size()
              + " adapters, "
              + annotations.size()
              + " annotations");
    }
  }

  /**
   * This descriptor contains everything needed to execute a service, including the service info.
   */
  class FunctionDescriptor {
    public ServiceInfo serviceInfo;
    // check call style: 1 = call, scope, prototype; 2 = call, scope; 3 = custom, matched at
    // each call
    public int methodCall;
    public boolean staticClass;  // TODO remove (not now because it messes up config)
    public boolean staticMethod;
    public boolean error;
  }
}
