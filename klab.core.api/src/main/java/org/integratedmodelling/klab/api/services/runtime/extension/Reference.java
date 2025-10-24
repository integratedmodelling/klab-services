package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * These are used to replicate an annotation for an alternative method or class. Alternatives are
 * evaluated dynamically at runtime, matching the arguments to the context of execution.
 *
 * <p>All references should appear after the first declaration for the annotated element with the
 * same name and can only refer to elements inside the same library.
 */
public @interface Reference {

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface Function {
    String name();
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface Exporter {
    String name();
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface Importer {
    String name();
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface Verb {
    String name();
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface Annotation {
    String name();
  }
}
