package org.integratedmodelling.klab.api.services.resources;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.AuthenticatedIdentity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Specifications and schemata for resource importing and exporting. IDs of the transport utilized will be
 * required in each import or export call. These apply both to the default services and to individual
 * adapters, which can specify the transport schemata they accept and provide.
 */
public enum ResourceTransport {

    INSTANCE;

    private final Map<String, List<Schema>> importSchemata = new HashMap<>();
    private final Map<String, List<Schema>> exportSchemata = new HashMap<>();

    //    public Schema COMPONENT_MAVEN;
    //    public Schema COMPONENT_JAR;
    //    public Schema PROJECT_ZIP;
    //    public Schema PROJECT_GIT;


    /**
     * A resource import schema. Each resource type may admit one or more. A few schemata are predefined
     */
    public static class Schema {

        /**
         * Create a file asset. Validated against this schema.
         *
         * @param file
         * @return
         */
        public Asset asset(File file) {
            var ret = new Asset();
            ret.setFile(file);
            return ret;
        }

        /**
         * Create a URL-based asset. Validated against this schema.
         *
         * @param url
         * @return
         */
        public Asset asset(URL url) {
            var ret = new Asset();
            ret.setUrl(url);
            return ret;
        }

        /**
         * Create a properties-based asset. Validated against the schema.
         *
         * @param properties
         * @return
         */
        public Asset asset(Object... properties) {
            var ret = new Asset();
            ret.setProperties(Parameters.create(properties));
            return ret;
        }


        /**
         * Asset coordinate object used in service calls that provide assets for import. The asset can be a
         * direct bytestream coming from a URL or a file, or be specified through properties. A URN may be
         * optionally specified.
         * <p>
         * The asset must be
         */
        public class Asset {

            private Parameters<String> properties;
            private String urn;
            private URL url;
            private File file;

            public Parameters<String> getProperties() {
                return properties;
            }

            public void setProperties(Parameters<String> properties) {
                this.properties = properties;
            }

            public String getUrn() {
                return urn;
            }

            public void setUrn(String urn) {
                this.urn = urn;
            }

            public URL getUrl() {
                return url;
            }

            public void setUrl(URL url) {
                this.url = url;
            }

            public File getFile() {
                return file;
            }

            public void setFile(File file) {
                this.file = file;
            }
        }

        public record Property(String name, Artifact.Type type, boolean optional, String defaultValue) {
        }

        private Type type;
        private String adapter;
        private Map<String, Property> properties = new LinkedHashMap<>();
        private Set<String> extensions = new HashSet<>();
        private String description;
        private KlabAsset.KnowledgeClass knowledgeClass;
        private final List<String> mediaTypes = new ArrayList<>();
        private String schemaId;

        public enum Type {
            PROPERTIES, STREAM
        }

        public static Schema create(String schemaId, Type type,
                                    KlabAsset.KnowledgeClass knowledgeClassDefined,
                                    String description) {
            Schema ret = new Schema();
            ret.setType(type);
            ret.setSchemaId(schemaId);
            ret.setDescription(description);
            return ret;
        }

        /**
         * Define a property that will be null if optional.
         *
         * @param property
         * @param propertyType
         * @param optional
         * @return self
         */
        public Schema with(String property, Artifact.Type propertyType, boolean optional) {
            properties.put(property, new Property(property, propertyType, optional, null));
            return this;
        }

        public Schema fileExtensions(String... extensions) {
            if (type != Type.STREAM) {
                throw new KlabIllegalStateException("Extensions cannot be added to a property schema");
            }
            if (extensions != null) {
                for (var extension : extensions) {
                    this.extensions.add(extension);
                }
            }
            return this;
        }

        public Schema mediaType(String... mediaType) {
            if (mediaType != null) {
                Collections.addAll(this.mediaTypes, mediaType);
            }
            return this;
        }


