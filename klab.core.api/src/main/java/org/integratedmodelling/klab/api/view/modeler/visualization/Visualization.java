package org.integratedmodelling.klab.api.view.modeler.visualization;

import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

import java.lang.annotation.*;

/**
 * Annotates methods of the {@link org.integratedmodelling.klab.api.view.UIView}-annotated class
 * that can provide visualization for assets. Visualization does not necessarily imply a
 * graphical/visual UI and differs from export in that the results of visualization do not
 * necessarily contain the entire information relative to the asset, which cannot be reconstructed
 * from its visualized form. The methods are parsed and analyzed so that the {@link
 * org.integratedmodelling.klab.api.view.modeler.Modeler} can choose a suitable method for an asset
 * and manage the visualization.
 *
 * <p>Methods are discovered at runtime when the view is set into the UIController.
 *
 * <p>Visualization methods may rely on an export format (specified through the media type) provided
 * by services.
 *
 * <p>The applicable type of asset is determined by analyzing the parameters of the annotated
 * method.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Visualization {
  /**
   * Media type provided. Normally HTML, image or video. Can also be CSV, JSON, PDF in specific
   * circumstances.
   *
   * @return
   */
  String provides();

  /**
   * Media type required, which will be resolved through an export mechanism and made available
   * through a URL parameter in the annotated method. If this is empty, the asset can be used
   * directly.
   *
   * @return
   */
  String requires() default "";

  /**
   * Specification of a geometry that the asset must be compatible with. By default all geometries
   * are accepted. Only used for assets that specify a geometry.
   *
   * @return
   */
  String geometry() default "";

  /**
   * If applicable, choose the types of artifact this applies to. A parameter of the annotated
   * method must be compatible with all of them.
   *
   * @return
   */
  Artifact.Type[] artifactTypes() default {};

  /**
   * If applicable, choose the semantic types this applies to. The asset must be semantic.
   *
   * @return
   */
  SemanticType[] semanticTypes() default {};

  /**
   * Specification of an observable that the asset must be compatible with. By default, all are
   * accepted. Only used for assets that have semantics.
   *
   * @return
   */
  String semantics() default "";
}
