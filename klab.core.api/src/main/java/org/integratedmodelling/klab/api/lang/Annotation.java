package org.integratedmodelling.klab.api.lang;

import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;

/**
 * Annotation from code, normally starting out as a @xxx annotation in a parsed language (all three
 * k.LAB languages support Java-style annotations) but it can also be used for other purposes. It's
 * essentially a named {@link Parameters} object with a name; a content class attribute is also
 * added to capture typed objects like in k.IM's <code>define</code> statements. Does not preserve
 * the relationship with the k.IM, k.DL or k.Actors statement after construction.
 * <p>
 * Serializes correctly only if with a Jackson object mapper instrumented with specialized
 * serializers.
 * 
 * @author Ferd
 *
 */
public interface Annotation extends Parameters<String> {

    public static final String VALUE_PARAMETER_KEY = "value";
    
    /**
     * The name of the annotation.
     * 
     * @return
     */
    String getName();

    String getContentClass();
    
    @SuppressWarnings("unchecked")
    public static Annotation create(String name, Object... o) {
        Map<String, Object> inp = new LinkedHashMap<String, Object>();
        if (o != null) {
            for (int i = 0; i < o.length; i++) {
                if (o[i] instanceof Map) {
                    inp.putAll((Map) o[i]);
                } else if (o[i] != null) {
                    if (!ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
                        inp.put(o[i].toString(), o[i + 1]);
                    }
                    i++;
                }
            }
        }
        AnnotationImpl ret = new AnnotationImpl(inp);
        ret.setName(name);
        return ret;
    }

    //
    // /**
    // * Specialized get() that converts IKimConcepts left over as forward references into concepts
    // at
    // * the time of use. It could also parse KExpressions into IExpressions but this is not done at
    // * this time.
    // */
    // Object getDeclared(Object key);
    //
    // /**
    // * Typed version of getDeclared().
    // *
    // * @param name
    // * @param cls
    // * @return
    // */
    // <K> K getDeclared(String name, Class<? extends K> cls);
}
