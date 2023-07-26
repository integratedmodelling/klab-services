package org.integratedmodelling.klab.utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.integratedmodelling.kactors.api.IKActorsAction;
import org.integratedmodelling.kactors.api.IKActorsStatement;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.kim.api.IKimStatement;
import org.integratedmodelling.kim.api.IParameters;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IConcept;
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
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.model.IAnnotation;
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

	public static class Collections {

		public static <T1, T2> List<T1> sortMatching(List<T1> toSort, List<T2> toMatch, Comparator<T2> comparator) {
			MatchedSorter<T1, T2> sorter = new MatchedSorter<>(toSort, toMatch, comparator);
			return sorter.getSortedValues();
		}

		public static <T1, T2> List<Pair<T1, T2>> sortMatchingPairs(List<T1> toSort, List<T2> toMatch,
				Comparator<T2> comparator) {
			MatchedSorter<T1, T2> sorter = new MatchedSorter<>(toSort, toMatch, comparator);
			List<Pair<T1, T2>> ret = new ArrayList<>();
			for (int i = 0; i < sorter.getSortedCriteria().size(); i++) {
				ret.add(Pair.of(sorter.getSortedValues().get(i), sorter.getSortedCriteria().get(i)));
			}
			return ret;
		}

		@SafeVarargs
		public static <T> List<T> join(Collection<T>... resources) {
			List<T> ret = new ArrayList<>();
			for (Collection<T> list : resources) {
				ret.addAll(list);
			}
			return ret;
		}

		@SafeVarargs
		public static <T> List<T> join(Iterable<T>... resources) {
			List<T> ret = new ArrayList<>();
			for (Iterable<T> list : resources) {
				for (T o : list) {
					ret.add(o);
				}
			}
			return ret;
		}

		/**
		 * Pack the arguments into a collection; if any argument is a collection, add
		 * its elements but do not unpack below the first level.
		 * 
		 * @param objects
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public static <T> Collection<T> shallowCollection(Object... objects) {
			List<T> ret = new ArrayList<>();
			for (Object o : objects) {
				if (o instanceof Collection) {
					ret.addAll((Collection<T>) o);
				} else {
					ret.add((T) o);
				}
			}
			return ret;
		}

		/**
		 * Pack the arguments into a collection; if any argument is a collection, unpack
		 * its elements recursively so that no collections remain.
		 * 
		 * @param objects
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public static <T> Collection<T> flatCollection(T... objects) {
			List<T> ret = new ArrayList<>();
			addToCollection(ret, objects);
			return ret;
		}

		@SuppressWarnings("unchecked")
		private static <T> void addToCollection(List<T> ret, T... objects) {
			for (T o : objects) {
				if (o instanceof Collection) {
					for (T oo : ((Collection<T>) o)) {
						addToCollection(ret, oo);
					}
				} else if (o != null && o.getClass().isArray()) {
					for (int i = 0; i < Array.getLength(o); i++) {
						addToCollection(ret, (T) Array.get(o, i));
					}
				} else {
					ret.add(o);
				}
			}
		}

		@SuppressWarnings("unchecked")
		public static <T> Set<T> asSet(Collection<T> items) {
			if (items instanceof Set) {
				return (Set<T>) items;
			}

			if (items instanceof Enum) {
				return EnumSet.copyOf((Collection) items);
			}

			return new HashSet<>(items);
		}
	}

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
		 * Shorthand to check whether the default parameter (list or individual value)
		 * of an annotation contains the passed string.
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

			if (object instanceof KimObservable) {

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
			} else if (object instanceof KimConcept) {
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
			} else if (object instanceof Concept) {
				// TODO
			} else if (object instanceof Observable) {
				// TODO
			} else if (object instanceof Model) {
				collectAnnotations(((Model) object).getObservables().get(0), collection);
			} else if (object instanceof Instance) {
				collectAnnotations(((Instance) object).getObservable(), collection);
			}
//
//			if (getParent(object) != null) {
//				collectAnnotations(object.getParent(), collection);
//			}

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
		 * Extract the OWL assets in the classpath (under /knowledge/**) to the
		 * specified filesystem directory.
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
					.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
					.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
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
		 * Default conversion, use within custom deserializers to "normally" deserialize
		 * an object.
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
		 * Convert a map resulting from parsing generic JSON (or any other source) to
		 * the passed type.
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

		public static void copyDirectory(File directory, File backupDir) {
			try {
				FileUtils.copyDirectory(directory, backupDir);
			} catch (IOException e) {
				throw new KIOException(e);
			}
		}

		public static void writeStringToFile(String string, File file) {
			try {
				FileUtils.write(file, string, UTF_8_Y);
			} catch (IOException e) {
				throw new KIOException(e);
			}
		}

	}

	public static class Markdown {

	}

	public static class Git {

		public static final String MAIN_BRANCH = "master";

		/**
		 * Clone.
		 *
		 * @param gitUrl           the git url
		 * @param directory        the directory
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

			try (org.eclipse.jgit.api.Git result = org.eclipse.jgit.api.Git.cloneRepository().setURI(url)
					.setBranch(branch).setDirectory(pdir).call()) {

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
					PullResult result = pullCmd.call();
//					return result != null && result.getFetchResult() != null && result.getFetchResult().

				} catch (Throwable e) {
					throw new KlabIOException(
							"error pulling repository " + localRepository + ": " + e.getLocalizedMessage());
				}
			} catch (IOException e) {
				throw new KlabIOException(e);
			}
		}

		/**
		 * If a Git repository with the repository name corresponding to the URL exists
		 * in gitDirectory, pull it from origin; otherwise clone it from the passed Git
		 * URL.
		 * 
		 * TODO: Assumes branch is already set correctly if repo is pulled. Should check
		 * branch and checkout if necessary.
		 *
		 * @param gitUrl       the git url
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

		/**
		 * Check for matching of simple wildcard patterns using * and ? as per
		 * conventions.
		 *
		 * @param string  the string
		 * @param pattern the pattern
		 * @return a boolean.
		 */
		public static boolean matches(String string, String pattern) {
			return new WildcardMatcher().match(string, pattern);
		}
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
		 * Call with "-" as a parameter to get the typical MAC address string. Otherwise
		 * use another string to get a unique machine identifier that can be customized.
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

	/**
	 * Sorts an array according to the sort order of a matched other using a given
	 * comparator. Makes up for the lovely matched sort available in C# and missing
	 * in Java collections.
	 *
	 * @author Ferd
	 * @version $Id: $Id
	 * @param <T1> the generic type
	 * @param <T2> the generic type
	 */
	private static class MatchedSorter<T1, T2> {

		List<T1> _a;
		List<T2> _criteria;
		Comparator<T2> _comparator;

		/**
		 * Instantiates a new matched sorter.
		 *
		 * @param a          the a
		 * @param criteria   the criteria
		 * @param comparator the comparator
		 */
		public MatchedSorter(List<T1> a, List<T2> criteria, Comparator<T2> comparator) {
			_a = a;
			_criteria = criteria;
			_comparator = comparator;
			if (a.size() > 0) {
				quicksort(0, a.size() - 1);
			}
		}

		/**
		 * Gets the sorted values.
		 *
		 * @return the sorted values
		 */
		public List<T1> getSortedValues() {
			return _a;
		}

		/**
		 * Gets the sorted criteria.
		 *
		 * @return the sorted criteria
		 */
		public List<T2> getSortedCriteria() {
			return _criteria;
		}

		private void swap(int lft, int rt) {
			T1 temp;
			temp = _a.get(lft);
			_a.set(lft, _a.get(rt));
			_a.set(rt, temp);

			T2 otemp = _criteria.get(lft);
			_criteria.set(lft, _criteria.get(rt));
			_criteria.set(rt, otemp);
		}

		private void quicksort(int low, int high) {

			int i = low, j = high;

			// Get the pivot element from the middle of the list
			T2 pivot = _criteria.get(low + (high - low) / 2);

			// Divide into two lists
			while (i <= j) {
				// If the current value from the left list is smaller then the pivot
				// element then get the next element from the left list
				while (_comparator.compare(_criteria.get(i), pivot) < 0) {
					i++;
				}
				// If the current value from the right list is larger then the pivot
				// element then get the next element from the right list
				while (_comparator.compare(_criteria.get(j), pivot) > 0) {
					j--;
				}

				// If we have found a values in the left list which is larger then
				// the pivot element and if we have found a value in the right list
				// which is smaller then the pivot element then we exchange the
				// values.
				// As we are done we can increase i and j
				if (i <= j) {
					swap(i, j);
					i++;
					j--;
				}
			}

			// Recursion
			if (low < j)
				quicksort(low, j);
			if (i < high)
				quicksort(i, high);
		}

	}

	/**
	 * This class is a utility for finding the String based upon the wild card
	 * pattern. For example if the actual String "John" and your wild card pattern
	 * is "J*", it will return true.
	 *
	 * @author Debadatta Mishra(PIKU)
	 */
	private static class WildcardMatcher {
		/**
		 * String variable for wild card pattern
		 */
		private String wildCardPatternString;
		/**
		 * Variable for the length of the wild card pattern
		 */
		private int wildCardPatternLength;
		/**
		 * Boolean variable to for checking wild cards, It is false by default.
		 */
		private boolean ignoreWildCards;
		/**
		 * Boolean variable to know whether the pattern has leading * or not.
		 */
		private boolean hasLeadingStar;
		/**
		 * Boolean variable to know whether the pattern has * at the end.
		 */
		private boolean hasTrailingStar;
		/**
		 * A String array to contain chars
		 */
		private String charSegments[];
		/**
		 * Variable to maintain the boundary of the String.
		 */
		private int charBound;

		/**
		 * Default constructor.
		 */
		public WildcardMatcher() {
			ignoreWildCards = false;
		}

		/**
		 * This is the public method which will be called to match a String with the
		 * wild card pattern.
		 *
		 * @param actualString   of type String indicating the String to be matched
		 * @param wildCardString of type String indicating the wild card String
		 * @return true if matches
		 */
		public boolean match(String actualString, String wildCardString) {
			wildCardPatternString = wildCardString;
			wildCardPatternLength = wildCardString.length();
			setWildCards();
			return doesMatch(actualString, 0, actualString.length());
		}

		/**
		 * This method is used to set the wild cards. The pattern for the wild card may
		 * be *, ? or a combination of *,? and alphanumeric character.
		 */
		private void setWildCards() {
			if (wildCardPatternString.startsWith("*")) {
				hasLeadingStar = true;
			}
			if (wildCardPatternString.endsWith("*") && wildCardPatternLength > 1) {
				hasTrailingStar = true;
			}
			Vector temp = new Vector();
			int pos = 0;
			StringBuffer buf = new StringBuffer();
			while (pos < wildCardPatternLength) {
				char c = wildCardPatternString.charAt(pos++);
				switch (c) {
				case 42: // It refers to *
					if (buf.length() > 0) {
						temp.addElement(buf.toString());
						charBound += buf.length();
						buf.setLength(0);
					}
					break;
				case 63: // It refers to ?
					buf.append('\0');
					break;

				default:
					buf.append(c);
					break;
				}
			}
			if (buf.length() > 0) {
				temp.addElement(buf.toString());
				charBound += buf.length();
			}
			charSegments = new String[temp.size()];
			temp.copyInto(charSegments);
		}

		/**
		 * This is the actual method which makes comparison with the wild card pattern.
		 * 
		 * @param text       of type String indicating the actual String
		 * @param startPoint of type int indicating the start index of the String
		 * @param endPoint   of type int indicating the end index of the String
		 * @return true if matches.
		 */
		private final boolean doesMatch(String text, int startPoint, int endPoint) {
			int textLength = text.length();

			if (startPoint > endPoint) {
				return false;
			}
			if (ignoreWildCards) {
				return endPoint - startPoint == wildCardPatternLength
						&& wildCardPatternString.regionMatches(false, 0, text, startPoint, wildCardPatternLength);
			}
			int charCount = charSegments.length;
			if (charCount == 0 && (hasLeadingStar || hasTrailingStar)) {
				return true;
			}
			if (startPoint == endPoint) {
				return wildCardPatternLength == 0;
			}
			if (wildCardPatternLength == 0) {
				return startPoint == endPoint;
			}
			if (startPoint < 0) {
				startPoint = 0;
			}
			if (endPoint > textLength) {
				endPoint = textLength;
			}
			int currPosition = startPoint;
			int bound = endPoint - charBound;
			if (bound < 0) {
				return false;
			}
			int i = 0;
			String currString = charSegments[i];
			int currStringLength = currString.length();
			if (!hasLeadingStar) {
				if (!isExpressionMatching(text, startPoint, currString, 0, currStringLength)) {
					return false;
				}
				i++;
				currPosition += currStringLength;
			}
			if (charSegments.length == 1 && !hasLeadingStar && !hasTrailingStar) {
				return currPosition == endPoint;
			}
			for (; i < charCount; i++) {
				currString = charSegments[i];
				int k = currString.indexOf('\0');
				int currentMatch;
				currentMatch = getTextPosition(text, currPosition, endPoint, currString);
				if (k < 0) {
					if (currentMatch < 0) {
						return false;
					}
				}
				currPosition = currentMatch + currString.length();
			}
			if (!hasTrailingStar && currPosition != endPoint) {
				int clen = currString.length();
				return isExpressionMatching(text, endPoint - clen, currString, 0, clen);
			}
			return i == charCount;
		}

		/**
		 * This method finds the position of the String based upon the wild card
		 * pattern. It also considers some special case like *.* and ???.? and their
		 * combination.
		 * 
		 * @param textString of type String indicating the String
		 * @param start      of type int indicating the start index of the String
		 * @param end        of type int indicating the end index of the String
		 * @param posString  of type indicating the position after wild card
		 * @return the position of the String
		 */
		private final int getTextPosition(String textString, int start, int end, String posString) {
			/*
			 * String after *
			 */
			int plen = posString.length();
			int max = end - plen;
			int position = -1;
			int i = textString.indexOf(posString, start);
			/*
			 * The following conditions are met for the special case where user give *.*
			 */
			if (posString.equals(".")) {
				position = 1;
			}
			if (i == -1 || i > max) {
				position = -1;
			} else {
				position = i;
			}
			return position;
		}

		/**
		 * This method is used to match the wild card with the String based upon the
		 * start and end index.
		 * 
		 * @param textString        of type String indicating the String
		 * @param stringStartIndex  of type int indicating the start index of the
		 *                          String.
		 * @param patternString     of type String indicating the pattern
		 * @param patternStartIndex of type int indicating the start index
		 * @param length            of type int indicating the length of pattern
		 * @return true if matches otherwise false
		 */
		private boolean isExpressionMatching(String textString, int stringStartIndex, String patternString,
				int patternStartIndex, int length) {
			while (length-- > 0) {
				char textChar = textString.charAt(stringStartIndex++);
				char patternChar = patternString.charAt(patternStartIndex++);
				if ((ignoreWildCards || patternChar != 0) && patternChar != textChar
						&& (textChar != patternChar && textChar != patternChar)) {
					return false;
				}
			}
			return true;
		}

	}
}
