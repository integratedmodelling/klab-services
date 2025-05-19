package org.integratedmodelling.klab.services.reasoner.authorities;

/**
 * An authority that enables defining patterns whose semantics depend on configured authorities and bridges
 * out to others for the definition of the individual patterns. Enables creating complex pattern structures
 * through a (relatively) simple pattern language. Each pattern can be arranged internally or externally
 * according to the extent in which the respective configuration is seen - such as compresence,
 * subsequentiality/adjacenty, relative proportions etc.
 * <p>
 * The reasoning on patterns uses a specific pattern ontology and each pattern is converted into the specified
 * axioms so that all inference can be made directly using the OWL reasoner.
 */
public class PatternAuthority {
}
