package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.lang.Annotation;

import java.util.List;

/**
 * Semantics is any knowledge that holds computable meaning.
 *
 * @author Ferd
 */
public interface Semantics extends Knowledge {

    /**
     * The name is never null and is meant for human and code consumption. It is is always a simple lowercase
     * identifier using snake_case conventions. In a {@link Observable} it can be set using the 'named'
     * clause. If a 'named' clause is not present, a name is computed similarly to {@link #getReferenceName()}
     * but without using disambiguating namespaces, therefore not guaranteeing a 1:1 correspondence to the
     * semantics, but is predictabile enough to not have to use 'named' in simple situations (single concepts
     * or simple expressions) to refer to the observable. It's always a lowercase valid identifier in k.IM,
     * k.DL, and most languages. Even if 'named' is given, the name may be different from the stated because
     * of possible disambiguation when the observable is used in a dataflow or in a model.
     *
     * @return the name of this observable
     */
    String getName();

    /**
     * The reference name is the default name in generated code so it must follow snake_case language
     * conventions. It only depends on the contents of the observable and it is uniquely related to the
     * semantics: different semantics or observable options <em>must</em> generate different reference names.
     * This does not apply to mediation in values such as units. Implementations are not required to be able
     * to regenerate semantics from the reference name. It does not correspond to {@link #getName()}, which is
     * meant for human consumption.
     *
     * @return the reference name of this observable
     */
    String getReferenceName();

    /**
     * Semantics always resides in a namespace.
     *
     * @return
     */
    String getNamespace();

    /**
     * Semantics can carry annotations from its definition or reference.
     *
     * @return
     */
    List<Annotation> getAnnotations();

    /**
     * Logical, fast check for basic semantic type, no reasoning is performed.
     *
     * @param type
     * @return
     */
    boolean is(SemanticType type);

    /**
     * Abstract status means that observations of this otherwise observable concept cannot exist. It is stated
     * for basic concepts but is attributed by reasoning in conceptual expressions.
     *
     * @return
     */
    boolean isAbstract();

    /**
     * Semantic assets may be referenced in code using identifiers. Return an appropriate identifier for this
     * concept compatible with the k.LAB language family.
     *
     * @return
     */
    String codeName();

    /**
     * Return a single name usable for human consumption.
     *
     * @return
     */
    String displayName();

    /**
     * Return a short label that may contain spaces and may have been assigned by the user. If none exists
     * return {@link #displayName()}.
     *
     * @return
     */
    String displayLabel();

    /**
     * Anything semantic can be seen as a concept.
     *
     * @return
     */
    Concept asConcept();

    /**
     * Generic semantics expects to be resolved extensively - i.e., through its subtypes corresponding to the
     * resolution of generic components (concepts declared with <code>any</code>, <code>all</code> or
     * <code>no</code>, i.e. with {@link Concept#getQualifier()} != null). Generic semantics is a semantic
     * query and is always abstract by definition.
     *
     * @return true if the semantics is generic, directly or through generic components.
     */
    boolean isGeneric();


}
