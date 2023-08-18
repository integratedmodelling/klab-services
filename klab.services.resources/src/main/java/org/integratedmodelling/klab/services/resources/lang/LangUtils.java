package org.integratedmodelling.klab.services.resources.lang;

import org.integratedmodelling.kactors.api.IKActorsAction;
import org.integratedmodelling.kactors.api.IKActorsStatement;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.kim.api.IKimStatement;
import org.integratedmodelling.kim.api.IParameters;
import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
import org.integratedmodelling.klab.utils.Range;

public class LangUtils {

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
