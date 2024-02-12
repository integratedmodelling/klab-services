package org.integratedmodelling.klab.api.view.annotations;

import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

import java.lang.annotation.*;

/**
 * Annotates UI event handler methods in UIReactor interfaces with this to automatically register and react to
 * UI events. Defines the contract of each view/panel interface. Can also be used to add handlers for
 * additional events in implementations.
 * <p>
 * The annotated method must take parameters of the type specified in the associated event's payload class. In
 * addition, they may have other parameters of type {@link org.integratedmodelling.klab.api.view.UIController}
 * for the controller, {@link UIReactor} for the sender, {@link org.integratedmodelling.klab.api.scope.Scope}
 * and {@link NavigableAsset} in their different
 * declinations, and potentially others that are automatically wired to the context of calling.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UIEventHandler {

    /**
     * The type of event this method handles. Parameters passed to the method must be assignable to the
     * payload class(es) associated with the event type and in the same order.
     *
     * @return
     */
    UIReactor.UIEvent value() default UIReactor.UIEvent.AnyEvent;

}
