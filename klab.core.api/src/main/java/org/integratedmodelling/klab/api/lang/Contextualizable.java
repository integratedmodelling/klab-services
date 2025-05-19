package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * FIXME all this is obsolete. Should be a light wrapper without all those methods.
 *
 * <p>A contextualizable is the declaration of a resource that can be compiled into a processing
 * step for a dataflow. In k.IM this can represent:
 *
 * <p>
 *
 * <ul>
 *   <li>a literal value;
 *   <li>the URN for a data source or computation;
 *   <li>a service call explicitly given in k.IM;
 *   <li>an executable expression in a supported language;
 *   <li>a classification or lookup table;
 *   <li>a conversion between a source and a target value semantics (e.g. unit or currency)
 * </ul>
 *
 * <p>Contextualizables have an artifact type and a declared geometry which determines which phases
 * of a dataflow they apply to.
 *
 * <p>It is the runtime's task to turn any computable resource into a uniform k.DL service call. The
 * call produces a IContextualizer that is inserted in a dataflow.
 *
 * <p>FIXME this should merely be a tag interface that tags standard KimAssets. The
 * contextualization mode/trigger should be kept in the model independently.
 *
 * @author Ferd
 */
public interface Contextualizable extends KlabStatement {

  public static enum Type {
    CLASSIFICATION,
    SERVICE,
    LOOKUP_TABLE,
    RESOURCE,
    EXPRESSION,
    CONVERSION,
    LITERAL,
    /*
     * conditions are currently underspecified
     */ CONDITION
  }

  enum Action {
    SET,
    INTEGRATE,
    DO,
    MOVE,
    DESTROY
  }

  /**
   * Trigger for actions. Also gets propagated to contextualizables.
   *
   * @author Ferd
   */
  public enum Trigger {

    /**
     * Definition, i.e. initialization. Only legal with perdurants. Bound to 'on definition' [set to
     * | do ] For instantiators, 'self' is the context of the new instances.
     */
    DEFINITION,

    /**
     * State initialization is called after all the context has been initialized (with individual on
     * definition actions) in a state model. Self is the state itself.
     */
    STATE_INITIALIZATION,

    /**
     * Instantiation: before resolution of EACH new instance from an instantiator. Not accepted
     * within contextualizers. 'self' is the new instance, 'context' their context on which the
     * instantiator was called.
     */
    INSTANTIATION,

    /**
     * The default trigger for resources that provide the main content for models (including
     * instantiating resources) unless the model is for an occurrent, in which case the resource
     * gets the TRANSITION trigger.
     */
    RESOLUTION,

    /**
     * Termination: just after 'move away' or deactivate(). Cannot change the outcome of
     * deactivation but object can still "do" things within the action.
     */
    TERMINATION,

    /** Triggered by events (types returned by getTriggeredEvents()) */
    EVENT,

    /** Triggered by temporal transitions. */
    TRANSITION
  }

  /**
   * The data structure describing interactive parameters. It's a javabean with only strings for
   * values, so that it can be easily serialized for communication.
   *
   * <p>FIXME we should just keep an annotation for this and use it if it's there. No reason for
   * this to be here, either.
   *
   * @author ferdinando.villa
   */
  public static class InteractiveParameter {

    private String id;
    private String functionId;
    private String description;
    private String label;
    private Artifact.Type type;
    private String initialValue;
    private Set<String> values;
    // validation
    private List<Double> range;
    private int numericPrecision;
    private String regexp;
    private String sectionTitle;
    private String sectionDescription;

    // range, regexp & numeric precision

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Artifact.Type getType() {
      return type;
    }

    public void setType(Artifact.Type type) {
      this.type = type;
    }

    public String getInitialValue() {
      return initialValue;
    }

    public void setInitialValue(String initialValue) {
      this.initialValue = initialValue;
    }

    public Set<String> getValues() {
      return values;
    }

    public void setValues(Set<String> values) {
      this.values = values;
    }

    public List<Double> getRange() {
      return range;
    }

    public void setRange(List<Double> range) {
      this.range = range;
    }

    public int getNumericPrecision() {
      return numericPrecision;
    }

    public void setNumericPrecision(int numericPrecision) {
      this.numericPrecision = numericPrecision;
    }

    public String getRegexp() {
      return regexp;
    }

    public void setRegexp(String regexp) {
      this.regexp = regexp;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFunctionId() {
      return functionId;
    }

    public void setFunctionId(String functionId) {
      this.functionId = functionId;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return "InteractiveParameter [id="
          + id
          + ", functionId="
          + functionId
          + ", description="
          + description
          + ", label="
          + label
          + ", type="
          + type
          + ", initialValue="
          + initialValue
          + ", values"
          + "="
          + values
          + "]";
    }

    public String getSectionTitle() {
      return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
      this.sectionTitle = sectionTitle;
    }

    public String getSectionDescription() {
      return sectionDescription;
    }

    public void setSectionDescription(String sectionDescription) {
      this.sectionDescription = sectionDescription;
    }
  }

//  /**
//   * Return the type of the contained resource.
//   *
//   * @return
//   */
//  Type getType();

  /**
   * Target ID: if null, the main observable of the model, otherwise another observable which must
   * be defined. This is a syntactic property and can be accessed outside of a contextualization
   * scope.
   *
   * @return the target ID.
   */
  String getTargetId();

  /**
   * The type of action (applies to expressions)
   * @return
   */
  Action getAction();

