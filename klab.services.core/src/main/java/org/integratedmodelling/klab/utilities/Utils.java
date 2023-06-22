package org.integratedmodelling.klab.utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.transform.trait.Traits;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.integratedmodelling.kactors.api.IKActorsAction;
import org.integratedmodelling.kactors.api.IKActorsStatement;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.kim.api.IKimConcept.Type;
import org.integratedmodelling.kim.api.IKimStatement;
import org.integratedmodelling.kim.api.IParameters;
import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.knowledge.IObservable;
import org.integratedmodelling.klab.api.knowledge.ISemantic;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.model.IAcknowledgement;
import org.integratedmodelling.klab.api.model.IAnnotation;
import org.integratedmodelling.klab.api.model.IConceptDefinition;
import org.integratedmodelling.klab.api.model.IKimObject;
import org.integratedmodelling.klab.api.model.IModel;
import org.integratedmodelling.klab.data.encoding.JacksonConfiguration;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.utils.Parameters;
import org.integratedmodelling.klab.utils.Range;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Utils extends org.integratedmodelling.klab.api.utils.Utils {

    public static class Annotations {

        public static boolean hasAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
        }

        public static boolean hasAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
        }

        /**
         * Shorthand to check whether the default parameter (list or individual value) of an
         * annotation contains the passed string.
         * 
         * @param string
         * @return
         */
        public static boolean defaultsContain(Annotation annotation, String string) {
            if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) instanceof List) {
                return ((List<?>) annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME)).contains(string);
            } else if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) != null) {
                return annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME).equals(string);
            }
            return false;
        }

        /**
         * Simple methods that are messy to keep writing explicitly
         * 
         * @param annotations
         * @param id
         * @return
         */
        public static Annotation getAnnotation(List<Annotation> annotations, String id) {
            for (Annotation annotation : annotations) {
                if (id.equals(annotation.getName())) {
                    return annotation;
                }
            }
            return null;
        }

        public static Parameters<String> collectVariables(List<Annotation> annotations) {
            Parameters<String> ret = Parameters.create();
            for (Annotation annotation : annotations) {
                if ("var".equals(annotation.getName())) {
                    for (String key : annotation.getNamedKeys()) {
                        ret.put(key, annotation.get(key));
                    }
                }
            }
            return ret;
        }
        

    	/**
    	 * Collect the annotations from an k.IM object and its semantic lineage,
    	 * ensuring that downstream annotations of the same name override those
    	 * upstream. Any string parameter filters the annotations collected.
    	 * 
    	 * @param object
    	 * @return all annotations from upstream
    	 */
    	public static Collection<Annotation> collectAnnotations(Object... objects) {

    		Map<String, Annotation> ret = new HashMap<>();
    		for (Object object : objects) {
    			if (object instanceof KlabAsset) {
    				collectAnnotations((Knowledge) object, ret);
    			} else if (object instanceof Statement) {
    				collectAnnotations((Statement) object, ret);
    			} else if (object instanceof Artifact) {
    				for (Annotation annotation : ((Artifact) object).getAnnotations()) {
    					if (!ret.containsKey(annotation.getName())) {
    						ret.put(annotation.getName(), annotation);
    					}
    				}
    			}
    		}
    		return ret.values();
    	}

    	/**
    	 * Collect the annotations from anything semantic lineage, ensuring that
    	 * downstream annotations of the same name override those upstream.
    	 * 
    	 * @param object
    	 * @return all annotations from upstream
    	 */
    	public static Collection<IAnnotation> collectAnnotations(KlabAsset object) {
    		Map<String, IAnnotation> ret = new HashMap<>();
    		collectAnnotations(object, ret);
    		return ret.values();
    	}

    	private static void collectAnnotations(KlabAsset object, Map<String, Annotation> collection) {

    		for (Annotation annotation : object.getAnnotations()) {
    			if (!collection.containsKey(annotation.getName())) {
    				Annotation a = new AnnotationImpl(annotation);
    				collection.put(a.getName(), a);
    			}
    		}

    		/*
    		 * TODO recurse upwards based on asset type
    		 */
    		
    		if (object instanceof Model) {
//    			collectAnnotations(((Model) object).getObservables().get(0), collection);
    		} else if (object instanceof Instance) {
//    			collectAnnotations(((IModel) object).getObservables().get(0), collection);
			} /*
				 * else if (object instanceof KimConceptDefinition) {
				 * collectAnnotations(((IConceptDefinition) object).getStatement(), collection);
				 * }
				 * 
				 * if (getParent(object) != null) { collectAnnotations(object.getParent(),
				 * collection); }
				 */
    	}

