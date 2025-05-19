package org.integratedmodelling.klab.api.view.annotations;

import org.integratedmodelling.klab.api.view.UIReactor;

import java.lang.annotation.*;

/**
 * Annotates UI event action handler methods in UIReactor interfaces. If the {@link UIReactor.UIEvent} handled
 * has a payload, this applies to elements of that class, such as a menu for a document in a tree; otherwise
 * it is a generic menu action associated with the reactor, as a menu or action button.
 * <p>
 * When an interface contains methods annotated with this, it must provide a correspondent menu selection or
 * UI action target involving the object set as the payload of the argument. The controller will provide the
 * associated labels and tooltips (at some point also localized) so that consistent interfaces can be defined
 * if wished.
 * <p>
 * In Java-based views, actions do not necessarily need to be explicitly annotated as the dispatch() method
 * can be called explicitly on the controller. Still, it's good practice to only use explicitly declared
 * actions, which can be validated, documented and possibly transpiled whenever the view is specified in
 * anything other than Java, e.g. JSON or k.Actors. This way, all view methods that are intended to be linked
 * to specific user actions can be easily distinguished and validated.
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

    /**
     * The target class of the action, which must be an object in current focus within the view this action is
     * part of. If the target is Void.class, the action is enabled globally in the view that contains it..
     *
     * @return
     */
    Class<?> target() default Void.class;

    /**
     * The events that this action may send to the UI once it's executed.
     *
     * @return the event list
     */
    UIReactor.UIEvent[] sends() default {};

}