  /**
   * The target observable for this computation, correspondent to the target ID. Accessible only
   * during contextualization. Null if the target is the main observable in the correspondent
   * actuator. Otherwise the computation affects other artifacts, as in the case of internal
   * dependencies due to indirect observables being used in subsequent computation to produce the
   * main one.
   *
   * @return the target name
   */
  KimObservable getTarget();

//  /**
//   * The target artifact ID when this computation is a mediation. In this case the computation means
//   * "send this artifact through this mediator".
//   *
//   * <p>This may be merged with getTarget() at some point as the use cases for it are similar.
//   *
//   * @return the mediation target ID.
//   */
//  String getMediationTargetId();

  /**
   * Each computation may use a different language. Null means the default supported expression
   * language.
   *
   * @return the language or null
   */
  String getLanguage();

  /**
   * A literal constant produced in lieu of this computation. Only one among getLiteral(),
   * getServiceCall(), getUrn(), getClassification(), getAccordingTo(), getLookupTable() and
   * getExpression() will return a non-null value.
   *
   * @return any literal
   */
  Object getLiteral();

  /**
   * A literal constant produced in lieu of this computation Only one among getLiteral(),
   * getServiceCall(), getUrn(), getClassification(), getAccordingTo(), getLookupTable() and
   * getExpression() will return a non-null value.
   *
   * @return the service call
   */
  ServiceCall getServiceCall();

  /**
   * A literal constant produced in lieu of this computation. Only one among getLiteral(),
   * getServiceCall(), getUrn(), getClassification(), getAccordingTo(), getLookupTable() and
   * getExpression() will return a non-null value.
   *
   * @return the expression
   */
  ExpressionCode getExpression();

  /**
   * A classification of the input. Only one among getLiteral(), getServiceCall(), getUrn(),
   * getClassification(), getAccordingTo(), getLookupTable() and getExpression() will return a
   * non-null value.
   *
   * @return the classification
   */
  KimClassification getClassification();

  /**
   * A lookup table translating the inputs. Only one among getLiteral(), getServiceCall(), getUrn(),
   * getClassification(), getAccordingTo(), getLookupTable() and getExpression() will return a
   * non-null value.
   *
   * @return the lookup table
   */
  KimLookupTable getLookupTable();

  /**
   * An implicit classification built by matching values of an annotation property to subclasses of
   * the observable. Only one among getLiteral(), getServiceCall(), getUrn(), getClassification(),
   * getAccordingTo(), getLookupTable() and getExpression() will return a non-null value.
   *
   * @return the classifier property
   */
  String getAccordingTo();

  /**
   * One or more URNs specifying a remote computation. Only one among getLiteral(),
   * getServiceCall(), getUrn() and getExpression() will return a non-null value.
   *
   * @return the urn
   */
  List<String> getResourceUrns();

  //  /**
  //   * Contextualization requires a trip back to the resolver to resolve a contextualization
  // strategy
  //   * in the current contextualization context.
  //   *
  //   * @return
  //   */
  //  ObservationStrategy getObservationStrategy();

  /**
   * Resources such as expressions or URN-specified remote computations may have requirements that
   * must be satisfied within the model where the computation appears. These will be made available
   * in appropriate form (scalar or not) by the runtime environment.
   *
   * @return the requirements as a collection of name and type pairs.
   */
  Collection<Pair<String, Artifact.Type>> getInputs();

  Trigger getTrigger();

//  /**
//   * Any parameters set for the computation, e.g. in the case of a function call or a URN with
//   * optional values.
//   *
//   * @return parameter map, never null, possibly empty.
//   */
//  Parameters<String> getParameters();

  /**
   * In interactive mode, resources may expose parameters for users to check and modify before
   * execution. Implementation-dependent services will extract descriptors and set values.
   *
   * @return the list of all parameters that may be changed by users.
   */
  Collection<String> getInteractiveParameters();

  /**
   * This computation may be linked to a condition, which is another computation producing a
   * boolean. This is always empty if this resource is itself a condition.
   *
   * @return the condition or an empty container.
   */
  Contextualizable getCondition();

//  /**
//   * The computation may consist in a mediation of a quantity represented by the first element in
//   * the returned tuple, which must be converted into a value represented by the second.
//   *
//   * <p>If the resource is created, the mediators must be guaranteed compatible.
//   *
//   * <p>
//   *
//   * @return a tuple containing the original and target value semantics.
//   */
//  Pair<ValueMediator, ValueMediator> getConversion();

  /**
   * Only meaningful if this computable is a condition computing a (scalar or distributed) boolean,
   * this specifies whether this condition was given with negative ('unless' instead of 'if')
   * semantics.
   *
   * @return true if negated ('unless')
   */
  boolean isNegated();

  /**
   * True if this computation is a mediation, expected to output a transformation of the artifact
   * passed to it, to be used in its place to match a specific observation semantics.
   *
   * @return true if mediator
   */
  boolean isMediation();

  /**
   * This will return the geometry incarnated by the computable. It should normally return a scalar
   * geometry except for resources and services.
   *
   * @return the geometry
   */
  Geometry getGeometry();

  /**
   * If true, this defines an accessory variable rather than a dependency. The targetId is the name
   * of the variable.
   *
   * @return
   */
  boolean isVariable();

  /**
   * If true, no need for this contextualization to proceed as nothing would happen. Can be set
   * after contextualize() if there's no temporal change for example.
   *
   * <p>Overridden only to document the difference in semantics.
   */
  boolean isEmpty();
}