//    	private void collectAnnotations(Knowledge object, Map<String, IAnnotation> collection) {
//
//    		for (Annotation annotation : object.getAnnotations()) {
//    			if (!collection.containsKey(annotation.getName())) {
//    				collection.put(annotation.getName(), annotation);
//    			}
//    		}
//
//    	}
//
//    	private void collectAnnotations(ISemantic object, Map<String, IAnnotation> collection) {
//
//    		if (object instanceof IObservable) {
//
//    			for (IAnnotation annotation : ((IObservable)object).getAnnotations()) {
//    				if (!collection.containsKey(annotation.getName())) {
//    					collection.put(annotation.getName(), annotation);
//    				}
//    			}
//
//    			/*
//    			 * collect from roles, traits and main in this order
//    			 */
//    			// for (IConcept role : Roles.INSTANCE.getRoles(((IObservable)
//    			// object).getType())) {
//    			// collectAnnotations(role, collection);
//    			// }
//    			for (IConcept trait : Traits.INSTANCE.getTraits(((IObservable) object).getType())) {
//    				// FIXME REMOVE ugly hack: landcover is a type, but it's used as an attribute in
//    				// various places so the change
//    				// is deep. This makes landcover colormaps end up in places they shouldn't be.
//    				// TODO check - may not be relevant anymore now that landcover is correctly a type of and not a trait.
//    				if (!trait.getNamespace().equals("landcover")) {
//    					collectAnnotations(trait, collection);
//    				}
//    			}
//
//    			collectAnnotations(((IObservable) object).getType(), collection);
//
//    		} else if (object instanceof IConcept) {
//    			IKimObject mobject = Resources.INSTANCE.getModelObject(object.toString());
//    			if (mobject != null) {
//    				collectAnnotations(mobject, collection);
//    			}
//    			if (((IConcept) object).is(Type.CLASS)) {
//    				// collect annotations from what is classified
//    				IConcept classified = Observables.INSTANCE.getDescribedType((IConcept) object);
//    				if (classified != null) {
//    					collectAnnotations(classified, collection);
//    				}
//    			}
//    			for (IConcept parent : ((IConcept) object).getParents()) {
//    				if (!CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
//    					collectAnnotations(parent, collection);
//    				}
//    			}
//    		}
//    	}
        
    }

    public static class Classpath {

        /**
         * Extract the OWL assets in the classpath (under /knowledge/**) to the specified filesystem
         * directory.
         * 
         * @param destinationDirectory
         * @throws IOException
         */
        public static void extractKnowledgeFromClasspath(File destinationDirectory) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                org.springframework.core.io.Resource[] resources = resolver.getResources("/knowledge/**");
                for (org.springframework.core.io.Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }

                    if (!path.endsWith("owl")) {
                        continue;
                    }

                    String filePath = path.substring(path.indexOf("knowledge/") + "knowledge/".length());

                    int pind = filePath.lastIndexOf('/');
                    if (pind >= 0) {
                        String fileDir = filePath.substring(0, pind);
                        File destDir = new File(destinationDirectory + File.separator + fileDir);
                        destDir.mkdirs();
                    }
                    File dest = new File(destinationDirectory + File.separator + filePath);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }

        /**
         * Only works for a flat hierarchy!
         * 
         * @param resourcePattern
         * @param destinationDirectory
         */
        public static void extractResourcesFromClasspath(String resourcePattern, File destinationDirectory) {

            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                org.springframework.core.io.Resource[] resources = resolver.getResources(resourcePattern);
                for (org.springframework.core.io.Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }
                    String fileName = Files.getFileName(path);
                    File dest = new File(destinationDirectory + File.separator + fileName);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }
    }

    public static class Lang {

        /**
         * Encode the value so that it can be understood in k.IM code.
         * 
         * @param value
         * @return
         */
        public static String encodeValue(Object value) {
            if (value instanceof String) {
                return "'" + ((String) value).replace("'", "\\'") + "'";
            } else if (value instanceof IConcept) {
                return ((IConcept) value).getDefinition();
            } else if (value instanceof Range) {
                return ((Range) value).getKimCode();
            }
            return value == null ? "unknown" : value.toString();
        }

        public static void copyStatementData(IKimStatement source, KimStatementImpl destination) {

            destination.setUri(((IKimStatement) source).getURI());
            destination.setLocationDescriptor(((IKimStatement) source).getLocationDescriptor());
            destination.setNamespace(((IKimStatement) source).getNamespace());

            destination.setMetadata(makeMetadata(source.getMetadata()));

            destination.setFirstLine(source.getFirstLine());
            destination.setLastLine(source.getLastLine());
            destination.setFirstCharOffset(source.getFirstCharOffset());
            destination.setLastCharOffset(source.getLastCharOffset());
            destination.setSourceCode(source.getSourceCode());

            for (IKimAnnotation annotation : source.getAnnotations()) {
                Annotation newAnnotation = makeAnnotation(annotation);
                if ("deprecated".equals(newAnnotation.getName())) {
                    destination.setDeprecated(true);
                    destination.setDeprecation(newAnnotation.get(Annotation.VALUE_PARAMETER_KEY, String.class));
                } else if ("documented".equals(newAnnotation.getName())) {
                    destination.setDocumentationMetadata(newAnnotation);
                }
                destination.getAnnotations().add(newAnnotation);
            }
        }
        
        public static void copyStatementData(IKActorsAction source, KActorsActionImpl destination) {

            destination.setMetadata(makeMetadata(source.getMetadata()));
            destination.setTag(source.getTag());

            destination.setFirstLine(source.getFirstLine());
            destination.setLastLine(source.getLastLine());
            destination.setFirstCharOffset(source.getFirstCharOffset());
            destination.setLastCharOffset(source.getLastCharOffset());
            destination.setSourceCode(source.getSourceCode());

            for (IKimAnnotation annotation : source.getAnnotations()) {
                Annotation newAnnotation = makeAnnotation(annotation);
                if ("deprecated".equals(newAnnotation.getName())) {
                    destination.setDeprecated(true);
                    destination.setDeprecation(newAnnotation.get(Annotation.VALUE_PARAMETER_KEY, String.class));
                }
                destination.getAnnotations().add(newAnnotation);
            }
        }

        public static void copyStatementData(IKActorsStatement source, KActorsStatementImpl destination) {

            destination.setMetadata(makeMetadata(source.getMetadata()));

            destination.setTag(source.getTag());
            destination.setFirstLine(source.getFirstLine());
            destination.setLastLine(source.getLastLine());
            destination.setFirstCharOffset(source.getFirstCharOffset());
            destination.setLastCharOffset(source.getLastCharOffset());
            destination.setSourceCode(source.getSourceCode());

            for (IKimAnnotation annotation : source.getAnnotations()) {
                Annotation newAnnotation = makeAnnotation(annotation);
                if ("deprecated".equals(newAnnotation.getName())) {
                    destination.setDeprecated(true);
                    destination.setDeprecation(newAnnotation.get(Annotation.VALUE_PARAMETER_KEY, String.class));
                }
                destination.getAnnotations().add(newAnnotation);
            }
        }

        public static Annotation makeAnnotation(IKimAnnotation annotation) {
            AnnotationImpl ret = new AnnotationImpl();
            ret.setName(annotation.getName());
            ret.putAll(annotation.getParameters());
            return ret;
        }

        public static Metadata makeMetadata(IParameters<String> metadata) {
            MetadataImpl ret = new MetadataImpl();
            if (metadata != null) {
                ret.putAll(metadata);
            }
            return ret;
        }
    }

    public static class YAML {

        static ObjectMapper defaultMapper;

        static {
            defaultMapper = new ObjectMapper(new YAMLFactory());
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
            public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused)
                    throws IOException, JsonProcessingException {
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
         * @param <T> the generic type
         * @param node the node
         * @param cls the cls
         * @return the t
         */
        public static <T> T as(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, cls);
        }

        /**
         * Convert node to list of type T.
         *
         * @param <T> the generic type
         * @param node the node
         * @param cls the cls
         * @return the list
         */
        public static <T> List<T> asList(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, new TypeReference<List<T>>(){
            });
        }

        public static <T> List<T> asList(JsonNode node, Class<T> cls, ObjectMapper mapper) {
            return mapper.convertValue(node, new TypeReference<List<T>>(){
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
         * @param <T> the generic type
         * @param node the node
         * @param cls the cls
         * @return the sets the
         */
        public static <T> Set<T> asSet(JsonNode node, Class<T> cls) {
            return defaultMapper.convertValue(node, new TypeReference<Set<T>>(){
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
         * Convert a map resulting from parsing generic JSON (or any other source) to the passed
         * type.
         * 
         * @param payload
         * @param cls
         * @return the converted object
         */
        public static <T> T convertMap(Map<?, ?> payload, Class<T> cls) {
            return defaultMapper.convertValue(payload, cls);
        }

    }

    public static class Files extends org.integratedmodelling.klab.api.utils.Utils.Files {

        public static void deleteDirectory(File pdir) {
            try {
                FileUtils.deleteDirectory(pdir);
            } catch (IOException e) {
                throw new KIOException(e);
            }
        }

        public static boolean deleteQuietly(File pdir) {
            return FileUtils.deleteQuietly(pdir);
        }

    }

    public static class Markdown {

    }

    public static class Git {

        public static final String MAIN_BRANCH = "master";

        /**
         * Clone.
         *
         * @param gitUrl the git url
         * @param directory the directory
         * @param removeIfExisting the remove if existing
         * @return the string
         * @throws KlabException the klab exception
         */
        public static String clone(String gitUrl, File directory, boolean removeIfExisting) throws KlabException {

            String dirname = URLs.getURLBaseName(gitUrl);

            File pdir = new File(directory + File.separator + dirname);
            if (pdir.exists()) {
                if (removeIfExisting) {
                    try {
                        Files.deleteDirectory(pdir);
                    } catch (Throwable e) {
                        throw new KlabIOException(e);
                    }
                } else {
                    throw new KlabIOException("git clone: directory " + pdir + " already exists");
                }
            }

            String[] pdefs = gitUrl.split("#");
            String branch;
            if (pdefs.length < 2) {
                branch = MAIN_BRANCH;
            } else {
                branch = branchExists(pdefs[0], pdefs[1]) ? pdefs[1] : MAIN_BRANCH;
            }
            String url = pdefs[0];

            Logging.INSTANCE.info("cloning Git repository " + url + " branch " + branch + " ...");

            try (org.eclipse.jgit.api.Git result = org.eclipse.jgit.api.Git.cloneRepository().setURI(url).setBranch(branch)
                    .setDirectory(pdir).call()) {

                Logging.INSTANCE.info("cloned Git repository: " + result.getRepository());

                if (!branch.equals(MAIN_BRANCH)) {
                    result.checkout().setName(branch).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/" + branch).call();

                    Logging.INSTANCE.info("switched repository: " + result.getRepository() + " to branch " + branch);
                }

            } catch (Throwable e) {
                throw new KlabIOException(e);
            }

            return dirname;
        }

        /**
         * Pull local repository in passed directory.
         *
         * @param localRepository main directory (containing .git/)
         * @throws KlabException the klab exception
         */
        public static void pull(File localRepository) throws KlabException {

            try (Repository localRepo = new FileRepository(localRepository + File.separator + ".git")) {
                try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(localRepo)) {

                    Logging.INSTANCE.info("fetch/merge changes in repository: " + git.getRepository());

                    PullCommand pullCmd = git.pull();
                    pullCmd.call();

                } catch (Throwable e) {
                    throw new KlabIOException("error pulling repository " + localRepository + ": " + e.getLocalizedMessage());
                }
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        /**
         * If a Git repository with the repository name corresponding to the URL exists in
         * gitDirectory, pull it from origin; otherwise clone it from the passed Git URL.
         * 
         * TODO: Assumes branch is already set correctly if repo is pulled. Should check branch and
         * checkout if necessary.
         *
         * @param gitUrl the git url
         * @param gitDirectory the git directory
         * @return the string
         * @throws KlabException the klab exception
         */
        public static String requireUpdatedRepository(String gitUrl, File gitDirectory) throws KlabException {

            String repositoryName = URLs.getURLBaseName(gitUrl);

            File repoDir = new File(gitDirectory + File.separator + repositoryName);
            File gitDir = new File(repoDir + File.separator + ".git");

            if (gitDir.exists() && gitDir.isDirectory() && gitDir.canRead() && repoDir.exists()) {

                pull(repoDir);
                /*
                 * TODO check branch and switch/pull if necessary
                 */
            } else {
                if (gitDir.exists()) {
                    Files.deleteQuietly(gitDir);
                }
                clone(gitUrl, gitDirectory, true);
            }

            return repositoryName;
        }

        /**
         * Checks if is remote git URL.
         *
         * @param string the string
         * @return a boolean.
         */
        public static boolean isRemoteGitURL(String string) {
            return string.startsWith("http:") || string.startsWith("git:") || string.startsWith("https:")
                    || string.startsWith("git@");
        }

        /**
         * Check if remote branch exists
         * 
         * @param gitUrl the remote repository
         * @param branch the branch (without refs/heads/)
         * @return true if branch exists
         */
        public static boolean branchExists(String gitUrl, String branch) {
            final LsRemoteCommand lsCmd = new LsRemoteCommand(null);
            lsCmd.setRemote(gitUrl);
            try {
                return lsCmd.call().stream().filter(ref -> ref.getName().equals("refs/heads/" + branch)).count() == 1;
            } catch (GitAPIException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public static class Maps {

    }

    public static class Templates {

    }

    public static class Wildcards {

    }

    public static class Network {

        /**
         * Checks if is server alive.
         *
         * @param host the host
         * @return a boolean.
         */
        static public boolean isAlive(String host) {

            try {
                if (InetAddress.getByName(host).isReachable(200)) {
                    return true;
                }
            } catch (Exception e) {
            }
            return false;
        }

        /**
         * Port available.
         *
         * @param port the port
         * @return a boolean.
         */
        public static boolean portAvailable(int port) {

            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                ss = new ServerSocket(port);
                ss.setReuseAddress(true);
                ds = new DatagramSocket(port);
                ds.setReuseAddress(true);
                return true;
            } catch (Exception e) {
                e.getMessage();
            } finally {
                if (ds != null) {
                    ds.close();
                }

                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException e) {
                        /* should not be thrown */
                    }
                }
            }

            return false;
        }

        /**
         * Call with "-" as a parameter to get the typical MAC address string. Otherwise use another
         * string to get a unique machine identifier that can be customized.
         *
         * @param sep the sep
         * @return MAC address
         */
        public static String getMACAddress(String sep) {

            InetAddress ip;
            String ret = null;
            try {

                ip = InetAddress.getLocalHost();
                NetworkInterface network = NetworkInterface.getByInetAddress(ip);
                byte[] mac = network.getHardwareAddress();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? sep : ""));
                }
                ret = sb.toString();

            } catch (Exception e) {
                throw new KlabIOException(e);
            }

            return ret;
        }

    }

}