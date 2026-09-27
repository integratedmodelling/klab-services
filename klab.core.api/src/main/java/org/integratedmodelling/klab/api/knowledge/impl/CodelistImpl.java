package org.integratedmodelling.klab.api.knowledge.impl;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/** Portable implementation used for codelists derived by a Resources service. */
public class CodelistImpl implements Codelist {

  @Serial private static final long serialVersionUID = 1L;

  /** One declaration-order-preserving code mapping. */
  public static class Entry implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private String authorityId;
    private Long code;
    private KimConcept value;
    private String description;
    private boolean preferred = true;

    public Entry() {}

    public Entry(
        String authorityId, Long code, KimConcept value, String description, boolean preferred) {
      this.authorityId = authorityId;
      this.code = code;
      this.value = value;
      this.description = description;
      this.preferred = preferred;
    }

    public String getAuthorityId() {
      return authorityId;
    }

    public void setAuthorityId(String authorityId) {
      this.authorityId = authorityId;
    }

    public Long getCode() {
      return code;
    }

    public void setCode(Long code) {
      this.code = code;
    }

    public KimConcept getValue() {
      return value;
    }

    public void setValue(KimConcept value) {
      this.value = value;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public boolean isPreferred() {
      return preferred;
    }

    public void setPreferred(boolean preferred) {
      this.preferred = preferred;
    }
  }

  private String urn;
  private String name;
  private String description;
  private String authorityId;
  private boolean authority;
  private String worldview;
  private Artifact.Type type = Artifact.Type.CONCEPT;
  private String rootConceptId;
  private String pattern;
  private String serviceId;
  private Metadata metadata = Metadata.create();
  private List<Annotation> annotations = new ArrayList<>();
  private List<Entry> entries = new ArrayList<>();

  @Override
  public String getUrn() {
    return urn;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String getAuthorityId() {
    return authorityId;
  }

  public void setAuthorityId(String authorityId) {
    this.authorityId = authorityId;
  }

  @Override
  public Collection<String> getAuthorityIds() {
    var result = new LinkedHashSet<String>();
    for (var entry : entries) result.add(entry.getAuthorityId());
    return List.copyOf(result);
  }

  @Override
  public int size() {
    return entries.size();
  }

  @Override
  public boolean isAuthority() {
    return authority;
  }

  public void setAuthority(boolean authority) {
    this.authority = authority;
  }

  @Override
  public String getWorldview() {
    return worldview;
  }

  public void setWorldview(String worldview) {
    this.worldview = worldview;
  }

  @Override
  public Artifact.Type getType() {
    return type;
  }

  public void setType(Artifact.Type type) {
    this.type = type;
  }

  @Override
  public String getRootConceptId() {
    return rootConceptId;
  }

  public void setRootConceptId(String rootConceptId) {
    this.rootConceptId = rootConceptId;
  }

  @Override
  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public Object value(Object key) {
    for (var entry : entries) if (sameCode(entry.getCode(), key)) return entry.getValue();
    return null;
  }

  @Override
  public Object value(String authorityId, Object key) {
    for (var entry : entries)
      if (Objects.equals(authorityId, entry.getAuthorityId()) && sameCode(entry.getCode(), key))
        return entry.getValue();
    return null;
  }

  @Override
  public Collection<Object> codes() {
    return entries.stream().map(Entry::getCode).map(Object.class::cast).toList();
  }

  @Override
  public Collection<Object> codes(String authorityId) {
    return entries.stream()
        .filter(entry -> Objects.equals(authorityId, entry.getAuthorityId()))
        .map(Entry::getCode)
        .map(Object.class::cast)
        .toList();
  }

  @Override
  public Collection<Object> keys(Object value) {
    return entries.stream()
        .filter(entry -> Objects.equals(value, entry.getValue()))
        .map(Entry::getCode)
        .map(Object.class::cast)
        .toList();
  }

  @Override
  public Object key(Object value) {
    for (var entry : entries)
      if (entry.isPreferred() && Objects.equals(value, entry.getValue())) return entry.getCode();
    for (var entry : entries)
      if (Objects.equals(value, entry.getValue())) return entry.getCode();
    return null;
  }

  @Override
  public String getDescription(Object code) {
    for (var entry : entries) if (sameCode(entry.getCode(), code)) return entry.getDescription();
    return null;
  }

  @Override
  public String getDescription(String authorityId, Object code) {
    for (var entry : entries)
      if (Objects.equals(authorityId, entry.getAuthorityId()) && sameCode(entry.getCode(), code))
        return entry.getDescription();
    return null;
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  public List<Entry> getEntries() {
    return entries;
  }

  public void setEntries(List<Entry> entries) {
    this.entries = entries;
  }

  private static boolean sameCode(Long expected, Object key) {
    if (!(key instanceof Number number)) return false;
    try {
      if (number instanceof java.math.BigInteger integer)
        return expected.equals(integer.longValueExact());
      if (number instanceof java.math.BigDecimal decimal)
        return expected.equals(decimal.longValueExact());
    } catch (ArithmeticException e) {
      return false;
    }
    if (number instanceof Byte || number instanceof Short || number instanceof Integer
        || number instanceof Long) return expected.equals(number.longValue());
    double value = number.doubleValue();
    return Double.isFinite(value)
        && Math.rint(value) == value
        && value < 0x1.0p63
        && value >= -0x1.0p63
        && expected.equals((long) value);
  }
}
