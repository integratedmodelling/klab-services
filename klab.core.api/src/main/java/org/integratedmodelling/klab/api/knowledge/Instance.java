package org.integratedmodelling.klab.api.knowledge;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * The operational peer of KimKnowledge (former IAcknowledgement), i.e. an instance specified
 * through a k.IM <code>observe</code> statement, i.e. an {@link DescriptionType#ACKNOWLEDGEMENT}.
 * Not an OWL/RDF instance, for which the <code>define</code> mechanism is involved instead.
 * 
 * @author Ferd
 *
 */
public interface Instance extends Knowledge {

    /**
     * Models are in namespaces, which are relevant to organization and scoping.
     * 
     * @return
     */
    String getNamespace();

    /**
     * The concept this observes, a direct observable.
     *
     * @return observed concept
     */
    Observable getObservable();

    /**
     * Any objects declared within the scope of this one.
     * 
     * @return
     */
    List<Instance> getChildren();

    /**
     * Specifications for states are reported as observables, whose {@link Observable#getValue()} is
     * guaranteed not to return null as they must be fully specified within the instance definition.
     *
     * @return all stated indirect observations for the resulting observation.
     */
    List<Observable> getStates();

    /**
     * The scale of observation of this instance.
     * 
     * @return
     */
    Scale getScale();
}
