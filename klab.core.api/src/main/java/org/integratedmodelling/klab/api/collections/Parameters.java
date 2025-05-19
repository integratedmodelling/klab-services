package org.integratedmodelling.klab.api.collections;

import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API for a read-only, nicer to use Map<String, Object> that collects named parameters of a
 * function. Implemented in {@link ParametersImpl} which can be used as a drop-in replacement for a
 * parameter map.
 *
 * @author ferdinando.villa
 */
public interface Parameters<T> extends Map<T, Object>, Serializable {

  /**
   * Get the value as the passed type, if necessary converting between numeric types or casting to
   * strings.
   *
   * @param name
   * @param cls the expected class of the result
   * @return a plain Java object
   * @throws IllegalArgumentException if the requested class is incompatible with the type.
   */
  <K> K get(T name, Class<K> cls);

  /**
   * Get the value as a list of the passed type, if necessary converting between numeric types or
   * casting to strings. If the actual value is a scalar or an array, a list is built.
   *
   * @param name
   * @param cls the expected class of the result
   * @return a plain Java object
   * @throws IllegalArgumentException if the requested class is incompatible with the type.
   */
  <K> List<K> getList(T name, Class<K> cls);

  /**
   * Get the value as the passed type, if necessary converting between numeric types or casting to
   * strings. If the result is null, do your best to convert to a suitable primitive POD so that it
   * can be assigned to one without NPEs, but with possible inaccuracies (e.g. ints and longs will
   * be 0).
   *
   * @param name
   * @param cls the expected class of the result
   * @return a plain Java object
   * @throws IllegalArgumentException if the requested class is incompatible with the type.
   */
  <K> K getNotNull(T name, Class<K> cls);

  /**
   * Get the value as the passed type, returning a set default if the value is not there, otherwise
   * converting if necessary between numeric types or casting to strings.
   *
   * @param name
   * @param defaultValue the default value returned if the map does not contain the value; also
   *     specifies the expected class of the result and a potential conversion if found.
   * @return a plain Java object
   * @throws IllegalArgumentException if the requested class is incompatible with the type.
   */
  <K> K get(T name, K defaultValue);

  /**
   * Return the value that matches any of the passed keys, or null.
   *
   * @param <K>
   * @param keys
   * @return
   */
  <K> K getAny(T... keys);

  /**
   * When used as a parameter list parsed from a function call, this may contain arguments that are
   * unnamed. These are given default names and if any is present, their names are returned here.
   * Usage of this functionality is restricted to T == String.class and any usage outside of that
   * will generate runtime errors.
   *
   * @return a list of unnamed argument keys, possibly empty.
   */
  List<T> getUnnamedKeys();

  /**
   * Return all the unnamed arguments in order of declaration.
   *
   * @return
   */
  List<Object> getUnnamedArguments();

  /**
   * Return all the keys that correspond to named parameters.
   *
   * @return a list of unnamed argument keys, possibly empty.
   */
  List<T> getNamedKeys();

  @SuppressWarnings("unchecked")
  void putUnnamed(Object value);

  /**
   * Like {@link #containsKey(Object)}, except it returns false also if the key is there but the
   * corresponding object is null.
   *
   * @param key
   * @return false if key is not there or points to a null object
   */
  boolean contains(T key);

  /**
   * Check if an object is present for the key and it is of the passed class.
   *
   * @param key
   * @param cls
   * @return true if object is there and belongs to cls
   */
  boolean contains(T key, Class<?> cls);

  /**
   * True if this contains any of the passed keys
   *
   * @param keys
   * @return
   */
  boolean containsAnyKey(T... keys);

  /**
   * Return a new Parameters object with the corresponding names translated according to the
   * translation table passed.
   *
   * @param translationTable A map old->new name
   * @return the translated parameter table
   */
  Parameters<T> rename(Map<T, T> translationTable);

  /**
   * True if this contains any of the passed values
   *
   * @param objects
   * @return
   */
  boolean containsAny(Object... objects);

  /**
   * Return the subset of the map whose keys start with the passed string.
   *
   * @param string
   * @return
   */
  Map<T, Object> getLike(String string);

  /**
   * Return a new parameter object with the same content that automatically resolves templated
   * values using the passed map.
   *
   * @param templateVariables
   * @return
   */
  Parameters<T> with(Parameters<String> templateVariables);

  /**
   * If {@link #with(Parameters)} has been called, return the variables, otherwise return null.
   *
   * @return
   */
  Parameters<String> getTemplateVariables();

  Map<T, Object> asMap();

  /**
   * Create a parameters object from a list of key/value pairs, optionally including also other
   * (non-paired) map objects whose values are added as is. A null in first position of a pair is
   * ignored, as well as anything whose key is {@link #IGNORED_PARAMETER}.
   *
   * @param o
   * @return
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> Parameters<T> create(Object... o) {
    Map<T, Object> inp = new LinkedHashMap<T, Object>();
    if (o != null) {
      for (int i = 0; i < o.length; i++) {
        if (o[i] instanceof Map) {
          inp.putAll((Map) o[i]);
        } else if (o[i] != null) {
          if (!ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
            inp.put((T) o[i], o[i + 1]);
          }
          i++;
        }
      }
    }
    return new ParametersImpl(inp);
  }

  /**
   * Like the other create() but also ignores null values for non-null keys.
   *
   * @param <T>
   * @param o
   * @return
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> Parameters<T> createNotNull(Object... o) {
    Map<T, Object> inp = new LinkedHashMap<T, Object>();
    if (o != null) {
      for (int i = 0; i < o.length; i++) {
        if (o[i] instanceof Map) {
          inp.putAll((Map) o[i]);
        } else if (o[i] != null) {
          if (o[1 + 1] != null && !ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
            inp.put((T) o[i], o[i + 1]);
          }
          i++;
        }
      }
    }
    return new ParametersImpl(inp);
  }

  /**
   * Create a parameters object from a list of key/value pairs, optionally including also other
   * (non-paired) map objects whose values are added as is. A null in first position of a pair is
   * ignored, as well as anything whose key is {@link #IGNORED_PARAMETER}.
   *
   * @param o
   * @return
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> Parameters<T> createSynchronized(Object... o) {
    Map<T, Object> inp = Collections.synchronizedMap(new LinkedHashMap<T, Object>());
    if (o != null) {
      for (int i = 0; i < o.length; i++) {
        if (o[i] instanceof Map) {
          inp.putAll((Map) o[i]);
        } else if (o[i] != null) {
          if (!ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
            inp.put((T) o[i], o[i + 1]);
          }
          i++;
        }
      }
    }
    return new ParametersImpl(inp);
  }

  /**
   * Wrap an existing map and enjoy.
   *
   * @param <T>
   * @param map
   * @return
   */
  public static <T> Parameters<T> wrap(Map<T, Object> map) {
    return new ParametersImpl<T>(map);
  }
}
