/*******************************************************************************
 * Copyright (C) 2007, 2015:
 * 
 * - Ferdinando Villa <ferdinando.villa@bc3research.org> - integratedmodelling.org - any other
 * authors listed in @author annotations
 *
 * All rights reserved. This file is part of the k.LAB software suite, meant to enable modular,
 * collaborative, integrated development of interoperable data and model components. For details,
 * see http://integratedmodelling.org.
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *
 * This program is distributed in the hope that it will be useful, but without any warranty; without
 * even the implied warranty of merchantability or fitness for a particular purpose. See the Affero
 * General Public License for more details.
 * 
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.common.knowledge;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;

/**
 * A map indexed by concepts, whose get() method will select the entry that best corresponds to the
 * passed concept using reasoning instead of equality. If the key concept has been explicitly
 * inserted, the correspondent value is returned; otherwise the hierarchy of the requested key is
 * walked upstream and any concept inserted that matches a parent is used as key.
 * 
 * @author Ferdinando
 *
 * @param <T>
 */
public class IntelligentMap<T> implements Map<Concept, T> {

    HashMap<String, T> data = new HashMap<>();
    HashMap<String, T> cache = new HashMap<>();
    HashMap<String, Set<String>> closure = new HashMap<>();
    HashMap<Concept, T> original = new HashMap<>();
    Scope scope;

    private T defaultValue = null;

    public IntelligentMap(Scope scope) {
        this.scope = scope;
    }

    /**
     * Use this constructor if you want a default value to be returned on no match instead of null.
     * 
     * @param defaultValue
     */
    public IntelligentMap(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    public T get(Object object) {

        if (!(object instanceof Concept)) {
            return null;
        }

        Concept concept = (Concept) object;
        String definition = concept.getUrn();

        // cached
        if (cache.containsKey(definition)) {
            T ret = cache.get(definition);
            return ret == null ? defaultValue : ret;
        }

        // direct
        T ret = data.get(definition);

        // OK, indirect
        if (ret /* still */ == null) {
            for (String def : this.closure.keySet()) {
                if (this.closure.get(def).contains(definition)) {
                    ret = data.get(def);
                    break;
                }
            }
        }

        // cache nulls
        cache.put(definition, ret);

        return ret == null ? defaultValue : ret;

    }

    public T getValue(Concept key) {
        return original.get(key);
    }

    @Override
    public T put(Concept concept, T data) {

        cache.remove(concept.getUrn());

        if (!closure.containsKey(concept.getUrn())) {
            Set<String> clss = new HashSet<>();
            for (Concept c : scope.getService(Reasoner.class).closure(concept)) {
                clss.add(c.getUrn());
            }
            this.closure.put(concept.getUrn(), clss);
        }
        this.data.put(concept.getUrn(), data);
        return this.original.put(concept, data);
    }

    @Override
    public void clear() {
        // don't clear the expensive knowledge base
        original.clear();
        data.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return original.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return original.containsValue(value);
    }

    @Override
    public Set<Map.Entry<Concept, T>> entrySet() {
        return original.entrySet();
    }

    @Override
    public boolean isEmpty() {
        return original.isEmpty();
    }

    @Override
    public Set<Concept> keySet() {
        return original.keySet();
    }

    @Override
    public void putAll(Map<? extends Concept, ? extends T> m) {
        for (Concept c : m.keySet()) {
            put(c, m.get(c));
        }
    }

    @Override
    public T remove(Object key) {
        if (key instanceof Concept) {
            data.remove(((Concept) key).getUrn());
        }
        return original.remove(key);
    }

    @Override
    public int size() {
        return original.size();
    }

    @Override
    public Collection<T> values() {
        return original.values();
    }

}
