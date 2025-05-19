package org.integratedmodelling.common.knowledge;

import java.io.Serial;
import java.util.*;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.springframework.util.StringUtils;

public class ConceptImpl implements Concept {

  /**
   * Numeric IDs for concepts that are not in ontologies (including owl:Nothing in its non-semantic
   * version). These are fixed across reasoners. Positive concept IDs may be attributed on a
   * reasoner-specific basis.
   */
  public static final long NOTHING_ID = 0;

  public static final long NONSEMANTIC_SUBJECT_ID = -1;
  public static final long NONSEMANTIC_EVENT_ID = -2;
  public static final long NONSEMANTIC_RELATIONSHIP_ID = -3;
  public static final long NONSEMANTIC_NUMBER_ID = -4;
  public static final long NONSEMANTIC_CATEGORY_ID = -5;
  public static final long NONSEMANTIC_BOOLEAN_ID = -6;

  @Serial private static final long serialVersionUID = -6871573029225503370L;

  private long id;
  private String urn;
  private Metadata metadata = Metadata.create();
  private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
  private String namespace;
  private String name;
  private String referenceName;
  private boolean isAbstract;
  private boolean collective;
  private List<Annotation> annotations = new ArrayList<>();
  private LogicalConnector qualifier;
  private List<Notification> notifications = new ArrayList<>();

  public ConceptImpl() {}

  public ConceptImpl(ConceptImpl other) {
    this.id = other.id;
    this.urn = other.urn;
    this.metadata.putAll(other.metadata);
    this.type.addAll(other.type);
    this.namespace = other.namespace;
    this.name = other.name;
    this.referenceName = other.referenceName;
    this.isAbstract = other.isAbstract;
    this.collective = other.collective;
    this.annotations.addAll(other.annotations);
    this.qualifier = other.qualifier;
  }

  public void setQualifier(LogicalConnector qualifier) {
    this.qualifier = qualifier;
  }

  @Override
  public String getUrn() {
    return urn;
  }

  @Override
  public Set<SemanticType> getType() {
    return type;
  }

  @Override
  public boolean is(SemanticType type) {
    return this.type.contains(type);
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  @Override
  public LogicalConnector getQualifier() {
    return this.qualifier;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setType(Set<SemanticType> type) {
    this.type = type;
  }

  public void setAbstract(boolean isAbstract) {
    this.isAbstract = isAbstract;
  }

  @Override
  public boolean isAbstract() {
    return isAbstract;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setReferenceName(String referenceName) {
    this.referenceName = referenceName;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getReferenceName() {
    return referenceName;
  }

  @Override
  public String codeName() {
    return Utils.CamelCase.toLowerCase(displayName(), '_');
  }

  @Override
  public String displayName() {

    // String ret = getMetadata().get(IMetadata.DISPLAY_LABEL_PROPERTY, String.class);
    //
    // if (ret == null) {
    String ret = getMetadata().get(Metadata.DC_LABEL, String.class);
    // }
    if (ret == null) {
      ret = getName();
    }
    if (ret.startsWith("i")) {
      ret = ret.substring(1);
    }

    return ret;
  }

  @Override
  public String displayLabel() {
    String ret = displayName();
    if (!ret.contains(" ")) {
      ret = StringUtils.capitalize(Utils.CamelCase.toLowerCase(ret, ' '));
    }
    return ret;
  }

  @Override
  public Concept asConcept() {
    return this;
  }

  @Override
  public boolean isGeneric() {
    return false;
  }

  @Override
  public String toString() {
    return this.urn;
  }

  @Override
  public int hashCode() {
    return Objects.hash(urn);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ConceptImpl other = (ConceptImpl) obj;
    return Objects.equals(urn, other.urn);
  }

  @Override
  public List<Annotation> getAnnotations() {
    return this.annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  @Override
  public boolean isCollective() {
    return collective;
  }

  public void setCollective(boolean collective) {
    this.collective = collective;
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  @Override
  public ConceptImpl collective() {

    if (this.isCollective()) {
      return this;
    }

    var ret = new ConceptImpl(this);
    ret.setCollective(true);
    ret.setReferenceName("each_" + this.getReferenceName());
    ret.setUrn("each " + this.getUrn());
    return ret;
  }

  public ConceptImpl singular() {

    if (!this.isCollective()) {
      return this;
    }

    var ret = new ConceptImpl(this);
    ret.setCollective(false);
    ret.setReferenceName(this.getReferenceName().replaceFirst("each_", ""));
    ret.setUrn(this.getUrn().replaceFirst("each ", ""));
    return ret;
  }

  public void error(String s) {
    notifications.add(Notification.error(s));
    this.type = EnumSet.of(SemanticType.NOTHING);
  }

  public void info(String s) {
    notifications.add(Notification.error(s));
  }

  public void warn(String s) {
    notifications.add(Notification.error(s));
  }
}