        /**
         * For optional properties
         *
         * @param property     property name
         * @param defaultValue must be a POJO
         * @return self
         */
        public Schema with(String property, Object defaultValue) {
            if (type != Type.PROPERTIES) {
                throw new KlabIllegalStateException("Properties cannot be added to a file schema");
            }
            properties.put(property, new Property(property, Artifact.Type.classify(defaultValue), true,
                    defaultValue.toString()));
            return this;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public void setSchemaId(String schemaId) {
            this.schemaId = schemaId;
        }

        public Schema adapter(String string, Version version) {
            this.adapter = string + (version == null ? "" : ("@" + version.toString()));
            return this;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public Map<String, Property> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Property> properties) {
            this.properties = properties;
        }

        public Set<String> getExtensions() {
            return extensions;
        }

        public void setExtensions(Set<String> extensions) {
            this.extensions = extensions;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public KlabAsset.KnowledgeClass getKnowledgeClass() {
            return knowledgeClass;
        }

        public void setKnowledgeClass(KlabAsset.KnowledgeClass knowledgeClass) {
            this.knowledgeClass = knowledgeClass;
        }

        public List<String> getMediaTypes() {
            return mediaTypes;
        }


    }

    public void addImport(String schemaId, Schema... schemata) {
        if (schemata != null) {
            for (var schema : schemata) {
                importSchemata.computeIfAbsent(schemaId, (key) -> new ArrayList<>()).add(schema);
            }
        }
    }

    public void addExport(String schemaId, Schema... schemata) {
        if (schemata != null) {
            for (var schema : schemata) {
                exportSchemata.computeIfAbsent(schemaId, (key) -> new ArrayList<>()).add(schema);
            }
        }
    }

    public Map<String, List<Schema>> getImportSchemata() {
        return Collections.unmodifiableMap(importSchemata);
    }

    public Map<String, List<Schema>> getExportSchemata() {
        return Collections.unmodifiableMap(exportSchemata);
    }


    /**
     * Find the import schemata within those identified with <code>schemaId</code> that are compatible with
     * the passed media type and identity. If mediaType isn't null, the result should be unique.
     *
     * @param schemaId
     * @param mediaType
     * @param identity
     * @return
     */
    public List<Schema> findImportSchemata(String schemaId, String mediaType,
                                           AuthenticatedIdentity identity) {
        return findSchemata(importSchemata, schemaId, mediaType, identity);
    }

    /**
     * Find the export schemata within those identified with <code>schemaId</code> that are compatible with
     * the passed media type and identity. If mediaType isn't null, the result should be unique.
     *
     * @param schemaId
     * @param mediaType
     * @param identity
     * @return
     */
    public List<Schema> findExportSchemata(String schemaId, String mediaType,
                                           AuthenticatedIdentity identity) {
        return findSchemata(exportSchemata, schemaId, mediaType, identity);
    }

    // TODO pass scope for permissions
    private List<Schema> findSchemata(Map<String, List<Schema>> schemata, String schemaId, String mediaType
            , AuthenticatedIdentity identity) {
        List<Schema> ret = new ArrayList<>();
        if (schemata.containsKey(schemaId)) {
            for (var schema : schemata.get(schemaId)) {
                if (schema.mediaTypes.isEmpty() && mediaType != null) {
                    continue;
                } else {
                    if (mediaType == null || !schema.mediaTypes.contains(mediaType)) {
                        continue;
                    }
                }
                // TODO check permissions w.r.t. identity
                ret.add(schema);
            }
        }
        return ret;
    }

    public void registerImportSchema(ServiceInfo serviceInfo) {

        // create the schema
        var namespace = Utils.Paths.getLeading(serviceInfo.getName(), '.');
        var type = serviceInfo.listArguments().isEmpty() ? Schema.Type.STREAM : Schema.Type.PROPERTIES;
        var schema = Schema.create(serviceInfo.getName(), type, serviceInfo.getTargets().iterator().next(),
                serviceInfo.getDescription());

        for (var arg : serviceInfo.listArguments()) {
            schema = schema.with(arg.getName(), arg.getType().getFirst(), arg.isOptional());
        }
        addImport(namespace, schema);
    }

    public void registerExportSchema(ServiceInfo serviceInfo) {
        // create the schema
        var namespace = Utils.Paths.getLeading(serviceInfo.getName(), '.');
        var type = serviceInfo.listArguments().isEmpty() ? Schema.Type.STREAM : Schema.Type.PROPERTIES;
        var schema = Schema.create(serviceInfo.getName(), type, serviceInfo.getTargets().iterator().next(),
                serviceInfo.getDescription());

        // TODO revise, this is copied from import
        for (var arg : serviceInfo.listArguments()) {
            schema = schema.with(arg.getName(), arg.getType().getFirst(), arg.isOptional());
        }

        addExport(namespace, schema);
    }


    /**
     * Adds the known formats. Others may be added by processing adapter import/export annotations or
     * components.
     * <p>
     * TODO list permissions needed to submit
     */
    ResourceTransport() {

        //        addImport("component",
        //                COMPONENT_MAVEN = Schema.create("component.maven",
        //                        Schema.Type.PROPERTIES, KlabAsset.KnowledgeClass.COMPONENT, "Register a
        //                        component " +
        //                                "available on Maven " + "using " + "the component's Maven
        //                                coordinates").with(
        //                        "groupId", Artifact.Type.TEXT, false).with("adapterId", Artifact.Type.TEXT,
        //                        false).with("version", Artifact.Type.TEXT, false),
        //                COMPONENT_JAR = Schema.create("component.jar",
        //                        Schema.Type.STREAM, KlabAsset.KnowledgeClass.COMPONENT, "Register a
        //                        component by " +
        //                                "directly " +
        //                                "submitting a jar file").mediaType("application/java-archive")
        //                                .fileExtensions("jar"));
        //
        //        addImport("project.git",
        //                PROJECT_GIT = Schema.create("project.git", Schema.Type.PROPERTIES,
        //                        KlabAsset.KnowledgeClass.PROJECT, "Register a k.LAB project by submitting
        //                        the URL " +
        //                                "of a Git "
        //                                + "repository and optional credentials").with("url", Artifact
        //                                .Type.TEXT,
        //                        false).with("username", Artifact.Type.TEXT, true).with("password",
        //                        Artifact.Type.TEXT, true).with("token", Artifact.Type.TEXT, true),
        //                PROJECT_ZIP = Schema.create("project.zip",
        //                        Schema.Type.STREAM,
        //                        KlabAsset.KnowledgeClass.PROJECT, "Register a k.LAB by directly
        //                        submitting a zip " +
        //                                "archive").mediaType("application/zip",
        //                                "application/x-zip-compressed").fileExtensions("zip"));

    }

    // TODO use the scope for permissions (here or downstream in service controllers)
    public Schema findSchema(String id, Map<String, List<Schema>> schemata, Scope scope) {
        var list = schemata.get(Utils.Paths.getLeading(id, '.'));
        if (list != null) {
            for (var schema : list) {
                if (schema.getSchemaId().equals(id)) {
                    return schema;
                }
            }
        }
        return null;
    }


}
