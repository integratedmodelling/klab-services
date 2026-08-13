package org.integratedmodelling.klab.api.lang;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * All statements in k.LAB-supported languages are serializables. The resource service maintains the
 * catalog of available projects and resources, managing the transfer of pre-parsed projects with
 * their behaviors and namespaces to the semantic and resolver services.
 *
 * @author Ferd
 */
public interface Statement extends Serializable {

  /**
   * If this comes from a document, return the offset in the source code. Otherwise return -1. The
   * way to access the containing document is not specified in the API and is up to the
   * implementation.
   *
   * @return
   */
  int getOffsetInDocument();

  /**
   * If {@link #getOffsetInDocument()} returns >= 0, this must return a valid length of the textual
   * specification starting at the offset.
   *
   * @return
   */
  int getLength();

  /**
   * @return the annotations
   */
  List<Annotation> getAnnotations();

  /**
   * @return the reason for deprecation
   */
  String getDeprecation();

  /**
   * @return true if deprecated
   */
  boolean isDeprecated();

  /**
   * Any errors, warnings or info are reported as notifications. Check error notifications to see if
   * the statement is legal.
   *
   * @return
   */
  Collection<Notification> getNotifications();
}
