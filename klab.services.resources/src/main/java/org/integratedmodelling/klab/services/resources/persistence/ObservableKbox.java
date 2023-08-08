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
package org.integratedmodelling.klab.services.resources.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.groovy.util.Maps;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabStorageException;
import org.integratedmodelling.klab.persistence.h2.H2Database;
import org.integratedmodelling.klab.persistence.h2.H2Kbox;
import org.integratedmodelling.klab.persistence.h2.SQL;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Design principles:
 * </p>
 * * This is a hybrid kbox that depends on the reasoner knowing all concepts in it. For this reason,
 * it holds an index of the definitions of any compound observable concept ever stored in it, and
 * assigns an integer ID to it. The table is read on startup so that all concepts are known to the
 * reasoner, and any that are not will not be retrievable.
 * <p>
 * * The main table is CONCEPTS, containing simply the ID of the type and its definition. A query
 * for any instance stored by derived kboxes with its ID in the set will return compatible
 * observables.
 * </p>
 * For convenience, the kbox also maintains a METADATA table for POD objects and exposes simple
 * methods to store/retrieve/delete metadata beans.
 * </p>
 * TODO For now the table does not maintain reference counts, so it is possible that concepts IDs
 * are referenced that are no longer represented because the corresponding observations that have
 * been deleted. Refcounting is easy to implement but costly at store/delete, so let's see how
 * problematic this becomes.
 * 
 * @author ferdinando.villa
 *
 */
public abstract class ObservableKbox extends H2Kbox {

    private Map<String, Long> definitionHash = new HashMap<>();
    private Map<Long, String> typeHash = new HashMap<>();
    private Map<String, Set<String>> coreTypeHash = new HashMap<>();
    private Map<String, Concept> conceptHash = new HashMap<>();

    protected Reasoner reasoner;
    protected Scope scope;
    protected ResourcesService resourceService;

    /**
     * The version is used to create storage on the file system. Change this when incompatible
     * changes are made to force a rebuild.
     */
    public static final String KBOX_VERSION = "0120v0";

    /*
     * exposed to allow preallocating connections in big imports.
     */
    public H2Database getDatabase() {
        return this.database;
    }

    public Observable getType(long id) {
        if (typeHash.containsKey(id)) {
            // FIXME this looks yucky - nonsemantic must compile just like the others
            // if (typeHash.get(id).startsWith("nonsemantic:")) {
            // return
            // reasoner.declareObservable(resourceService.resolveObservable(typeHash.get(id)));
            // }
            return reasoner.declareObservable(resourceService.resolveObservable(typeHash.get(id)));
        }
        return null;
    }

    public String getTypeDefinition(long id) {
        return typeHash.get(id);
    }

    protected String joinStringConditions(String field, Collection<?> stringValues, String operator) {

        String ret = "";

        for (Object o : stringValues) {
            ret += (ret.isEmpty() ? "" : (" " + operator + " ")) + field + " = '" + o + "'";
        }

        return ret;
    }

    /**
     * Get the ID of the table that contains the "primary" object we provide. Used to check for
     * empty database - if this is not there, either nothing needs to be done or initialization
     * needs to be performed.
     * 
     * @return
     */
    protected abstract String getMainTableId();

    /**
     * Delete all objects in the passed namespace and return the number of objects deleted.
     * 
     * @param namespaceId
     * @return
     * @throws KlabException
     */
    protected abstract int deleteAllObjectsWithNamespace(String namespaceId, Channel monitor);

    protected abstract void deleteObjectWithId(long id, Channel monitor);

    protected abstract void initialize(Channel monitor);

    public int clearNamespace(String namespaceId, Channel monitor) {

        initialize(monitor);

        if (!database.hasTable(getMainTableId())) {
            return 0;
        }

        int n = deleteAllObjectsWithNamespace(namespaceId, monitor);

        database.execute("DELETE FROM namespaces where id = '" + namespaceId + "';");

        return n;
    }

    /**
     * Count the objects in the main table.
     * 
     * @return number of observations
     */
    public long count() {

        if (!database.hasTable(getMainTableId())) {
            return 0;
        }
        List<Long> ret = database.queryIds("SELECT COUNT(*) from " + getMainTableId() + ";");
        return ret.size() > 0 ? ret.get(0) : 0l;
    }

    class ObservableSchema implements Schema {

        @Override
        public String getCreateSQL() {
            return "CREATE TABLE concepts (" + "oid LONG PRIMARY KEY, " + "definition VARCHAR(1024), " + "refcount LONG" + "); "
                    + "CREATE TABLE metadata (" + "fid LONG, " + "mkey VARCHAR(256), " + "mvalue OTHER" + ");"
                    + "CREATE INDEX concepts_oid_index ON concepts(oid); "
                    + "CREATE INDEX concepts_definition_index ON concepts(definition); "
                    + "CREATE INDEX metadata_oid_index ON metadata(fid); ";
        }

