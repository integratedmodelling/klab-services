package org.integratedmodelling.common.data.jackson;

import java.io.IOException;
import java.util.Currency;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.RangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.common.logging.Logging;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;
import org.integratedmodelling.klab.rest.AuthenticatedIdentityImpl;
import org.integratedmodelling.klab.rest.GroupImpl;

public class JacksonConfiguration {

    static class KimStatementResolverBuilder extends DefaultTypeResolverBuilder {
        private static final long serialVersionUID = -8873215972141029473L;

        public KimStatementResolverBuilder() {
            super(DefaultTyping.NON_FINAL, LaissezFaireSubTypeValidator.instance);
        }

        @Override
        public boolean useForType(JavaType t) {
            if (KlabStatement.class.isAssignableFrom(t.getRawClass()) || Geometry.class.isAssignableFrom(t.getRawClass())
                    || Pair.class.isAssignableFrom(t.getRawClass()) || Triple.class.isAssignableFrom(t.getRawClass())
                    || Unit.class.isAssignableFrom(t.getRawClass()) || Currency.class.isAssignableFrom(t.getRawClass())
                    || NumericRange.class.isAssignableFrom(t.getRawClass())
                    || KActorsStatement.class.isAssignableFrom(t.getRawClass())
                    || Notification.class.isAssignableFrom(t.getRawClass())) {
                return true;
            }

            return false;
        }
    }

    static class LiteralDeserializer extends JsonDeserializer<Literal> {

        @Override
        public Literal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            LiteralImpl ret = new LiteralImpl();
            JsonNode node = p.getCodec().readTree(p);
            ret.setValueType(p.getCodec().treeToValue(node.get("valueType"), ValueType.class));
            switch(ret.getValueType()) {
            case ANNOTATION:
                break;
            case ANYTHING:
                break;
            case ANYTRUE:
                break;
            case ANYVALUE:
                break;
            case BOOLEAN:
                ret.setValue(node.get("valueType").asBoolean());
                break;
            case CALLCHAIN:
                break;
            case COMPONENT:
                break;
            case DATE:
                break;
            case EMPTY:
                break;
            case ERROR:
                break;
            case EXPRESSION:
                break;
            case LIST:
                break;
            case CONSTANT:
            case STRING:
            case CLASS:
            case IDENTIFIER:
            case LOCALIZED_KEY:
            case REGEXP:
            case NUMBERED_PATTERN:
            case URN:
                ret.setValue(node.get("valueType").asText());
                break;
            case MAP:
                break;
            case NODATA:
                break;
            case NUMBER:
                ret.setValue(node.get("valueType").asDouble());
                break;
            case OBJECT:
                break;
            case OBSERVABLE:
                break;
            case OBSERVATION:
                break;
            case QUANTITY:
                break;
            case RANGE:
                ret.setValue(p.getCodec().treeToValue(node.get("valueType"), RangeImpl.class));
                break;
            case SET:
                break;
            case TABLE:
                break;
            case TREE:
                break;
            case TYPE:
                break;
            case CONCEPT:
                break;
            case DOUBLE:
                ret.setValue(node.get("valueType").asDouble());
                break;
            case INTEGER:
                ret.setValue(node.get("valueType").asInt());
                break;
            default:
                break;
            }
            return ret;
        }

    }

    @SuppressWarnings("rawtypes")
    static class ParameterSerializer<T extends Parameters> extends JsonSerializer<T> {

        @Override
        public void serialize(Parameters value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeObjectField("@type", getTypeName(value));
            for (Object key : value.keySet()) {
                gen.writeObjectField(key.toString(), value.get(key));
            }
            gen.writeEndObject();
        }

        private String getTypeName(Parameters<?> value) {
            if (value instanceof Metadata) {
                return "KMetadata";
            } else if (value instanceof Annotation) {
                return "KAnnotation";
            }
            return "KParameters";
        }
    }

    static class ParameterDeserializer<T extends Parameters<?>> extends JsonDeserializer<T> {

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return (T) readParameters(node, p, node.get("@type").asText());
        }

        @SuppressWarnings("unchecked")
        private Parameters<?> readParameters(JsonNode node, JsonParser p, String type) {

            try {
                Parameters<?> ret = null;

                switch(type) {
                case "KParameters":
                    ret = (Parameters<?>) Parameters.create();
                    break;
                case "KAnnotation":
                    ret = new AnnotationImpl();
                    break;
                case "KMetadata":
                    ret = new MetadataImpl();
                    break;
                }
                Iterator<String> fields = node.fieldNames();
                while(fields.hasNext()) {
                    String field = fields.next();
                    JsonNode value = node.get(field);
                    Object val = null;
                    if (field.startsWith("@")) {
                        continue;
                    } else if (value.has("@type")) {
                        val = readParameters(value, p, node.get("@type").asText());
                    } else {
                        try {
                            val = p.getCodec().treeToValue(value, Object.class);
                        } catch (JsonProcessingException e) {
                            // screw that
                            Logging.INSTANCE.error(e);
                        }
                    }
                    ((Map<String, Object>) ret).put(field, val);
                }
                return ret;
            } catch (Throwable t) {
                throw new KlabInternalErrorException(t);
            }
        }
    }

    public static ObjectMapper newObjectMapper() {
        var mapper = new ObjectMapper();
        configureObjectMapperForKlabTypes(mapper);
        return mapper;
    }

    @SuppressWarnings({"unchecked"})
    public static void configureObjectMapperForKlabTypes(ObjectMapper mapper) {
        
        @SuppressWarnings("rawtypes")
        SimpleModule module = new SimpleModule();

        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(Group.class, GroupImpl.class);
        module.setAbstractTypes(resolver);

        module.addSerializer(Annotation.class, new ParameterSerializer())
                .addSerializer(Metadata.class, new ParameterSerializer())
                .addSerializer(Parameters.class, new ParameterSerializer())
                .addDeserializer(Metadata.class, new ParameterDeserializer())
                .addDeserializer(Annotation.class, new ParameterDeserializer())
                .addDeserializer(Parameters.class, new ParameterDeserializer())
                .addDeserializer(Literal.class, new LiteralDeserializer());

        mapper.registerModule(module);
        mapper.registerModule(new ParameterNamesModule());
        mapper.activateDefaultTypingAsProperty(new PolymorphicTypeValidator() {
            @Override
            public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
                return Validity.ALLOWED;
            }

            @Override
            public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) throws JsonMappingException {
                // TODO pass only XxxImpl?
                return Validity.ALLOWED;
            }

            @Override
            public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) throws JsonMappingException {
                return Validity.ALLOWED;
            }
        }, DefaultTyping.JAVA_LANG_OBJECT, "@type");

        // TODO this is for the above resolver, which would be great to abandon in favor of more mappings although
        //  some of the polymorphism will remain an issue. Maybe a Statement de/serializer would solve it.
