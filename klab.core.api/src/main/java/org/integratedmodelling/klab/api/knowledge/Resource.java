package org.integratedmodelling.klab.api.knowledge;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * A resource descriptor, including all the public information related to a URN-specified resource
 * or service. In the resource server this can also contain access details and credential metadata,
 * which must be removed by the adapter before the resource data are sent to any consumer. The
 * descriptor applies to both stored resources and services (the latter have " klab" as the host
 * name in the URN).
 */
public interface Resource extends Knowledge, Resolvable {

  /**
   * Get the geometry associated with the resource, without fetching the entire data content.
   *
   * @return the resource's geometry. A resource that works for any geometry should return an empty
   *     geometry.
   */
  Geometry getGeometry();

  /**
   * Get the version associated with the resource. If the resource is a k.LAB service, return the
   * adapter's version.
   *
   * @return the resource's version.
   */
  Version getVersion();

  /**
   * The data adapter that published this resource and will be used to encode it.
   *
   * @return the adapter. Should only be null when no adapter is used: resources that depend on an
   *     adapter should never be created if the adapter isn't found.
   */
  String getAdapterType();

  /**
   * Resources come with both system-defined and user-defined metadata. User metadata will be
   * indexed by Dublin Core properties. Other metadata fields will depend on the adapter used (for
   * example, no-data values or metadata attributes such as name).
   *
   * @return any metadata associated with the resource. Never null.
   */
  Metadata getMetadata();

  /**
   * The ID of the service that produced the resource.
   *
   * @return
   */
  String getServiceId();

  /**
   * Get the history of this resource as a list of all its versions. Resource services will return
   * an empty list.
   *
   * @return the list of previous resources in order of timestamp (oldest first).
   */
  List<Resource> getHistory();

  /**
   * URNs coming with parameters will list them here. These are intrinsic to the resource and stored
   * with it. Parameter passed using the URN fragment are specific of the URN reference and not the
   * resource, and do not appear here.
   *
   * <p>Parameters may contain access details or credentials that must be removed by the adapter's
   * sanitizer before the resource data are sent through HTTP.
   *
   * @return parameter map, possibly empty, never null.
   */
  Parameters<String> getParameters();

  /**
   * Each resource may expose one or more attributes, which describe the content retrievable through
   * the resource. This applies to objects when the resource is of type object; use of attributes
   * for non-object resources is reserved but not operational at the moment.
   *
   * <p>The attributes should not be confused this with the resource parameters, which are stored
   * with the resource by the validator to help the encoder produce the artifacts, or with resources
   * inputs and outputs.
   *
   * @author Ferd
   */
  Collection<Attribute> getAttributes();

  /**
   * A resource may have attributes beyond the "proper" ones from {@link #getAttributes()} that can
   * be categorized usefully. For example a table may need to turn the column headers into
   * categories. These are handled internally by the adapters so need no description as in an
   * attribute, but their IDs can be exposed here so that the UI can report them.
   *
   * @return
   */
  Collection<String> getCategorizables();

  /**
   * Dependencies describe artifacts that must be available when the resource is used to produce
   * data. They use the same structure as attributes and are necessary only in URNs that identify
   * computations.
   *
   * @return dependencies
   */
  Collection<Attribute> getInputs();

  /**
   * Dependencies describe artifacts that can be made be available beyond the main artifact when the
   * resource is used to produce data. They use the same structure as attributes and can only appear
   * in URNs that identify computations.
   *
   * @return dependencies
   */
  Collection<Attribute> getOutputs();

  /**
   * The type of the main artifact produced.
   *
   * @return the type
   */
  Artifact.Type getType();

  /**
   * The descriptor for each attribute. Not much at the moment.
   *
   * @author Ferd
   */
  interface Attribute {

    /**
     * @return
     */
    String getName();

    /**
     * @return
     */
    Artifact.Type getType();

    /**
     * True if it identifies the object it relates to uniquely.
     *
     * @return
     */
    boolean isKey();

    /**
     * True if it may have no data. If an attribute is a key this must return false.
     *
     * @return
     */
    boolean isOptional();

    /**
     * Return a numeric index into the resource when appropriate. This only applies to some
     * resources that have indexed attributes, so it shouldn't be relied upon unless in one of
     * those. If the resource isn't indexing its attribute, this should return -1 for all
     * attributes.
     *
     * @return
     */
    int getIndex();
  }

  /**
   * Resource builder
   *
   * @author ferdinando.villa
   */
  interface Builder {

    /**
     * @param type
     * @return the builder itself
     */
    Builder withType(Artifact.Type type);

    /**
     * @param key
     * @param value
     * @return the builder itself
     */
    Builder withMetadata(String key, Object value);

    /**
     * @param key
     * @param value
     * @return the builder itself
     */
    Builder withParameter(String key, Object value);

    /**
     * @param geometry
     * @return the builder itself
     */
    Builder withGeometry(Geometry geometry);

    /**
     * Add a local resource path.
     *
     * @param path
     * @return the builder itself
     */
    Builder addLocalResourcePath(String path);

    /**
     * Add an error notification to make the build produce a failed resource.
     *
     * @param notifications
     * @return the builder itself
     */
    Builder withNotifications(Notification... notifications);

