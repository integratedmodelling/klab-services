package org.integratedmodelling.klab.services.resources.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Backend boundary. Replacing one document is atomic; insertIfAbsent arbitrates concurrent writers. */
public interface KboxDocumentStore extends AutoCloseable {
  Map<String, Object> get(String key);
  Map<String, Object> insertIfAbsent(String key, Map<String, Object> value);
  void put(String key, Map<String, Object> value);
  List<Map<String, Object>> find(String field, Collection<?> values);
  void delete(String key);
  long nextId();
  @Override void close();
}
