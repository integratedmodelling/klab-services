package org.integratedmodelling.klab.api.services.runtime.kactors;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;

/**
 * Interface for those extension action classes that are declared by tagging classes with
 * {@link Action} within Java-specified behaviors.
 * 
 * @author Ferd
 *
 */
public interface ActionExecutor {

    /**
     * An action implementing this interface will be saved in the actor where the calling behavior
     * is running and be enabled to receive later messages just like an actor.
     * 
     * @author Ferd
     *
     */
    interface Actor {

        /**
         * The actor name, normally established using a tag.
         * 
         * @return
         */
        String getName();

        /**
         * Done by the calling actor using arguments and/or metadata
         * 
         * @param name
         */
        void setName(String name);

        /**
         * Implement the response to a messages sent in k.Actors.
         * 
         * @param message
         * @param scope
         */
        void onMessage(VM.Message message, VM.Scope scope);

    }

    /**
     * 
     * @return
     */
    boolean isSynchronized();

    /**
     * 
     * @return
     */
    Parameters<String> getArguments();

    void run(VM.Scope scope);

    /**
     * A component is an Actor that reacts through an MVC pattern.
     * 
     * @author Ferd
     *
     */
    public interface Component extends Actor {

        /**
         * Return a descriptor of the view component that will provide the view for this actor.
         * 
         * @return
         */
        public ViewComponent getViewComponent();

    }

}