    /**
     * @param v
     * @return the builder itself
     */
    Builder withResourceVersion(Version v);

    /**
     * @param timestamp
     * @return the builder itself
     */
    Builder withResourceTimestamp(long timestamp);

    /**
     * Add a history item. The passed resource should have no history of its own and these should be
     * added in order of timestamp, oldest first.
     *
     * @param previousResource
     * @return the builder itself
     */
    Builder addHistory(Resource previousResource);

    /**
     * Add a codelist, which will be saved as a file upon building.
     *
     * @param codelist
     * @return the builder itself
     */
    Builder addCodeList(Codelist codelist);

    /**
     * Build the resource with the passed URN. If there are errors, build a resource with errors;
     * never return null.
     *
     * @return the built resource
     */
    Resource build();

    /**
     * Set the adapter type of the built resource.
     *
     * @param string the adapter type
     * @return the builder itself
     */
    Builder withAdapterType(String string);

    /**
     * Set the local resource path.
     *
     * @param localPath the local resource path
     * @return the builder itself
     */
    Builder withLocalPath(String localPath);

    /**
     * Set the local resource name - for file resources, this should be the name of the primary file
     * they were loaded from, without any path but with the extension.
     *
     * @param localName the local resource name
     * @return the builder itself
     */
    Builder withLocalName(String localName);

    /**
     * Add all the passed parameters
     *
     * @param parameters a parameters object
     * @return the builder itself
     */
    Builder withParameters(Parameters<String> parameters);

    /**
     * Set the project name. Only for local resources. Project name enters the local URN but is not
     * exposed by the IResource API. Stored to enable easier management and retrieval.
     *
     * @param name
     * @return the builder itself
     */
    Builder withProjectName(String name);

    /**
     * Return all the files that compose this resource in their original locations.
     *
     * <p>This is only called on builders created by importers.
     *
     * @return
     */
    Collection<File> getImportedFiles();

    /**
     * Return a suitable local ID for this resource.
     *
     * <p>This is only called on builders created by importers.
     *
     * @return
     */
    String getResourceId();

    /**
     * Exclusively for use by importers. Resource IDs become part of URNs and are normally handled
     * externally unless we import in batch. Resource IDs don't need to check for uniqueness (the
     * importer will change suitably) but unique IDs should be set when possible, as the unique-fied
     * built externally can be ugly.
     *
     * @param identifier an identifier describing the resource, suitable for use as part of a URN
     *     (so no colons, punctuation, uppercase characters or anything but underscores as
     *     separators).
     */
    void setResourceId(String identifier);

    /**
     * Exclusively for use by importers that use physical files. All files set in here are copied to
     * the resource directory and exported when published.
     *
     * @param file original, existing file (will be copied to final location by import handler)
     */
    void addImportedFile(File file);

    /**
     * Add an attribute definition to the builder. For now attributes are simple enough that we just
     * pass all parameters instead of returning an attribute builder.
     *
     * @param name
     * @param type
     * @param key
     * @param optional
     * @return
     */
    Builder withAttribute(String name, Artifact.Type type, boolean key, boolean optional);

    /**
     * Add a dependency definition to the builder. For now dependencies are simple enough that we
     * just pass all parameters instead of returning an attribute builder.
     *
     * @param name
     * @param type
     * @param key
     * @param optional
     * @return
     */
    Builder withInput(String name, Artifact.Type type, boolean key, boolean optional);

    /**
     * Add an output definition to the builder.
     *
     * @param name
     * @param type
     * @param key
     * @return
     */
    Builder withOutput(String name, Artifact.Type type);

    /**
     * The builder knows in advance the URN for the prospective resource.
     *
     * @return
     */
    String getUrn();
  }

  /**
   * Return a timestamp that matches the time of last modification of the resource described.
   *
   * @return a long.
   */
  long getTimestamp();

  /**
   * Return all local resource file paths, as slash-separated strings starting at a point depending
   * on the resource type (e.g. in local resources it will start at the project name). May be empty,
   * never null. All paths will start with the return value of {@link #getLocalPath()}.
   *
   * @return all local resource file paths
   */
  List<String> getLocalPaths();

  /**
   * If the resource is local, a local path should be defined and will identify a directory where
   * all the {@link #getLocalPaths() local file resources} are found.
   *
   * @return local path
   */
  String getLocalPath();

  /**
   * In local resources, this is the short name that the resource can be referred to in k.IM
   * <strong>within the project that owns it</strong>. It will be null in public resources and won't
   * be recognized within different local projects.
   *
   * @return local name
   */
  String getLocalName();

  /**
   * In local resources, this is the name of the containing project and must be valid. In public
   * resources it must be null.
   *
   * @return project name
   */
  String getLocalProjectName();

  /**
   * Return the reference name for all codelists defined within this resource. A xxx.json file (in
   * lowercase) must be present for each of these in the resource folder or in a public codelist
   * repository. When allowed by the providers, these can be used as authorities and their codes
   * referenced within k.IM code as identities.
   *
   * @return
   */
  List<String> getCodelists();

  /**
   * A resource may come with notifications from the validator or importer, including error
   * notifications. The online/offline status should first of all check for error notifications,
   * which must be removed when the resource is updated to fix errors.
   *
   * @return
   */
  List<Notification> getNotifications();
}
