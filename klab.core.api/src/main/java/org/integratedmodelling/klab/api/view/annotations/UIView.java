package org.integratedmodelling.klab.api.view.annotations;

import org.integratedmodelling.klab.api.view.UIReactor;

import java.lang.annotation.*;

/**
 * Annotates UI event action handler methods in UIReactor interfaces. If the {@link UIReactor.UIEvent} handled
 * has a payload, this applies to elements of that class, such as a menu for a document in a tree; otherwise
 * it is a generic menu action associated with the reactor, as a menu or action button.
 * <p>
 * When an interface contains methods annotated with this, it must provide a correspondent menu selection for
 * the object set as the payload of the argument. The modeler will provide the associated labels and tooltips
 * (at some point also localized) so that consistent interfaces can be defined if wished.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UIView {

    /**
     * The type of event this method handles. Parameters passed to the method must be assignable to the
     * payload class(es) associated with the event type and in the same order.
     *
     * @return
     */
    UIReactor.Type value();

    /**
     * The ID is used to associate to components in declarative languages or JSON. If not provided, it
     * defaults to the name of the annotated class.
     *
     * @return
     */
    String id() default "";

    /**
     * The label to use to prompt for the action.
     *
     * @return
     */
    String label() default "";

    String tooltip() default "";

}
