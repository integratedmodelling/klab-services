package org.integratedmodelling.klab.api.modeler.annotations;

import org.integratedmodelling.klab.api.modeler.UIReactor;

import java.lang.annotation.*;

/**
 * Annotates UI event handler methods in UIReactor interfaces with this to automatically register and react to
 * UI events. Defines the contract of each view/panel interface. Can also be used to add handlers for
 * additional events in implementations.
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
