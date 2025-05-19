package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.KlabService;
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
      String serviceId,
      KlabService.Type serviceType,
      boolean universal,
      boolean reentrant,
      boolean contextualizing,
      boolean sanitizing,
      boolean inspecting,
      boolean publishing,
      Set<ResourceAdapter.Validator.LifecyclePhase> validatedPhases,
      List<ResourceTransport.Schema> importSchemata,
      List<ResourceTransport.Schema> exportSchemata) {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      AdapterDescriptor that = (AdapterDescriptor) o;
      return Objects.equals(name, that.name) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version);
    }
  }

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
   * The usage rights are hosted within the rights system and are not part of the descriptor; the
   * rights in the manifest are used to initialize the component's rights in the hosting service.
   *
   * @param id the mandatory, unique component URN.
   * @param version the mandatory version number, propagating to all contained elements
   * @param description the mandatory description
   * @param sourceArchive the local file hosting the component. Even if the component is used after
   *     unpacking the file, this should be kept for validation and integrity.
   * @param fileHash if the file hash is null, the component should never be used except in a local
   *     configuration and with admin privileges
   * @param mavenCoordinates the Maven/Gradle string identifying the component if it comes from
   *     Maven, consisting of groupId:artifactId:version
   * @param libraries descriptors for all {@link Library}-annotated classes in the component.
   * @param adapters descriptors for all {@link ResourceAdapter}-annotated classes in the component
   * @param services descriptors for all {@link KlabService}-annotated methods and classes,
   *     including those hosted within libraries.
   * @param annotations descriptor for all special annotations and their handler methods, including
   *     those in libraries
   * @param verbs descriptor for all {@link Verb}-annotated methods and classes in the component,
   *     including those in libraries.
   */
  record ComponentDescriptor(
      String id,
      Version version,
      String description,
      File sourceArchive,
      String fileHash,
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
    public boolean staticClass; // TODO remove (not now because it messes up config)
    public boolean staticMethod;
    public boolean error;
  }
}