        @Override
        public String getTableName() {
            return "concepts";
        }
    }

    static public class NamespaceSchema implements Schema {

        @Override
        public String getCreateSQL() {
            // TODO FIXME primary key on NS URN is giving errors although there seems to be
            // no violation. Must investigate.
            // return "CREATE TABLE namespaces (" + "id VARCHAR(256) PRIMARY KEY, " + "timestamp
            // LONG, "
            // + "isscenario BOOLEAN" + "); " + "CREATE INDEX namespace_id_index ON namespaces(id);
            // ";
            return "CREATE TABLE namespaces (" + "id VARCHAR(256) PRIMARY KEY, " + "timestamp LONG, " + "isscenario BOOLEAN"
                    + "); " + "CREATE INDEX namespace_id_index ON namespaces(id); ";
        }

        @Override
        public String getTableName() {
            return "namespaces";
        }
    }

    static class NamespaceSerializer implements Serializer<KimNamespace> {

        @Override
        public String serialize(KimNamespace ns, long primaryKey, long foreignKey) {

            String ret = null;
            if (ns != null) {
                ret = "DELETE FROM namespaces WHERE id = '" + ns.getUrn() + "'; INSERT INTO namespaces VALUES ('"
                        + Utils.Escape.forSQL(ns.getUrn()) + "', " + ns.getTimestamp() + ", "
                        + (ns.isScenario() ? "TRUE" : "FALSE") + ");";
            }
            return ret;
        }
    }

    /**
     * Get the ID correspondent to the passed concept, and if unavailable return -1. Does not use
     * the database so it's very fast.
     * 
     * @param c
     * @return the ID for the concept, or -1 if not seen before
     */
    public long getConceptId(Concept c) {
        Long ret = definitionHash.get(c.getUrn());
        return ret == null ? -1l : ret;
    }

    public List<String> getKnownDefinitions() {
        List<String> ret = new ArrayList<>();
        ret.addAll(definitionHash.keySet());
        Collections.sort(ret);
        return ret;
    }

    /**
     * Check that the passed observable has been inserted, and if not make sure it is represented in
     * the database. Return the stable ID to use for storing records that use it.
     * 
     * @param observable
     * @param monitor
     * @return the ID for the observable, creating as necessary
     */
    public long requireConceptId(Concept observable, Channel monitor) {

        long ret = getConceptId(observable);
        if (ret >= 0) {
            return ret;
        }

        try {
            final String definition = observable.getUrn();

            ret = database.storeObject(observable, 0, new Serializer<Concept>(){

                @Override
                public String serialize(Concept o, /* Schema schema, */ long primaryKey, long foreignKey) {
                    return "INSERT INTO concepts VALUES (" + primaryKey + ", '" + definition + "', 1);";
                }
            }, monitor);

            definitionHash.put(definition, ret);
            typeHash.put(ret, definition);
            conceptHash.put(definition, observable);

            // store all existing definitions with same core type
            Concept coreType = reasoner.coreObservable(observable);
            String cdef = coreType.getUrn();
            Set<String> cset = coreTypeHash.get(cdef);
            if (cset == null) {
                cset = new HashSet<>();
                coreTypeHash.put(cdef, cset);
            }
            cset.add(definition);
            conceptHash.put(cdef, coreType);

        } catch (KlabException e) {
            throw new KlabStorageException(e);
        }

        return ret;
    }

    /**
     * Determine all the compatible MODEL concepts for which observables have been stored, and
     * return the set of their IDs.
     * 
     * If the core type is concrete, only that core type is looked up in the observable's parents,
     * so that models that observe that type (potentially with other traits not adopted by the
     * observable and in any compatible context) are found. If the core type is abstract or was
     * stated generic, any child is OK as long as trait, roles, inherency and context are
     * compatible.
     * 
     * @param observable
     * @return the IDs of all compatible concepts that have been used in the kbox.
     */
    public Set<Long> getCompatibleTypeIds(Observable observable, Concept context) {

        Set<Long> ret = new HashSet<>();
        Concept main = reasoner.coreObservable(observable);
        if (main == null) {
            /*
             * not a domain concept or abstract; can't have observables.
             */
            return ret;
        }

        /*
         * We lookup all models whose observable incarnates the core type, adding all possible
         * specific models if the observable is abstract or the context requires generic matching
         * ('any' dependencies). The initial set of candidates is weeded out of all incompatible or
         * unrepresented concepts later.
         */
        for (Concept candidate : getCandidates(main, observable.getDescriptionType().isInstantiation(),
                observable.getResolvedPredicates())) {

            /*
             * let an abstract model resolve a concrete observable if the abstract traits are in the
             * resolved predicates for the observable. If the observable contains the "specialized"
             * flag, we don't compare the inherency, letting through models that are contextualized
             * to specialized contexts
             */

            if (reasoner.semanticDistance(candidate, observable,
                    context)/*
                             * TODO handle the resolved predicates?
                             * candidate.getSemanticDistance(observable, context,
                             * !observable.isSpecialized(), ((Observable)
                             * observable).getResolvedPredicates())
                             */ >= 0) {
                // System.out.println(" YES");
                long id = getConceptId(candidate);
                if (id >= 0) {
                    ret.add(id);
                }
            } /*
               * else { System.out.println("    NOPE"); }
               */
        }

        return ret;
    }

