package org.integratedmodelling.klab.api.view.annotations;

import org.integratedmodelling.klab.api.view.UIReactor;

import java.lang.annotation.*;

/**
 * Annotates UI event action handler methods in UIReactor interfaces. If the {@link UIReactor.UIEvent} handled
 * has a payload, this applies to elements of that class, such as a menu for a document in a tree; otherwise
 * it is a generic menu action associated with the reactor, as a menu or action button.
 * <p>
 * When an interface contains methods annotated with this, it must provide a correspondent menu selection for
 * the object set as the payload of the argument. The controller will provide the associated labels and
 * tooltips (at some point also localized) so that consistent interfaces can be defined if wished.
 * <p>
 * In Java-based views, actions do not necessarily need to be explicitly annotated as the dispatch() method
 * can be called explicitly on the controller. Still, it's good practice to only use explicitly declared
 * actions, which can be validated, documented and used whenever the view is specified in anything other than
 * Java, e.g. JSON or k.Actors.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UIActionHandler {

    /**
     * The type of event this method handles. Parameters passed to the method must be assignable to the
     * payload class(es) associated with the event type and in the same order.
     *
     * @return
     */
    UIReactor.UIAction value();

    /**
     * The ID is used to associate the action to components in declarative languages or JSON. If not provided,
     * it defaults to the name of the annotated method.
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
