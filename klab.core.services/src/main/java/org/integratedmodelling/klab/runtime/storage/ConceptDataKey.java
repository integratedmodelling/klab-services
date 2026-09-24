package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.classification.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.services.Reasoner;

/** One coordinated allocator per observation. Canonical values and negative validation are cached. */
final class ConceptDataKey implements DataKey {
  private final Reasoner reasoner;
  private final Concept target;
  private final WorldviewCommitment worldview;
  private final Runnable bindContext;
  private final Runnable validateWorldview;
  private final ArrayList<KeyedData.Entry> entries = new ArrayList<>();
  private final ArrayList<Concept> concepts = new ArrayList<>();
  private final Map<String, Integer> codes = new HashMap<>();
  private final IdentityHashMap<Object, Integer> hot = new IdentityHashMap<>();
  private final Set<String> rejected = new HashSet<>();

  ConceptDataKey(Reasoner reasoner, Concept target, WorldviewCommitment worldview, Runnable bindContext) {
    this(reasoner,target,worldview,bindContext,()->{});
  }
  ConceptDataKey(Reasoner reasoner, Concept target, WorldviewCommitment worldview, Runnable bindContext, Runnable validateWorldview) {
    this.validateWorldview=validateWorldview;
    if (target == null || worldview == null) throw new IllegalArgumentException("KEYED requires type-of semantics and a fingerprinted worldview");
    this.reasoner = reasoner; this.target = target; this.worldview = worldview; this.bindContext = bindContext;
  }
  private boolean bound;
  private void bind() { if (!bound) { bindContext.run(); bound = true; } }
  synchronized int code(Object value) {
    if (value == null) { bind(); return 0; }
    var cached = hot.get(value); if (cached != null) { bind(); return cached; }
    if (!(value instanceof Concept concept)) throw new IllegalArgumentException("KEYED values must be concepts");
    String urn = concept.getUrn();
    var known = codes.get(urn);
    if (known != null) { lookup(known); bind(); if (hot.size() > 1024) hot.clear(); hot.put(value, known); return known; }
    Concept canonical = validate(urn);
    bind();
    if (entries.size() == Integer.MAX_VALUE - 1) throw new IllegalStateException("Concept dictionary code overflow");
    int code = entries.size() + 1;
    entries.add(new KeyedData.Entry(canonical.getUrn(), Objects.toString(canonical.displayLabel(), canonical.getUrn()), authority(canonical)));
    concepts.add(canonical); codes.put(canonical.getUrn(), code); hot.put(value, code); hot.put(canonical, code);
    return code;
  }
  private static String authority(Concept concept) { return Objects.toString(concept.getMetadata().get("klab:authorityId"), ""); }
  private Concept validate(String urn) {
    if (urn == null || rejected.contains(urn)) throw invalid(urn);
    validateWorldview.run();
    var canonical = reasoner.resolveConcept(urn);
    if (canonical == null || !urn.equals(canonical.getUrn()) || canonical.isAbstract() || canonical.isGeneric() || canonical.is(SemanticType.NOTHING)
        || canonical.getUrn().equals(target.getUrn()) || !reasoner.is(canonical, target)) {
      rejected.add(urn); throw invalid(urn);
    }
    return canonical;
  }
  private IllegalArgumentException invalid(String urn) {
    return new IllegalArgumentException("Keyed value " + urn + " must be a concrete subclass of " + target.getUrn());
  }
  synchronized void restore(KeyedData.Dictionary dictionary) {
    if (!worldview.equals(dictionary.worldview()) || !target.getUrn().equals(dictionary.target()))
      throw new IllegalStateException("Keyed dictionary worldview/type differs from the context");
    for (int i = 0; i < dictionary.entries().size(); i++) {
      var entry = dictionary.entries().get(i);
      if (i < entries.size()) {
        if (!entries.get(i).equals(entry)) throw new IllegalStateException("Conflicting dictionary code " + (i + 1));
      } else {
        entries.add(entry); concepts.add(null); codes.put(entry.definition(), i + 1);
      }
    }
  }
  synchronized KeyedData.Dictionary snapshot() { return new KeyedData.Dictionary(1, worldview, target.getUrn(), entries); }
  public synchronized int size() { return entries.size() + 1; }
  public synchronized int reverseLookup(Object value) { return value == null ? 0 : value instanceof Concept c ? codes.getOrDefault(c.getUrn(), -1) : -1; }
  public synchronized Concept lookup(int code) {
    if (code == 0) return null;
    if (code < 0 || code > entries.size()) throw new IllegalStateException("Unknown keyed code " + code);
    Concept value = concepts.get(code - 1);
    if (value == null) { value = validate(entries.get(code - 1).definition());
      if(!authority(value).equals(entries.get(code-1).authority()))throw new IllegalStateException("Dictionary authority differs for " + value.getUrn());
      concepts.set(code - 1, value); hot.put(value, code); }
    return value;
  }
  public synchronized List<String> getLabels() { var labels=new ArrayList<String>();labels.add("Missing");entries.forEach(e->labels.add(e.label()));return List.copyOf(labels); }
  public synchronized List<Concept> getConcepts() { var ret = new ArrayList<Concept>(); for (int i=0;i<size();i++) ret.add(lookup(i)); return Collections.unmodifiableList(ret); }
  public synchronized List<Pair<Integer,String>> getAllValues() { var ret = new ArrayList<Pair<Integer,String>>(); for(int i=0;i<size();i++) ret.add(Pair.of(i,i==0?"Missing":entries.get(i-1).label())); return List.copyOf(ret); }
  synchronized DataKey readOnly() {
    var snapshot = snapshot();
    var byDefinition = new HashMap<String,Integer>();
    for(int i=0;i<snapshot.entries().size();i++)byDefinition.put(snapshot.entries().get(i).definition(),i+1);
    return new DataKey() {
      public int size() { return snapshot.entries().size() + 1; }
      public int reverseLookup(Object value) { return value == null ? 0 : value instanceof Concept c ? byDefinition.getOrDefault(c.getUrn(),-1) : -1; }
      public Object lookup(int code) { if(code<0 || code>=size())throw new IllegalStateException("Unknown keyed code " + code); return ConceptDataKey.this.lookup(code); }
      public List<String> getLabels() { var labels=new ArrayList<String>();labels.add("Missing");snapshot.entries().forEach(e->labels.add(e.label()));return List.copyOf(labels); }
      public List<Concept> getConcepts() { var ret=new ArrayList<Concept>();for(int i=0;i<size();i++)ret.add((Concept)lookup(i));return Collections.unmodifiableList(ret); }
      public List<Pair<Integer,String>> getAllValues() { var ret=new ArrayList<Pair<Integer,String>>();for(int i=0;i<size();i++)ret.add(Pair.of(i,i==0?"Missing":snapshot.entries().get(i-1).label()));return List.copyOf(ret); }
      public boolean isOrdered() { return false; }
      public Object include(Object value) { throw new UnsupportedOperationException("Dictionary snapshots are immutable; use typed scanner insertion"); }
      public Authority getAuthority() { return null; }
      public String encode(KlabLanguage language) { return org.integratedmodelling.klab.utilities.Utils.Json.asString(snapshot); }
    };
  }
  public boolean isOrdered() { return false; }
  public Object include(Object value) { return lookup(code(value)); }
  public Authority getAuthority() { return null; }
  public String encode(KlabLanguage language) { return org.integratedmodelling.klab.utilities.Utils.Json.asString(snapshot()); }
}
