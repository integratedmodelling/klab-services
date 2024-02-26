package org.integratedmodelling.common.data.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.common.logging.Logging;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public class JacksonConfiguration {

    static class LiteralDeserializer extends JsonDeserializer<Literal> {

        @Override
        public Literal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
                JsonProcessingException {
            LiteralImpl ret = new LiteralImpl();
            JsonNode node = p.getCodec().readTree(p);
            ret.setValueType(p.getCodec().treeToValue(node.get("valueType"), ValueType.class));
            switch (ret.getValueType()) {
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
                    ret.setValue(p.getCodec().treeToValue(node.get("valueType"), NumericRangeImpl.class));
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
            gen.writeObjectField("@CLASS", value.getClass().getName());
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
                return (T) deserialize(node, p, null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new KlabIOException(e);
            }
        }

        private Object deserialize(JsonNode node, JsonParser parser, Field field) throws Exception {

            if (node.isObject()) {
                return deserializeObject(node, parser, Class.forName(node.has("@CLASS") ?
                                                                     node.get("@CLASS").asText() :
                                                                     LinkedHashMap.class.getName()));
            } else if (node.isArray()) {
                return deserializeArray(node, parser, field);
            }
            return parser.getCodec().treeToValue(node, Object.class);
        }

        private Object deserializeArray(JsonNode node, JsonParser parser, Field field) throws Exception {
            Collection<Object> ret = field == null ? new ArrayList<>() : newCollection(field.getType());
            for (var elementNode : node) {
                ret.add(deserialize(elementNode, parser, null));
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
                    map.put(field, deserialize(node.get(field), parser, null));
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
            }
            throw new KlabInternalErrorException("Unpredicted collection type in custom deserializer: " + type.getCanonicalName());
        }

        private Object checkField(Class<?> type, Object val) {
            if (type.isEnum() && val instanceof String) {
                val = Enum.valueOf((Class<? extends Enum>) type, (String) val);
            } else if ((type == Long.class || type == long.class) && val instanceof Integer integer) {
                val = integer.longValue();
            } else if ((type == Integer.class || type == int.class) && val instanceof Long integer) {
                val = integer.intValue();
            }
            return val;
        }
    }

    public static ObjectMapper newObjectMapper() {
        var mapper = new ObjectMapper();
        configureObjectMapperForKlabTypes(mapper);
        return mapper;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@CLASS")
    public static class TypeInfoMixIn {
    }

    @SuppressWarnings({"unchecked"})
    public static void configureObjectMapperForKlabTypes(ObjectMapper mapper) {

        // needed to avoid some shit, provided we add and risk even more shit
        mapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);

        SimpleModule module = new SimpleModule();
        for (var cls : new Class<?>[]{Group.class, Geometry.class, Pair.class, Notification.class,
                                      Triple.class, Unit.class, KlabAsset.class, Currency.class,
                                      NumericRange.class, Annotation.class, Metadata.class,
                                      Geometry.Dimension.class, Parameters.class}) {
            module.addSerializer(cls, new PolymorphicSerializer());
            module.addDeserializer(cls, new PolymorphicDeserializer());
        }
        module.addDeserializer(Literal.class, new LiteralDeserializer());
        mapper.registerModule(module);
        mapper.registerModule(new ParameterNamesModule());
    }

}