    /*
     * FIXME use the description type directly
     */
    private Set<Concept> getCandidates(Concept concept, boolean instantiation, Map<Concept, Concept> resolvedPredicates) {

        Set<Concept> ret = new HashSet<>();
        for (Concept main : getAcceptableParents(concept, resolvedPredicates)) {

            Set<String> defs = coreTypeHash.get(main.getUrn());
            if (defs != null) {
                for (String def : defs) {
                    Concept candidate = conceptHash.get(def);
                    boolean ok = true;

                    if (candidate.is(SemanticType.PREDICATE)) {
                        // inherency must align with the resolution mode
                        boolean hasDistributedInherency = reasoner.hasDistributedInherency(candidate);
                        ok = (hasDistributedInherency && instantiation) || (!hasDistributedInherency && !instantiation);
                    }
                    if (ok) {
                        ret.add(candidate);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * We only accept abstract parent concepts (up to the base observable) if the observable is a
     * predicate.
     * 
     * @param concept
     * @param resolvedPredicates
     * @return
     */
    private List<Concept> getAcceptableParents(Concept concept, Map<Concept, Concept> resolvedPredicates) {

        List<Concept> ret = new ArrayList<>();
        ret.add(concept);
        if (concept.is(SemanticType.TRAIT) || concept.is(SemanticType.ROLE)) {
            Concept base = reasoner.baseParentTrait(concept);
            if (base == null) {
                return ret;
            }
            for (;;) {
                concept = reasoner.parent(concept);
                ret.add(concept);
                if (concept.isAbstract() || concept.equals(base)) {
                    break;
                }
            }
        }

        if (resolvedPredicates != null && !resolvedPredicates.isEmpty()) {
            List<Concept> rabs = new ArrayList<>();
            for (Concept r : ret) {
                rabs.add(replaceComponent(r, Maps.inverse(resolvedPredicates)));
            }
            ret.addAll(rabs);
        }

        return ret;
    }

    public ObservableKbox(String name, Scope scope) {

        super(name);

        this.scope = scope;
        this.reasoner = scope.getService(Reasoner.class);
        this.resourceService = scope.getService(ResourcesService.class);

        if (this.reasoner == null || this.resourceService == null) {
            throw new KIllegalStateException("cannot initialize kbox without a valid reasoner or resource service");
        }

        setSchema(Concept.class, new ObservableSchema());
        setSchema(KimNamespace.class, new NamespaceSchema());
        setSerializer(KimNamespace.class, new NamespaceSerializer());

        try {
            loadConcepts();
        } catch (KlabException e) {
            throw new KlabStorageException(e);
        }
    }

    private void loadConcepts() throws KlabException {

        if (!database.hasTable("concepts")) {
            return;
        }

        database.query("SELECT oid, definition FROM concepts", new SQL.SimpleResultHandler(){

            @Override
            public void onRow(ResultSet rs) {
                try {
                    definitionHash.put(rs.getString(2), rs.getLong(1));
                    typeHash.put(rs.getLong(1), rs.getString(2));
                } catch (SQLException e) {
                    throw new KlabStorageException(e);
                }
            }
        });
    }

    /**
     * De-nullify a string - use when filling in INSERT statements.
     * 
     * @param o
     * @return
     */
    protected static String cn(Object o) {
        return o == null ? "" : o.toString();
    }

    protected Map<String, String> getMetadataFor(long oid) throws KlabException {

        class Handler extends SQL.SimpleResultHandler {

            Map<String, String> ret = null;

            @Override
            public void onRow(ResultSet rs) {
                try {
                    String key = rs.getString(2);
                    Object value = rs.getObject(3);

                    if (key != null && value != null) {
                        if (ret == null) {
                            ret = new HashMap<>();
                        }
                        ret.put(key, value.toString());
                    }

                } catch (SQLException e) {
                    throw new KlabStorageException(e);
                }
            }
        }

        Handler handler = new Handler();
        database.query("SELECT * FROM metadata WHERE fid = " + oid, handler);

        return handler.ret;
    }

    protected void deleteMetadataFor(long oid) throws KlabException {
        database.execute("DELETE FROM metadata WHERE fid = " + oid);
    }

    protected void storeMetadataFor(long oid, Map<String, String> metadata) {

        for (String s : metadata.keySet()) {

            String sql = " INSERT INTO metadata VALUES (" + oid + ", "// +
                                                                      // "fid
                                                                      // LONG,
                                                                      // "
                    + "'" + s + "', "// + "key VARCHAR(256), "
                    + "?"// + "value OTHER"
                    + ")";
            try {
                /*
                 * OK, must execute these right away unfortunately - so if something goes wrong with
                 * the object's storage these will remain in the DB.
                 */
                PreparedStatement prsql = database.getConnection().prepareStatement(sql);
                prsql.setObject(1, metadata.get(s), Types.JAVA_OBJECT);
                prsql.executeUpdate();
            } catch (Exception e) {
                throw new KlabStorageException(e);
            }
        }
    }

    /**
     * Pass the a namespace to check if its objects need to be stored. If the stored namespace
     * record does not exist or has a timestamp older than the passed one, remove all objects that
     * belong to it and return true. Does not store a new namespace record - this should be done
     * when this has returned true and there were no errors.
     * 
     * Returns: 0 if no need to refresh, 1 if it must be entirely refreshed and every model and
     * namespace record is removed from the kbox, and 2 if the models without errors need to be
     * checked again (they may be in or not).
     * 
     * 
     * @param namespace
     * @param monitor
     * @return result action code
     * @throws KlabException
     */
    public int removeIfOlder(KimNamespace namespace, Channel monitor) throws KlabException {

        if (!database.hasTable("namespaces")) {
            return 1;
        }

        long dbTimestamp = getNamespaceTimestamp(namespace);
        long timestamp = namespace.getTimestamp();

        /*
         * if we have stored something and we are younger than the stored ns, remove all models
         * coming from it so we can add our new ones.
         */
        if (timestamp > dbTimestamp) {

            if (dbTimestamp > 0) {

                monitor.debug("Removing all observations in namespace " + namespace.getUrn());
                int removed = clearNamespace(namespace.getUrn(), monitor);
                monitor.debug("Removed " + removed + " observations.");
            }

            monitor.debug("Refreshing observations in " + namespace.getUrn() + ": stored  " + new Date(dbTimestamp) + " < "
                    + new Date(timestamp));

            return 1;
        }

        /*
         * if we have not changed the source file but models had errors when stored, return the
         * conservative mode so we can check model by model and only store those that are no longer
         * in error due to external reasons.
         */
        if (namespace != null && Utils.Notifications.hasErrors(namespace.getNotifications())) {
            return 2;
        }

        return 0;
    }

    public void remove(String namespaceId, Channel monitor) throws KlabException {

        if (!database.hasTable("namespaces")) {
            return;
        }
        monitor.debug("Removing all observations in namespace " + namespaceId);
        int removed = clearNamespace(namespaceId, monitor);
        monitor.debug("Removed " + removed + " observations.");
    }

    /**
     * Return 0 if namespace is not in the kbox, or the (long) timestamp of the namespace if it is.
     * 
     * @param namespace
     * 
     * @return result code
     * @throws KlabException
     */
    public long getNamespaceTimestamp(KimNamespace namespace) throws KlabException {

        if (!database.hasTable("namespaces")) {
            return 0l;
        }
        List<Long> ret = database
                .queryIds("SELECT timestamp FROM namespaces WHERE id = '" + Utils.Escape.forSQL(namespace.getUrn()) + "';");
        return ret.size() > 0 ? ret.get(0) : 0l;
    }

    protected static String nullify(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        return string;
    }

    /**
     * TODO use a proper builder
     * 
     * @param original
     * @param replacements
     * @return
     */
    protected Concept replaceComponent(Concept original, Map<Concept, Concept> replacements) {

        if (replacements.isEmpty()) {
            return original;
        }

        String declaration = original.getUrn();
        for (Concept key : replacements.keySet()) {
            String rep = replacements.get(key).getUrn();
            if (rep.contains(" ")) {
                rep = "(" + rep + ")";
            }
            declaration = declaration.replace(key.getUrn(), rep);
        }

        return reasoner.declareConcept(resourceService.resolveConcept(declaration));
    }

}