//        SimpleModule typeMapper = new SimpleModule("ExternalTypeMapping", Version.unknownVersion());
//        typeMapper.setAbstractTypes(resolver);

//        TypeResolverBuilder<?> typeResolver = new KimStatementResolverBuilder();
//        typeResolver.init(JsonTypeInfo.Id.CLASS, null);
//        typeResolver.inclusion(JsonTypeInfo.As.PROPERTY);
//        typeResolver.typeProperty("@CLASS");
//        mapper.setDefaultTyping(typeResolver);
//        mapper.registerModule(typeMapper);
    }
// TODO check this out
//    import java.util.Map;
//import java.util.Optional;
//
//    public class JacksonDeserializerOfNamedTypes extends StdDeserializer<Object> {
//        private final Map<String, Class<?>> typesByName;
//        private final String typeProperty;
//
//        private JacksonDeserializerOfNamedTypes(final Map<String, Class<?>> typesByName, final String typeProperty) {
//            super(Object.class);
//
//            this.typesByName = typesByName;
//            this.typeProperty = typeProperty;
//        }
//
//        @Override
//        public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException, JsonProcessingException {
//            final ObjectCodec codec = parser.getCodec();
//            final JsonNode root = parser.readValueAsTree();
//            final JsonNode typeNameNodeOrNull = root.get(typeProperty);
//            if (typeNameNodeOrNull == null) {
//                throw new JsonMappingException(parser, "Unable to determine Java type of JSON: " + root);
//            } else {
//                final String typeName = typeNameNodeOrNull.asText();
//                return Optional
//                        .ofNullable(typesByName.get(typeName))
//                        .map(type -> parseOrNull(root, type, codec))
//                        .orElseThrow(() ->
//                                new JsonMappingException(parser, String.format(
//                                        "Unsupported type name '%s' in JSON: %s", typeName, root)));
//            }
//        }
//
//        private <T> T parseOrNull(final JsonNode root, final Class<T> type, final ObjectCodec codec) {
//            try {
//                return root.traverse(codec).readValueAs(type);
//            } catch (IOException e) {
//                return null;
//            }
//        }
//
//        public static void main(String[] args) throws Exception {
//            final Map<String, Class<?>> typesByName = scanForNamedTypes();
//
//            final SimpleModule namedTypesModule = new SimpleModule("my-named-types-module");
//            namedTypesModule.addDeserializer(Object.class, new JacksonDeserializerOfNamedTypes(typesByName, JsonTypeInfo.Id.NAME.getDefaultPropertyName()));
//
//            final Car pinto = new Car("Ford", "Pinto", 1971);
//            final Balloon sharik = new Balloon("blue");
//            final ObjectMapper mapper = new ObjectMapper().registerModule(namedTypesModule);
//            System.out.println(mapper.readValue(mapper.writeValueAsString(pinto), Object.class).getClass());
//            System.out.println(mapper.readValue(mapper.writeValueAsString(sharik), Object.class).getClass());
//        }
//
//        @JsonTypeName("Balloon")
//        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
//        public static final class Balloon {
//            public String color;
//
//            private Balloon() {}
//
//            public Balloon(final String color) {
//                this.color = color;
//            }
//        }
//
//        @JsonTypeName("Car")
//        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
//        public static final class Car {
//            public String make;
//            public String model;
//            public int year;
//
//            private Car() {}
//
//            public Car(final String make, final String model, final int year) {
//                this.make = make;
//                this.model = model;
//                this.year = year;
//            }
//        }
//
//        static Map<String, Class<?>> scanForNamedTypes() {
//            //in reality, i'd be using a framework (e.g. Reflections) to scan the classpath
//            //for classes tagged with @JsonTypeName to avoid maintaining manual mappings
//            final Map<String, Class<?>> typesByName = new HashMap<>();
//            typesByName.put("Balloon", Balloon.class);
//            typesByName.put("Car", Car.class);
//            return Collections.unmodifiableMap(typesByName);
//        }
//    }
//
}
