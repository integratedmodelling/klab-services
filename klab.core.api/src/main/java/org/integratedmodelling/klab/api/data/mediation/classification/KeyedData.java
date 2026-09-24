package org.integratedmodelling.klab.api.data.mediation.classification;

import java.io.Serializable;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.WorldviewCommitment;

/** Durable dictionary contract: code 0 is missing; codes 1..size are append-only, unordered types. */
public final class KeyedData {
  private KeyedData() {}
  private static final Map<String,int[]> TRANSLATIONS = new LinkedHashMap<>(16,0.75f,true);
  public record Entry(String definition, String label, String authority) implements Serializable {
    public Entry(String definition,String label) { this(definition,label,""); }
    public Entry {
      if (definition == null || definition.isBlank() || label == null || authority == null)
        throw new IllegalArgumentException("Invalid keyed semantic identity");
    }
  }
  public record Dictionary(int version, WorldviewCommitment worldview, String target,
      List<Entry> entries) implements Serializable {
    public Dictionary {
      if (version != 1 || worldview == null || target == null || target.isBlank())
        throw new IllegalArgumentException("Invalid dictionary schema or worldview");
      entries = List.copyOf(entries);
      if (entries.stream().map(Entry::definition).distinct().count() != entries.size())
        throw new IllegalArgumentException("Duplicate dictionary identity");
    }
    public String fingerprint() {
      try {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var fields = new ArrayList<String>();
        fields.add("keyed-1"); fields.add(worldview.fingerprint()); fields.add(target);
        for (var entry : entries) { fields.add(entry.definition()); fields.add(entry.label()); fields.add(entry.authority()); }
        for (var field : fields) {
          var bytes = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
      } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    /** Explicit semantic translation, never integer arithmetic or label matching. */
    public int[] translationTo(Dictionary targetDictionary) {
      if (!worldview.equals(targetDictionary.worldview) || !target.equals(targetDictionary.target))
        throw new IllegalArgumentException("Incompatible dictionary worldview or type");
      String cacheKey=fingerprint()+":"+targetDictionary.fingerprint();
      synchronized(TRANSLATIONS) { var cached=TRANSLATIONS.get(cacheKey);if(cached!=null)return cached.clone(); }
      var codes = new HashMap<String, Integer>();
      for (int i = 0; i < targetDictionary.entries.size(); i++) codes.put(targetDictionary.entries.get(i).definition(), i + 1);
      int[] result = new int[entries.size() + 1];
      for (int i = 0; i < entries.size(); i++) {
        var code = codes.get(entries.get(i).definition());
        if (code == null) throw new IllegalArgumentException("Target dictionary lacks " + entries.get(i).definition());
        if(!entries.get(i).authority().equals(targetDictionary.entries.get(code-1).authority()))
          throw new IllegalArgumentException("Dictionary authority differs for " + entries.get(i).definition());
        result[i + 1] = code;
      }
      if(result.length<=65536) synchronized(TRANSLATIONS) {
        TRANSLATIONS.put(cacheKey,result.clone());
        if(TRANSLATIONS.size()>16)TRANSLATIONS.remove(TRANSLATIONS.keySet().iterator().next());
      }
      return result;
    }
  }
  public record Summary(Dictionary dictionary, CategoryHistogram histogram) implements Serializable {
    public Summary {
      if (!dictionary.fingerprint().equals(histogram.dictionary())) throw new IllegalArgumentException("Histogram dictionary mismatch");
      if(histogram.counts().keySet().stream().anyMatch(code->code>dictionary.entries().size()))throw new IllegalArgumentException("Unknown histogram code");
    }
  }
  public record CategoryHistogram(String dictionary, Map<Integer, Long> counts, long missing)
      implements Serializable {
    public CategoryHistogram {
      if (dictionary == null || !dictionary.matches("[0-9a-f]{64}") || missing < 0)
        throw new IllegalArgumentException("Invalid categorical histogram");
      counts = Collections.unmodifiableMap(new TreeMap<>(counts));
      counts.forEach((code, count) -> { if (code <= 0 || count < 0) throw new IllegalArgumentException("Invalid category count"); });
    }
  }
}
