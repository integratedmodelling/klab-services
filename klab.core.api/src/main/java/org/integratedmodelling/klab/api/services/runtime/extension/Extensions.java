package org.integratedmodelling.klab.api.services.runtime.extension;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Holder of descriptive records for extensions of all kinds. */
public interface Extensions {

  /**
   * Descriptor of an extension library with its services, annotations and verbs.
   *
   * @param name
   * @param description
   * @param services
   * @param annotations
   * @param actors
   * @param exporters
   * @param importers
   */
  record LibraryDescriptor(
      String name,
      String description,
      List<Pair<ServiceInfo, FunctionDescriptor>> services,
      List<Pair<ServiceInfo, FunctionDescriptor>> annotations,
      List<Pair<String, ActorDescriptor>> actors,
      List<Pair<ServiceInfo, FunctionDescriptor>> exporters,
      List<Pair<ServiceInfo, FunctionDescriptor>> importers) {

    public LibraryDescriptor {
      services = services == null ? new ArrayList<>() : services;
      annotations = annotations == null ? new ArrayList<>() : annotations;
      actors = actors == null ? new ArrayList<>() : actors;
      exporters = exporters == null ? new ArrayList<>() : exporters;
      importers = importers == null ? new ArrayList<>() : importers;
    }
  }

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
   * @param actors descriptor for all {@link Actor}-annotated classes in the component, including
   *     those in libraries.
   */
  record ComponentDescriptor(
      String id,
      Version version,
      String description,
      File sourceArchive,
      String fileHash,
      String mavenCoordinates,
      ResourcePrivileges usageRights,
      List<LibraryDescriptor> libraries,
      List<AdapterDescriptor> adapters,
      // FIXME these must be able to list multiple descriptors per URN, selected based on parameter
      //  types
      Map<String, List<FunctionDescriptor>> services,
      Map<String, List<FunctionDescriptor>> annotations,
      Map<String, List<ActorDescriptor>> actors,
      Map<String, List<FunctionDescriptor>> exporters,
      Map<String, List<FunctionDescriptor>> importers,
      String sourceServiceId, // ID of source service for updates,
      long timestamp // time of creation/last update
      ) {

    public ComponentDescriptor {
      libraries = libraries == null ? new ArrayList<>() : libraries;
      adapters = adapters == null ? new ArrayList<>() : adapters;
      services = services == null ? new HashMap<>() : services;
      annotations = annotations == null ? new HashMap<>() : annotations;
      actors = actors == null ? new HashMap<>() : actors;
      exporters = exporters == null ? new HashMap<>() : exporters;
      importers = importers == null ? new HashMap<>() : importers;
    }

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
              + actors.size()
              + " actors, "
              + annotations.size()
              + " adapters");
    }
  }

  /**
   * This descriptor contains everything needed (except the independently maintained implementation)
   * to execute a service, including the service info. It must remain serializable and is used to
   * build a catalog of available services and verbs, available (for now) in capabilities.
   */
  class FunctionDescriptor {
    public ServiceInfo serviceInfo;
    // check call style: 1 = call, scope, prototype; 2 = call, scope; 3 = custom, matched at
    // each call. FIXME this is probably obsolete by now
    public int methodCall;
    public boolean staticMethod;
    public String behaviorUrn;
    public boolean error;
  }

  class ActorDescriptor {

    public String urn;
    public String version;
    public String description;
    public String javaClassName;

    public List<FunctionDescriptor> verbs = new ArrayList<>();

    /** The adapter to implement casts, if any. */
    public FunctionDescriptor adapter;
  }
}
