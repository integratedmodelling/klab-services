package org.integratedmodelling.klab.api.services.resources;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceInfo;

import java.util.*;

/**
 * Specifications and schemata for resource importing and exporting. IDs of the transport utilized will be
 * required in each import or export call. These apply both to the default services and to individual
 * adapters, which can specify the transport schemata they accept and provide.
 */
public enum ResourceTransport {

    INSTANCE;

    private Map<String, List<Schema>> importSchemata = new HashMap<>();
    private Map<String, List<Schema>> exportSchemata = new HashMap<>();

    /**
     * A resource import schema. Each resource type may admit one or more. A few schemata are predefined
     */
    public static class Schema {

        public record Property(String name, Artifact.Type type, boolean optional, String defaultValue) {
        }

        private String name;
        private Type type;
        private String adapter;
        private Map<String, Property> properties = new HashMap<>();
        private Set<String> extensions = new HashSet<>();
        private String description;
        private KlabAsset.KnowledgeClass knowledgeClass;

        public enum Type {
            PROPERTIES,
            STREAM
        }

        public static Schema create(String name, Type type, KlabAsset.KnowledgeClass knowledgeClassDefined,
                                    String description) {
            Schema ret = new Schema();
            ret.setName(name);
            ret.setType(type);
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

        public Schema adapter(String string, Version version) {
            this.adapter = string + (version == null ? "" : ("@" + version.toString()));
            return this;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

    /**
     * Adds the known formats. Others may be added by processing adapter import/export annotations or
     * components.
     */
    ResourceTransport() {
        // define known schemata

        addImport("component.maven",
                Schema.create("component.maven.schema", Schema.Type.PROPERTIES,
                              KlabAsset.KnowledgeClass.COMPONENT, "Register a component available on Maven " +
                                      "using " +
                                      "the component's Maven coordinates")
                      .with("groupId", Artifact.Type.TEXT, false)
                      .with("adapterId", Artifact.Type.TEXT, false)
                      .with("version", Artifact.Type.TEXT, false));

        addImport("component.jar",
                Schema.create("component.maven.schema", Schema.Type.STREAM,
                              KlabAsset.KnowledgeClass.COMPONENT,
                              "Register a component by directly submitting a jar file")
                      .fileExtensions("jar"));

    }


}
