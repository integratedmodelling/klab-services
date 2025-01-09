package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An order-preserving map with improved get() methods to enable simpler and more flexible use
 * idioms. Also enables accounting of unnamed inputs (mapped to _p<n> keys, from k.IM/k.Actors code)
 * and k.Actors metadata keys.
 *
 * @author ferdinando.villa
 */
public class ParametersImpl<T> implements Parameters<T> {

  private static final long serialVersionUID = 99901513041971570L;

  private Map<T, Object> delegate;
  private List<T> unnamedKeys = new ArrayList<>();
  private Parameters<String> templateVariables = null;

  public ParametersImpl(Map<T, Object> delegate) {
    this.delegate = delegate == null ? new LinkedHashMap<>() : delegate;
    if (delegate instanceof ParametersImpl) {
      this.unnamedKeys.addAll(((ParametersImpl<T>) delegate).unnamedKeys);
    }
  }

  public static final String IGNORED_PARAMETER = "__IGNORED__";

  /**
   * Only used when the object must be serialized through reflection.
   *
   * @return a map with all data
   */
  public Map<T, Object> getData() {
    return delegate;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K> K get(T name, K defaultValue) {
    Object ret = get(name);
    if (ret == null) {
      return defaultValue;
    }
    return defaultValue == null ? (K) ret : Utils.Data.asType(ret, defaultValue.getClass());
  }

  @Override
  public <K> K get(T name, Class<K> cls) {
    Object ret = get(name);
    if (ret == null) {
      return null;
    }
    return Utils.Data.asType(ret, cls);
  }

  @Override
  public <K> List<K> getList(T name, Class<K> cls) {
    Object ret = get(name);
    if (ret == null) {
      return List.of();
    }
    if (ret instanceof Collection list) {
      return list.stream().map(value -> Utils.Data.asType(value, cls)).toList();
    } else if (ret.getClass().isArray()) {
      return (List<K>)
          Utils.Data.convertArrayToList(ret).stream()
              .map(value -> Utils.Data.asType(value, cls))
              .toList();
    }
    return List.of(Utils.Data.asType(ret, cls));
  }

  @Override
  public <K> K getNotNull(T name, Class<K> cls) {
    Object ret = get(name);
    if (ret == null) {
      return Utils.Data.notNull(cls);
    }
    return Utils.Data.asType(ret, cls);
  }

  public ParametersImpl() {
    this.delegate = new LinkedHashMap<>();
  }

  public ParametersImpl(Map<T, Object> delegate, List<T> unnamedKeys) {
    this.delegate = delegate;
    this.unnamedKeys = unnamedKeys;
  }

  public int size() {
    return delegate.size();
  }

  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  public boolean containsKey(Object key) {
    return delegate.containsKey(key);
  }

  public boolean containsValue(Object value) {
    return delegate.containsValue(value);
  }

  public Object get(Object key) {
    Object ret = delegate.get(key);
    //        if (this.templateVariables != null && ret instanceof TemplateValue) {
    //            ret = ((TemplateValue) ret).getValue(this.templateVariables);
    //        }
    if (ret instanceof Map && !(ret instanceof Parameters)) {
      ret = new ParametersImpl((Map<?, ?>) ret);
      ((ParametersImpl<?>) ret).templateVariables = this.templateVariables;
    }
    return ret;
  }

  public Object put(T key, Object value) {
    return delegate.put(key, value);
  }

  public Object remove(Object key) {
    return delegate.remove(key);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @SuppressWarnings("unchecked")
  public void putAll(Map<? extends T, ? extends Object> m) {
    delegate.putAll(m);
    if (m instanceof ParametersImpl) {
      this.unnamedKeys.addAll(((ParametersImpl<T>) m).unnamedKeys);
    }
  }

  public Map<String, String> asStringMap() {
    Map<String, String> ret = new LinkedHashMap<>();
    for (T object : keySet()) {
      ret.put(object.toString(), ret.get(object) == null ? null : ret.get(object).toString());
    }
    return ret;
  }

  public Map<T, Object> asMap() {
    Map<T, Object> ret = new LinkedHashMap<>();
    for (T object : keySet()) {
      ret.put(object, ret.get(object) == null ? null : ret.get(object));
    }
    return ret;
  }

  public void clear() {
    delegate.clear();
  }

  public Set<T> keySet() {
    return delegate.keySet();
  }

  public Collection<Object> values() {
    return delegate.values();
  }

  public Set<Entry<T, Object>> entrySet() {
    return delegate.entrySet();
  }

  public boolean equals(Object o) {
    return delegate.equals(o);
  }

  public int hashCode() {
    return delegate.hashCode();
  }

  public Object getOrDefault(Object key, Object defaultValue) {
    return delegate.getOrDefault(key, defaultValue);
  }

  public void forEach(BiConsumer<? super T, ? super Object> action) {
    delegate.forEach(action);
  }

  public void replaceAll(BiFunction<? super T, ? super Object, ? extends Object> function) {
    delegate.replaceAll(function);
  }

  public Object putIfAbsent(T key, Object value) {
    return delegate.putIfAbsent(key, value);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void putUnnamed(Object value) {
    String name = "_p" + (unnamedKeys.size() + 1);
    unnamedKeys.add((T) name);
    put((T) name, value);
  }

  public boolean remove(Object key, Object value) {
    return delegate.remove(key, value);
  }

  public boolean replace(T key, Object oldValue, Object newValue) {
    return delegate.replace(key, oldValue, newValue);
  }

  public Object replace(T key, Object value) {
    return delegate.replace(key, value);
  }

  public Object computeIfAbsent(T key, Function<? super T, ? extends Object> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  public Object computeIfPresent(
      T key, BiFunction<? super T, ? super Object, ? extends Object> remappingFunction) {
    return delegate.computeIfPresent(key, remappingFunction);
  }

  public Object compute(
      T key, BiFunction<? super T, ? super Object, ? extends Object> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  public Object merge(
      T key,
      Object value,
      BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  @Override
  public boolean contains(T key) {
    return delegate.get(key) != null;
  }

  @Override
  public boolean contains(T key, Class<?> cls) {
    Object obj = delegate.get(key);
    return obj != null && cls.isAssignableFrom(obj.getClass());
  }

  @Override
  public List<T> getUnnamedKeys() {
    return unnamedKeys;
  }

  @Override
  public List<T> getNamedKeys() {
    List<T> ret = new ArrayList<>();
    for (T key : delegate.keySet()) {
      if (!unnamedKeys.contains(key)) {
        ret.add(key);
      }
    }
    return ret;
  }

  @Override
  public boolean containsAnyKey(T... keys) {
    if (keys != null) {
      for (T key : keys) {
        if (this.containsKey(key)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Parameters<T> rename(Map<T, T> translationTable) {
    Parameters<T> ret = new ParametersImpl<T>();
    for (T key : keySet()) {}

    throw new KlabUnimplementedException("ParameterImpl::rename");
  }

  @Override
  public boolean containsAny(Object... objects) {
    if (objects != null) {
      for (Object key : objects) {
        if (this.containsValue(key)) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K> K getAny(T... keys) {
    if (keys != null) {
      for (T key : keys) {
        K ret = (K) get(key);
        if (ret != null) {
          return (K) ret;
        }
      }
    }
    return null;
  }

  @Override
  public Map<T, Object> getLike(String string) {
    Map<T, Object> ret = new LinkedHashMap<>();
    for (T key : keySet()) {
      if (key.toString().startsWith(string)) {
        ret.put(key, get(key));
      }
    }
    return ret;
  }

  @Override
  public List<Object> getUnnamedArguments() {
    List<Object> ret = new ArrayList<>();
    for (T key : getUnnamedKeys()) {
      ret.add(get(key));
    }
    return ret;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Parameters<T> with(Parameters<String> state) {
    if (state != null && !state.isEmpty()) {
      ParametersImpl<T> ret = new ParametersImpl(this.delegate, this.unnamedKeys);
      ret.templateVariables = state;
      return ret;
    }
    return (Parameters<T>) this;
  }

  @Override
  public Parameters<String> getTemplateVariables() {
    return this.templateVariables;
  }

  public void setUnnamedKeys(List<T> unnamedKeys) {
    this.unnamedKeys = unnamedKeys;
  }

  public void setTemplateVariables(Parameters<String> templateVariables) {
    this.templateVariables = templateVariables;
  }
}
