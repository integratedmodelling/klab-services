package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * This annotation is used on a class or a method to declare a k.LAB service accessible through a
 * k.IM service call. The class or methods must be part of a top-level class tagged with the @{@link
 * Library} annotation, which supplies the load point in a component and the namespace for the
 * service call.
 *
 * <p>If used on a class, the interface implemented by the class defines the type of use. Normally
 * the annotation is used with a subinterface of {@link Contextualizer} to produce stateful
 * contextualizers.
 *
 * <p>If used on a method, the arguments and the geometry are used to classify the context of use
 * for the call.
 *
 * <p>The functions can be used in k.IM, k.Actors and the observation language. This cannot be used
 * to create implementations for k.Actors <em>actions</em>, which must be tagged with {@link Verb}
 * instead.
 *
 * <p>TODO missing fields (to substitute some booleans): ComputationContextType (sic) i.e. processes
 * Observation, Value, Resource, Concept ProcessingType (Producer, Filter, Consumer) i.e. what it
 * takes and produces and how Use in addition to Geometry and Artifact Type
 *
 * @author Ferd
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface KlabFunction {

  /**
   * Arguments are used in the declaration to describe function parameters to be passed to
   * prototypes.
   *
   * @author Ferd
   */
  @interface Argument {

    String name();

    Artifact.Type[] type();

    /**
     * Where appropriate. Either a unit or a currency.
     *
     * @return
     */
    String unit() default "";

    /**
     * Mandatory description for the documentation.
     *
     * @return
     */
    String description();

    boolean optional() default false;

    boolean isFinal() default false;

    /**
     * If true, must be a POD literal and nothing else is accepted.
     *
     * @return
     */
    boolean constant() default false;

    /**
     * If true, must be the local name of another observation stated as a dependency in the
     * containing model, and nothing else is accepted.
     *
     * @return
     */
    boolean artifact() default false;
  }

  /**
   * Tag an {@link org.integratedmodelling.klab.api.knowledge.observation.Observation}, {@link
   * org.integratedmodelling.klab.api.data.Storage} or a subclass of {@link
   * org.integratedmodelling.klab.api.data.Storage.Buffer} to define a dependency by name and type.
   * The name will become a requirement from the dependencies in the model and the type will be
   * validated. This can also be declared in the main prototype.
   */
  @Target({ElementType.PARAMETER})
  @interface Import {

    String name();

    Artifact.Type[] type();

    String observable() default "";

    /**
     * Where appropriate. Either a unit or a currency.
     *
     * @return
     */
    String unit() default "";

    /**
     * Mandatory description for the documentation.
     *
     * @return
     */
    String description();

    boolean optional() default false;
  }

  /**
   * Tag an {@link org.integratedmodelling.klab.api.knowledge.observation.Observation}, {@link
   * org.integratedmodelling.klab.api.data.Storage} or a subclass of {@link
   * org.integratedmodelling.klab.api.data.Storage.Buffer} to define an additional output by name
   * and type. The name will become a requirement from the dependencies in the model and the type
   * will be validated. This can also be declared in the main prototype.
   */
  @Target({ElementType.PARAMETER})
  @interface Export {

    String name();

    Artifact.Type[] type();

    String observable();

    /**
     * Where appropriate. Either a unit or a currency.
     *
     * @return
     */
    String unit() default "";

    /**
     * Mandatory description for the documentation.
     *
     * @return
     */
    String description();

    boolean optional() default false;
  }

  /**
   * Single, lowercase name. Will be compoundend with the enclosing library's namespace to build a
   * path that must be unique.
   *
   * @return
   */
  String name();

  /**
   * Descriptions are mandatory. Documentation is created from the annotation. Simple Markdown is
   * supported here.
   *
   * @return
   */
  String description();

  /**
   * The geometry can be used to specify extent constraints rather than specific, resolved
   * geometries. This said, it can also specify a specific coverage. Only the string-encoded form
   * can be used here. The default geometry is scalar and the return types and arguments will be
   * validated against it.
   *
   * @return
   */
  String geometry() default "*";

  /**
   * If this is true, one instance of the associated contextualizer class or method is created and
   * reused. Otherwise a new one is obtained at each reference in the dataflow.
   *
   * @return
   */
  boolean reentrant() default false;

  /**
   * Filters other observations. This should evolve into a method type that explicitly defines what
   * this does along with the geometry.
   *
   * @deprecated use a mandatory type
   * @return
   */
  boolean filter() default false;

  /**
   * This is a hint to the runtime that applies to value contextualizers, which are functional
   * objects that may or may not be executable in parallel. It implies {@link #reentrant()} == true.
   *
   * @return
   */
  boolean parallel() default false;

  String dataflowLabel() default "";

  Import[] imports() default {};

  Export[] exports() default {};

  Argument[] parameters() default {};

  Artifact.Type[] type();

  String unit() default "";

  /**
   * Number of allowed splits for buffers in the contextualization strategy. Only relevant for
   * quality contextualizers. Default is up to the implementation. Pass 1 if parallelization is not
   * wanted. Overrides any settings made with the @split annotation on models and observables.
   *
   * <p>TODO annotating a function that takes a single Buffer should automatically force this to 1.
   *
   * @return
   */
  int split() default -1;

  /**
   * Type of filling curve to override any other specification given in code.
   *
   * @return
   */
  Data.SpaceFillingCurve fillingCurve() default Data.SpaceFillingCurve.UNSPECIFIED;
}
