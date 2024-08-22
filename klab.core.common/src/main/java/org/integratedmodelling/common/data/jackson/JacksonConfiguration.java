package org.integratedmodelling.common.data.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class JacksonConfiguration {

    public static final String CLASS_FIELD = "@CLASS";

    @SuppressWarnings("rawtypes")
    static class PolymorphicSerializer<T> extends JsonSerializer<T> {

        List<Field> getAllFields(Class clazz) {
            if (clazz == null) {
                return Collections.emptyList();
            }

            List<Field> result = new ArrayList<>(getAllFields(clazz.getSuperclass()));
            List<Field> filteredFields =
                    Arrays.stream(clazz.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())).collect(Collectors.toList());
            result.addAll(filteredFields);
            return result;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeObjectField(CLASS_FIELD, value.getClass().getName());
            for (Field field : getAllFields(value.getClass())) {
                field.setAccessible(true); // You might want to set modifier to public first.
                try {
                    var fvalue = field.get(value);
                    if (fvalue != null) {
                        gen.writeObjectField(field.getName(), fvalue);
                    }
                } catch (Throwable t) {
                    // screw that
                    Logging.INSTANCE.error(t);
                }
            }
            gen.writeEndObject();
        }
    }

    static class PolymorphicDeserializer<T> extends JsonDeserializer<T> {

        Field findField(Class cls, String name) {
            try {
                return cls.getDeclaredField(name);
            } catch (Throwable t) {
                return cls != Object.class ? findField(cls.getSuperclass(), name) : null;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            try {
                return (T) deserialize(node, p, (Field) null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new KlabIOException(e);
            }
        }

        private Object deserialize(JsonNode node, JsonParser parser, Class<?> type) throws Exception {

            if (node.isObject()) {
                return deserializeObject(node, parser, Class.forName(node.has(CLASS_FIELD) ?
                                                                     node.get(CLASS_FIELD).asText() :
                                                                     LinkedHashMap.class.getName()));
            } else if (node.isArray()) {
                return deserializeArray(node, parser, null);
            }
            return parser.getCodec().treeToValue(node, type);
        }

        private Object deserialize(JsonNode node, JsonParser parser, Field field) throws Exception {

            if (node.isObject()) {
                return deserializeObject(node, parser, Class.forName(node.has(CLASS_FIELD) ?
                                                                     node.get(CLASS_FIELD).asText() :
                                                                     LinkedHashMap.class.getName()));
            } else if (node.isArray()) {
                return deserializeArray(node, parser, field);
            }
            return parser.getCodec().treeToValue(node, Object.class);
        }

        private Class<?> getGenericType(Field field, int n) {
            Class<?> ret = Object.class;
            if (field != null && field.getGenericType() instanceof ParameterizedType type) {
                try {
                    ret = Class.forName(type.getActualTypeArguments()[n].getTypeName());
                } catch (ClassNotFoundException e) {
                    // just return Object
                }
            }
            return ret;
        }

        private Object deserializeArray(JsonNode node, JsonParser parser, Field field) throws Exception {
            Collection<Object> ret = field == null ? new ArrayList<>() : newCollection(field.getType());
            for (var elementNode : node) {
                ret.add(deserialize(elementNode, parser, getGenericType(field, 0)));
            }
            return ret;
        }

        private Object deserializeObject(JsonNode node, JsonParser parser, Class<?> cls) throws Exception {

            Iterator<String> fields = node.fieldNames();
            var constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            var ret = constructor.newInstance();

            while (fields.hasNext()) {
                String field = fields.next();
                if (field.startsWith("@")) {
                    continue;
                }

                var declaredField = findField(cls, field);
                if (declaredField == null && ret instanceof Map map) {
                    // FIXME must pass the generic type for the field
                    map.put(field, deserialize(node.get(field), parser, getGenericType(declaredField, 1)));
                } else if (declaredField != null) {
                    declaredField.setAccessible(true);
                    declaredField.set(ret, checkField(declaredField.getType(), deserialize(node.get(field),
                            parser, declaredField)));
                } else {
                    throw new KlabInternalErrorException("Unexpected field name " + field + " in " +
                            "deserialization of " + cls);
                }
            }

            return ret;
        }

        private Collection<Object> newCollection(Class<?> type) {
            if (!Modifier.isInterface(type.getModifiers()) && !Modifier.isAbstract(type.getModifiers()) && Modifier.isStatic(type.getModifiers())) {
                try {
                    return (Collection<Object>) type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    Logging.INSTANCE.error(e);
                    // fall through
                }
            }
            if (List.class.isAssignableFrom(type)) {
                return new ArrayList<>();
            } else if (Set.class.isAssignableFrom(type)) {
                return new HashSet<>();
            } else if (Queue.class.isAssignableFrom(type)) {
                return new LinkedList<>();
            } else if (Collection.class.isAssignableFrom(type)) {
                // generic collection, just use an ArrayList
                return new ArrayList<>();
            }
            throw new KlabInternalErrorException("Unpredicted collection type in custom deserializer: " + type.getCanonicalName());
        }

        private Object checkField(Class<?> type, Object val) {

            if (type.equals(Object.class)) {
                return val;
            } else if (type.isEnum() && val instanceof String) {
                val = Enum.valueOf((Class<? extends Enum>) type, (String) val);
            } else if ((type == Long.class || type == long.class) && val instanceof Integer integer) {
                val = integer.longValue();
            } else if ((type == Integer.class || type == int.class) && val instanceof Long integer) {
                val = integer.intValue();
            } else if (val instanceof Map map && !Map.class.isAssignableFrom(type)) {
                val = Utils.Json.convertMap(map, type);
            } else if (val instanceof String && URL.class.isAssignableFrom(type)) {
                try {
                    val = new URI(val.toString()).toURL();
                } catch (Exception e) {
                    val = null;
                }
            } else if (val instanceof String && URI.class.isAssignableFrom(type)) {
                try {
                    val = new URI(val.toString());
                } catch (Exception e) {
                    val = null;
                }
            }

            return val;
        }
    }

    public static ObjectMapper newObjectMapper() {
        var mapper = new ObjectMapper();
        configureObjectMapperForKlabTypes(mapper);
        return mapper;
    }

    @SuppressWarnings({"unchecked"})
    public static void configureObjectMapperForKlabTypes(ObjectMapper mapper) {

        // needed to avoid some shit, provided we add and risk even more shit
        mapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        SimpleModule module = new SimpleModule();
        for (var cls : new Class<?>[]{Group.class, Geometry.class, Pair.class, Notification.class,
                                      Project.Manifest.class, Identifier.class,
                                      Triple.class, Unit.class, Project.class, KlabAsset.class,
                                      Currency.class, Message.class, Worldview.class, Workspace.class,
                                      Concept.class, Observable.class, Resource.class, KimOntology.class,
                                      KimNamespace.class, KimObservationStrategyDocument.class,
                                      KdlDataflow.class, KActorsBehavior.class, KimModel.class,
                                      KimSymbolDefinition.class, Contextualizable.class, Identifier.class,
                                      KimConcept.class, KimObservable.class, Quantity.class,
                                      NumericRange.class, Annotation.class, Metadata.class,
                                      Geometry.Dimension.class, Parameters.class,
                                      Notification.LexicalContext.class}) {
            module.addSerializer(cls, new PolymorphicSerializer<>());
            module.addDeserializer(cls, new PolymorphicDeserializer<>());
        }

        mapper.registerModule(module);
        mapper.registerModule(new ParameterNamesModule());
    }

}
