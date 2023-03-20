package org.integratedmodelling.klab.data.encoding;

import java.io.IOException;
import java.util.Currency;
import java.util.Iterator;
import java.util.Map;

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
import org.integratedmodelling.klab.api.data.mediation.impl.Range;
import org.integratedmodelling.klab.api.exceptions.KInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.logging.Logging;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public class JacksonConfiguration {

    static class KimStatementResolverBuilder extends DefaultTypeResolverBuilder {
        private static final long serialVersionUID = -8873215972141029473L;

        public KimStatementResolverBuilder() {
            super(DefaultTyping.NON_FINAL, LaissezFaireSubTypeValidator.instance);
        }

        @Override
        public boolean useForType(JavaType t) {
            if (KimStatement.class.isAssignableFrom(t.getRawClass()) || Geometry.class.isAssignableFrom(t.getRawClass())
                    || Pair.class.isAssignableFrom(t.getRawClass()) || Triple.class.isAssignableFrom(t.getRawClass())
                    || Unit.class.isAssignableFrom(t.getRawClass()) || Currency.class.isAssignableFrom(t.getRawClass())
                    || NumericRange.class.isAssignableFrom(t.getRawClass())
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
                ret.setValue(p.getCodec().treeToValue(node.get("valueType"), Range.class));
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
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
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
                throw new KInternalErrorException(t);
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    public static void configureObjectMapperForKlabTypes(ObjectMapper mapper) {
        
        @SuppressWarnings("rawtypes")
        SimpleModule module = new SimpleModule().addSerializer(Annotation.class, new ParameterSerializer())
                .addSerializer(Metadata.class, new ParameterSerializer())
                .addSerializer(Parameters.class, new ParameterSerializer())
                .addDeserializer(Metadata.class, new ParameterDeserializer())
                .addDeserializer(Annotation.class, new ParameterDeserializer())
                .addDeserializer(Parameters.class, new ParameterDeserializer())
                .addDeserializer(Literal.class, new LiteralDeserializer());
        mapper.registerModule(module);
        mapper.registerModule(new ParameterNamesModule());

        TypeResolverBuilder<?> typeResolver = new KimStatementResolverBuilder();
        typeResolver.init(JsonTypeInfo.Id.CLASS, null);
        typeResolver.inclusion(JsonTypeInfo.As.PROPERTY);
        typeResolver.typeProperty("@CLASS");
        mapper.setDefaultTyping(typeResolver);
    }

}
