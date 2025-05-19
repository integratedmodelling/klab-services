package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Pair;

import java.util.Collection;
import java.util.List;

/**
 * The syntactic peer of a k.LAB namespace.
 *
 * @author ferdinando.villa
 */
public interface KimOntology extends KlabDocument<KimConceptStatement> {

    /**
     * Use ONLY to compare with getDomain() to check if this is the one and only worldview root.
     */
    public static final KimConcept rootDomain = null;

    /**
     * Return all the worldview ontology URNs that are referenced here and must be known before loading the
     * knowledge in this one.
     *
     * @return IDs of namespaces we do not agree with
     */
    Collection<String> getImportedOntologies();

    /**
     * Imports of external OWL ontologies
     *
     * @return
     */
    List<Pair<String, String>> getOwlImports();

    /**
     * Import of vocabularies from resources, as resource URN -> list of vocabularies from that resource.
     *
     * @return
     */
    List<Pair<String, List<String>>> getVocabularyImports();

    /**
     * The domain concept, if stated. Stating it is mandatory except for the root domain, which can only be
     * stated in the root of the worldview and brings with itself potential OWL imports. Check for root domain
     * using <code>getDomain == KimOntology.rootDomain</code>.
     *
     * @return
     */
    KimConcept getDomain();

}
