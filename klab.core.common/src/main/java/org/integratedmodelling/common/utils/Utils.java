package org.integratedmodelling.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Utils extends org.integratedmodelling.klab.api.utils.Utils {

    public static class Http {

        public static class Client implements AutoCloseable {
            private HttpClient client;
            private URI uri;

            /**
             * GET helper that sets all headers and automatically handles JSON marshalling.
             *
             * @param apiRequest
             * @param resultClass
             * @param <T>
             * @return
             */
            public static <T> HttpResponse<T> post(String apiRequest, Object payload, Class<T> resultClass, Object... parameters) {
                return null;
            }

            /**
             * POST helper that sets all headers and automatically handles JSON marshalling.
             *
             * @param apiRequest
             * @param resultClass
             * @param parameters  paired key, value sequence for URL options
             * @param <T>
             * @return
             */
            public static <T> HttpResponse<T> get(String apiRequest, Class<T> resultClass, Object... parameters) {
                return null;
            }

            @Override
            public void close() throws Exception {
                if (client != null) {
                    client.close();
                }
            }
        }

        /**
         * Get a configured client for a specific URL; if the URL is recognized as being handled by a specific
         * authentication scheme, use the configured credentials for it and automatically build the
         * authentication strategy into the returned client. This should be used for services outside the
         * k.LAB network that do not require an authenticated scope. Use within a try {} pattern to ensure
         * that the connection is closed appropriately.
         *
         * @param serviceUrl
         * @return
         */
        public static Client getClient(String serviceUrl) {
            // TODO use configuration for timeouts and other options
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
            var ret = new Client();
            ret.client = client;
            ret.uri = URI.create(serviceUrl);
            return ret;
        }

        /**
         * Get an authenticated client for a k.LAB service in a given scope. If needed, notify the scope to
         * the service passing the hub token and obtain authorization. Clients for local services should
         * always be authorized. Use within a try {} pattern to ensure that the  connection is closed
         * appropriately.
         *
         * @param scope
         * @param service
         * @return a client authenticated for the service in the passed scope
         * @throws org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException if not authorized to
         *                                                                                access
         */
        public static Client getServiceClient(Scope scope, Service service) {
            return null;
        }
    }

    public static class Json {

        static ObjectMapper defaultMapper;

        static {
            defaultMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                                              .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
            defaultMapper.getSerializerProvider().setNullKeySerializer(new NullKeySerializer());
            JacksonConfiguration.configureObjectMapperForKlabTypes(defaultMapper);
        }

        static class NullKeySerializer extends StdSerializer<Object> {

            private static final long serialVersionUID = 7120301608140961908L;

            public NullKeySerializer() {
                this(null);
            }

            public NullKeySerializer(Class<Object> t) {
                super(t);
            }

            @Override
            public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused) throws IOException {
                jsonGenerator.writeFieldName("");
            }
        }

        /**
         * Default conversion for a map object.
         *
         * @param node the node
         * @return the map
         */
        @SuppressWarnings("unchecked")
        public static Map<String, Object> asMap(JsonNode node) {
            return defaultMapper.convertValue(node, Map.class);
        }

        /**
         * Default conversion, use within custom deserializers to "normally" deserialize an object.
         *
         * @param <T>  the generic type
         * @param node the node
         * @param cls  the cls
         * @return the t
         */
        public static <T> T as(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, cls);
        }

        /**
         * Convert node to list of type T.
         *
         * @param <T>  the generic type
         * @param node the node
         * @param cls  the cls
         * @return the list
         */
        public static <T> List<T> asList(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, new TypeReference<List<T>>() {
            });
        }

        public static <T> List<T> asList(JsonNode node, Class<T> cls, ObjectMapper mapper) {
            return mapper.convertValue(node, new TypeReference<List<T>>() {
            });
        }

        @SuppressWarnings("unchecked")
        public static <T> T parseObject(String text, Class<T> cls) {
            try {
                return (T) defaultMapper.readerFor(cls).readValue(text);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Convert node to list of type T.
         *
         * @param <T>  the generic type
         * @param node the node
         * @param cls  the cls
         * @return the sets the
         */
        public static <T> Set<T> asSet(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, new TypeReference<Set<T>>() {
            });
        }

        @SuppressWarnings("unchecked")
        public static <T> T cloneObject(T object) {
            return (T) parseObject(printAsJson(object), object.getClass());
        }

        /**
         * Load an object from an input stream.
         *
         * @param url the input stream
         * @param cls the class
         * @return the object
         * @throws KlabIOException
         */
        public static <T> T load(InputStream url, Class<T> cls) throws KlabIOException {
            try {
                return defaultMapper.readValue(url, cls);
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        /**
         * Load an object from a file.
         *
         * @param file
         * @param cls
         * @return the object
         * @throws KlabIOException
         */
        public static <T> T load(File file, Class<T> cls) throws KlabIOException {
            try {
                return defaultMapper.readValue(file, cls);
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        /**
         * Load an object from a URL.
         *
         * @param url
         * @param cls
         * @return the object
         * @throws KlabIOException
         */
        public static <T> T load(URL url, Class<T> cls) throws KlabIOException {
            try {
                return defaultMapper.readValue(url, cls);
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        /**
         * Serialize an object to a file.
         *
         * @param object
         * @param outFile
         * @throws KlabIOException
         */
        public static void save(Object object, File outFile) throws KlabIOException {
            try {
                defaultMapper.writeValue(outFile, object);
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        public static String asString(Object object) {
            try {
                return defaultMapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("serialization failed: " + e.getMessage());
            }
        }

        /**
         * Serialize the passed object as JSON and pretty-print the resulting code.
         *
         * @param object the object
         * @return the string
         */
        public static String printAsJson(Object object) {

            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
            om.enable(SerializationFeature.WRITE_NULL_MAP_VALUES); // pretty print
            om.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED); // pretty print
            om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

            try {
                return om.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("serialization failed: " + e.getMessage());
            }
        }

        /**
         * Serialize the passed object as JSON and pretty-print the resulting code.
         *
         * @param object the object
         * @param file
         * @return the string
         */
        public static void printAsJson(Object object, File file) {

            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
            om.enable(SerializationFeature.WRITE_NULL_MAP_VALUES); // pretty print
            om.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED); // pretty print
            om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

            try {
                om.writeValue(file, object);
            } catch (Exception e) {
                throw new IllegalArgumentException("serialization failed: " + e.getMessage());
            }
        }

        /**
         * Convert a map resulting from parsing generic JSON (or any other source) to the passed type.
         *
         * @param payload
         * @param cls
         * @return the converted object
         */
        public static <T> T convertMap(Map<?, ?> payload, Class<T> cls) {
            return defaultMapper.convertValue(payload, cls);
        }

    }
}
