package org.integratedmodelling.klab.api.knowledge;

public interface Semantics extends Knowledge {

    /**
     * The name is never null and is meant for human and code consumption. It is is always a simple
     * lowercase identifier using snake_case conventions. In a {@link Observable} it can be set
     * using the 'named' clause. If not a KObservable or 'named' clause is present, a name is
     * computed similarly to {@link #getReferenceName()} but without using disambiguating
     * namespaces, therefore not guaranteeing a 1:1 correspondence to the semantics but with enough
     * predictability to not have to use 'named' in simple situations to refer to the observable.
     * It's always a lowercase valid identifier in k.IM, k.DL, and most languages. Even if 'named'
     * is given, the name may be different from the stated because of disambiguation when the
     * observable is used in a dataflow.
     *
     * @return the name of this observable
     */
    String getName();

    /**
     * The reference name is the default name in generated code so it must follow snake_case
     * language conventions. It only depends on the contents of the observable and it is uniquely
     * related to the semantics: different semantics or observable options (such as units)
     * <em>must</em> generate different reference names. It is not required to be able to regenerate
     * semantics from the reference name. Although the same semantics should generate the same
     * reference name, it may be modified for disambiguation in the observables used when creating
     * dataflows. It does not correspond to {@link #getName()}, which is meant for human
     * consumption.
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
     * Return the raw semantics for this object, normally a Concept. If this is a Concept, return
     * self.
     * 
     * @return
     */
    Semantics semantics();

    /**
     * All semantic knowledge in a worldview should exist in a conceptual domain so this should not
     * return null, although at the moment this is not enforced.
     * 
     * @return
     */
    Semantics domain();

    /**
     * Basic subsumption check. Implementations may decide otherwise, but normally this uses the
     * classified ontology including any inferred hierarchy. Use only when {@link #is(SemanticType)}
     * does not fit the requirements, and cache as appropriate if needed, as this will never be fast
     * enough for massive repetition.
     * 
     * @param other
     * @return
     */
    boolean is(Semantics other);

    /**
     * Logical, fast check for basic semantic type, no reasoning is performed.
     * 
     * @param type
     * @return
     */
    boolean is(SemanticType type);

    /**
     * Abstract status means that observations of this otherwise observable concept cannot exist. It
     * is stated for basic concepts but is attributed by reasoning in conceptual expressions.
     * 
     * @return
     */
    boolean isAbstract();

    /**
     * Semantic assets may be referenced in code using identifiers. Return an appropriate identifier
     * for this concept compatible with the k.LAB language family.
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
     * Return a short label that may contain spaces and may have been assigned by the user. If none
     * exists return {@link #displayName()}.
     * 
     * @return
     */
    String displayLabel();

}
