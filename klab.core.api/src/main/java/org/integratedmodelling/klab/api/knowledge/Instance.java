//package org.integratedmodelling.klab.api.knowledge;
//
//import java.util.List;
//
//import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
//import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
//
///**
// * An Instance defines an {@link org.integratedmodelling.klab.api.knowledge.observation.Observation} and is a
// * {@link Resolvable} that can be specified in k.IM using the <code>define</code> mechanism with class
// * <code>observation</code>. Its resolution mode is {@link DescriptionType#ACKNOWLEDGEMENT} as it is fully
// * specified, but the resolution should imply resolving the individual object after it has been instantiated
// * by resolving the statement. It does not automatically specify an OWL/RDF instance unless the resolver is
// * instructed to do so using annotations to be determined.
// *
// * @author Ferd
// */
//public interface Instance extends KlabStatement, Resolvable {
//
//    /**
//     * Models are in namespaces, which are relevant to organization and scoping.
//     *
//     * @return
//     */
//    String getNamespace();
//
//    /**
//     * The concept this observes, a direct observable.
//     *
//     * @return observed concept
//     */
//    Observable getObservable();
//
//    /**
//     * Any objects declared within the scope of this one.
//     *
//     * @return
//     */
//    List<Instance> getChildren();
//
//    /**
//     * Specifications for states are reported as observables, whose {@link Observable#getValue()} is
//     * guaranteed not to return null as they must be fully specified within the instance definition.
//     *
//     * @return all stated indirect observations for the resulting observation.
//     */
//    List<Observable> getStates();
//
//    /**
//     * The scale of observation of this instance.
//     *
//     * @return
//     */
//    Scale getScale();
//}
