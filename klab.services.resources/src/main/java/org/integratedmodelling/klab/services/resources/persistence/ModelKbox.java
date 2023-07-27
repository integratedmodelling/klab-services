package org.integratedmodelling.klab.services.resources.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.h2gis.utilities.SpatialResultSet;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.EnumeratedExtension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabStorageException;
import org.integratedmodelling.klab.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.persistence.h2.SQL;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.services.resources.persistence.ModelReference.Mediation;
import org.locationtech.jts.geom.Geometry;

public class ModelKbox extends ObservableKbox {

	// private boolean workRemotely = !Configuration.INSTANCE.isOffline();
	private boolean initialized = false;

	/**
	 * Create a kbox with the passed name. If the kbox exists, open it and return
	 * it.
	 * 
	 * @param name
	 * @return a new kbox
	 */
	public static ModelKbox create(String name, Scope scope) {
		return new ModelKbox(name, scope);
	}

	private ModelKbox(String name, Scope scope) {
		super(name, scope);
	}

	@Override
	protected void initialize(Channel monitor) {

		if (!initialized) {

			initialized = true;

			setSchema(ModelReference.class, new Schema() {

				@Override
				public String getTableName() {
					return getMainTableId();
				}

				@Override
				public String getCreateSQL() {
					String ret = "CREATE TABLE model (" + "oid LONG, " + "serverid VARCHAR(64), " + "id VARCHAR(256), "
							+ "name VARCHAR(256), " + "namespaceid VARCHAR(128), " + "projectid VARCHAR(128), "
							+ "typeid LONG, " + "otypeid LONG, " + "scope VARCHAR(16), " + "isresolved BOOLEAN, "
							+ "isreification BOOLEAN, " + "inscenario BOOLEAN, " + "hasdirectobjects BOOLEAN, "
							+ "hasdirectdata BOOLEAN, " + "timestart LONG, " + "timeend LONG, " + "isspatial BOOLEAN, "
							+ "istemporal BOOLEAN, " + "timemultiplicity LONG, " + "spacemultiplicity LONG, "
							+ "scalemultiplicity LONG, " + "dereifyingattribute VARCHAR(256), "
							+ "minspatialscale INTEGER, " + "maxspatialscale INTEGER, " + "mintimescale INTEGER, "
							+ "maxtimescale INTEGER, " + "space GEOMETRY, " + "observationtype VARCHAR(256), "
							+ "enumeratedspacedomain VARCHAR(256), " + "enumeratedspacelocation VARCHAR(1024), "
							+ "specializedObservable BOOLEAN " + "); " + "CREATE INDEX model_oid_index ON model(oid); "
					// + "CREATE SPATIAL INDEX model_space ON model(space);"
					;

					return ret;

				}
			});

			setSerializer(ModelReference.class, new Serializer<ModelReference>() {

				private String cn(Object o) {
					return o == null ? "" : o.toString();
				}

				@Override
				public String serialize(ModelReference model, long primaryKey, long foreignKey) {

					long tid = requireConceptId(model.getObservableConcept(), monitor);

					String ret = "INSERT INTO model VALUES (" + primaryKey + ", " + "'" + cn(model.getServerId())
							+ "', " + "'" + cn(model.getName()) + "', " + "'" + cn(model.getName()) + "', " + "'"
							+ cn(model.getNamespaceId()) + "', " + "'" + cn(model.getProjectId()) + "', " + tid + ", "
							+ /* observation concept is obsolete oid */ 0 + ", '" + (model.getScope().name()) + "', "
							+ (model.isResolved() ? "TRUE" : "FALSE") + ", "
							+ (model.isReification() ? "TRUE" : "FALSE") + ", "
							+ (model.isInScenario() ? "TRUE" : "FALSE") + ", "
							+ (model.isHasDirectObjects() ? "TRUE" : "FALSE") + ", "
							+ (model.isHasDirectData() ? "TRUE" : "FALSE") + ", " + model.getTimeStart() + ", "
							+ model.getTimeEnd() + ", " + (model.isSpatial() ? "TRUE" : "FALSE") + ", "
							+ (model.isTemporal() ? "TRUE" : "FALSE") + ", " + model.getTimeMultiplicity() + ", "
							+ model.getSpaceMultiplicity() + ", " + model.getScaleMultiplicity() + ", " + "'"
							+ cn(model.getDereifyingAttribute()) + "', " + model.getMinSpatialScaleFactor() + ", "
							+ model.getMaxSpatialScaleFactor() + ", " + model.getMinTimeScaleFactor() + ", "
							+ model.getMaxTimeScaleFactor() + ", " + "'"
							+ (model.getShape() == null ? "GEOMETRYCOLLECTION EMPTY"
									: ShapeImpl.promote(model.getShape()).getStandardizedGeometry().toString())
							+ "', '" + model.getObservationType() + "', '" + cn(model.getEnumeratedSpaceDomain())
							+ "', '" + cn(model.getEnumeratedSpaceLocation()) + "', "
							+ (model.isSpecializedObservable() ? "TRUE" : "FALSE") + ");";

					if (model.getMetadata() != null && model.getMetadata().size() > 0) {
						storeMetadataFor(primaryKey, model.getMetadata());
					}

					return ret;
				}
			});

		}
	}

	/**
	 * Pass the output of queryModelData to a contextual prioritizer and return the
	 * ranked list of IModels. If we're a personal engine, also broadcast the query
	 * to the network and merge results before returning.
	 * 
	 * @param observable
	 * @param resolutionScope
	 * @return models resulting from query, best first.
	 * @throws KlabException
	 */
	public Collection<ModelReference> query(Observable observable, ContextScope scope) throws KlabException {

		initialize(scope);

		// Resolution resolution = scope.getData().get(Resolution.)

		// Contextualize the observable if needed. Don't do it if we're a predicate or
		// if
		// contextualization is deferred.

		/**
		 * CHECK all this logic should be moved to the resolver; if we get here we need
		 * to resolve
		 */
		// if (resolutionScope.getContext() != null &&
		// !observable.getType().is(Type.PREDICATE)
		// && observable.mustContextualizeAtResolution()) {
		// observable = Observables.INSTANCE.contextualizeTo(observable,
		// resolutionScope.getContext().getObservable().getType(),
		// true, resolutionScope.getMonitor());
		// }
		//
		// Pair<Scale, Collection<Model>> preResolved = resolutionScope.isCaching()
		// ? null
		// : resolutionScope.getPreresolvedModels(observable);

		// Prioritizer<ModelReference> prioritizer =
		// Resolver.getPrioritizer(resolutionScope);
		// ModelQueryResult ret = new ModelQueryResult(prioritizer,
		// resolutionScope.getMonitor());
		Set<ModelReference> local = new HashSet<>();

		/*
		 * use previously resolved
		 * 
		 * TODO check use of contains(): overlaps() would be more correct but then we
		 * would need to continue resolving, which misses the whole point of caching,
		 * and limit the resolution to "other" models.
		 * 
		 * FIXME: MODELS FROM SCENARIOS MUST STILL TAKE OVER THESE!
		 */
		// if (preResolved != null &&
		// preResolved.getFirst().contains(resolutionScope.getCoverage())) {
		//
		// for (IRankedModel model : preResolved.getSecond()) {
		// // rank them again in our scale
		// ret.addCachedModel(model);
		// }
		//
		// if (!Configuration.INSTANCE.resolveAllInstances()) {
		// resolutionScope.getMonitor().debug("Model for " + observable + " was preset
		// at
		// resolution");
		// return ret;
		// }
		// }

		/*
		 * only query locally if we've seen a model before.
		 */
		if (database.hasTable("model")) {
			for (ModelReference md : queryModels(observable, scope)) {
				if (md.getPermissions().checkAuthorization(scope)) {
					local.add(md);
					// ret.addModel(md);
				}
			}
		}

		/*
		 * Warn and provide output if models were chosen but reported unavailability.
		 * Message is a warning only if no other models were found. TODO move this to
		 * the resolver
		 */
		// if (ret.getOfflineModels().size() > 0) {
		//
		// String message = "warning: " + ret.getOfflineModels().size() + " model"
		// + (ret.getOfflineModels().size() < 2 ? " was" : "s were") + " chosen but
		// found offline:
		// ";
		//
		// for (ModelReference m : ret.getOfflineModels()) {
		// message += "\n " + m.getName();
		// }
		//
		// if (ret.size() > 0) {
		// scope.info(message);
		// } else {
		// scope.warn(message);
		// }
		//
		// for (ModelReference ref : ret.getOfflineModels()) {
		// scope.debug("model " + ref.getName() + " is offline");
		// }
		// }

		return local;
	}

	/**
	 * Find and deserialize all modeldata matching the parameters. Do not rank or
	 * anything.
	 * 
	 * @param observable
	 * @param context
	 * @return all unranked model descriptors matching the query
	 * @throws KlabException
	 */
	public List<ModelReference> queryModels(Observable observable, ContextScope context) {

		List<ModelReference> ret = new ArrayList<>();

		if (!database.hasTable("model")) {
			return ret;
		}

		String query = "SELECT model.oid FROM model WHERE ";
		Concept contextObservable = context.getResolutionObservation() == null ? null
				: context.getResolutionObservation().getObservable().getSemantics();
		String typequery = observableQuery(observable, contextObservable);

		if (typequery == null) {
			return ret;
		}

		query += "(" + scopeQuery(context, observable) + ")";
		query += " AND (" + typequery + ")";
		if (context.getGeometry().getSpace() != null) {
			String sq = spaceQuery(context.getGeometry().getSpace());
			if (!sq.isEmpty()) {
				query += " AND (" + sq + ")";
			}
		}

		String tquery = timeQuery(context.getGeometry().getTime());
		if (!tquery.isEmpty()) {
			query += " AND (" + tquery + ");";
		}

		// KLAB.info(query);

		final List<Long> oids = database.queryIds(query);

		for (long l : oids) {
			ModelReference model = retrieveModel(l, context);
			if (model != null) {
				if (model.getPermissions().checkAuthorization(context)) {
					Coverage coverage = resourceService.modelGeometry(model.getName());
					if (!coverage.checkConstraints(context.getGeometry())) {
						resourceService.scope().debug("model " + model.getName() + " of " + observable
								+ " discarded because of coverage constraints mismatch");
						continue;
					}
					ret.add(model);
				}
			}
		}

		resourceService.scope().info("model query for " + observable.getDescriptionType() + " of " + observable
				+ " found " + (ret.size() == 1 ? ret.get(0).getName() : (ret.size() + " models")));

		return ret;
	}

	// private boolean isAuthorized(ModelReference model, IObservable observable,
	// Set<String>
	// userPermissions,
	// Collection<IResolutionConstraint> constraints) {
	//
	// if (model.getProjectId() != null) {
	// Set<String> permissions =
	// Authentication.INSTANCE.getProjectPermissions(model.getProjectId());
	// if (!permissions.isEmpty()) {
	// if (Sets.intersection(permissions, userPermissions).size() == 0) {
	// return false;
	// }
	// }
	// }
	//
	// if (constraints != null) {
	// for (IResolutionConstraint c : constraints) {
	// KlabAsset m = resourceService.resolveAsset(model.getUrn());
	// if (m instanceof KimModelStatement) {
	// if (!c.accepts((IModel) m, observable)) {
	// return false;
	// }
	// }
	// }
	// }
	//
	// return true;
	// }

	private String observableQuery(Observable observable, Concept context) {

		Set<Long> ids = this.getCompatibleTypeIds(observable, context);
		if (ids == null || ids.size() == 0) {
			return null;
		}
		String ret = "";
		for (long id : ids) {
			ret += (ret.isEmpty() ? "" : ", ") + id;
		}
		return "typeid IN (" + ret + ")";
	}

	/*
	 * select models that are [instantiators if required] AND:] [private and in the
	 * home namespace if not dummy OR] [project private and in the home project if
	 * not dummy OR] (non-private and non-scenario) OR (in any of the scenarios in
	 * the context).
	 */
	private String scopeQuery(ContextScope context, Observable observable) {

		String ret = "";
		String projectId = null;
		String namespaceId = context.getResolutionNamespace() == null ? DUMMY_NAMESPACE_ID
				: context.getResolutionNamespace();
		if (!namespaceId.equals(DUMMY_NAMESPACE_ID)) {
			ret += "(model.namespaceid = '" + namespaceId + "')";
			projectId = context.getResolutionProject();
		}

		ret += (ret.isEmpty() ? "" : " OR ") + "((NOT model.scope = 'NAMESPACE') AND (NOT model.inscenario))";

		if (context.getResolutionScenarios().size() > 0) {
			ret += " OR (" + joinStringConditions("model.namespaceid", context.getResolutionScenarios(), "OR") + ")";
		}

		if (observable.is(SemanticType.COUNTABLE)) {
			if (observable.getDescriptionType().isInstantiation()) {
				ret = "(" + ret + ") AND model.isreification";
			} else {
				ret = "(" + ret + ") AND (NOT model.isreification)";
			}
		}

		if (projectId != null) {
			ret += " AND (NOT (model.scope = 'PROJECT' AND model.projectid <> '" + projectId + "'))";
		}

		return ret;
	}

	/*
	 * select models that intersect the given space or have no space at all. TODO
	 * must match geometry when forced - if it has @intensive(space, time) it
	 * shouldn't match no space/time OR non-distributed space/time. ALSO the
	 * dimensionality!
	 */
	private String spaceQuery(Space space) {

		space = resolveEnumeratedExtensions(space);

		if (space instanceof EnumeratedExtension) {
			// Accept anything that is from the same authority or baseconcept. If the
			// requesting
			// context needs specific values, these should be checked later in the
			// prioritizer.
			// Pair<String, String> defs = ((EnumeratedExtension)
			// space).getExtentDescriptors();
			// return "model.enumeratedspacedomain = '" + defs.getFirst() + "'";
			throw new KlabUnimplementedException("enumerated extension");
		}

		if (space.getShape().isEmpty()) {
			return "";
		}

		String scalequery = space.getRank() + " BETWEEN model.minspatialscale AND model.maxspatialscale";

		String spacequery = "model.space && '" + ShapeImpl.promote(space.getGeometricShape()).getStandardizedGeometry()
				+ "' OR ST_IsEmpty(model.space)";

		return "(" + scalequery + ") AND (" + spacequery + ")";
	}

	/*
	 * Entirely TODO. For initialization we should use time only to select for most
	 * current info - either closer to the context or to today if time is null. For
	 * dynamic models we should either not have a context or cover the context.
	 * Guess this is the job of the prioritizer, and we should simply let anything
	 * through except when we look for T1(n>1) models.
	 * 
	 * TODO must match geometry when forced - if it has @intensive(space, time) it
	 * shouldn't match no space/time OR non-distributed space/time. ALSO the
	 * dimensionality!
	 */
	private String timeQuery(Time time) {

		time = resolveEnumeratedExtensions(time);

		if (time /* still */ instanceof EnumeratedExtension) {
			// TODO
			throw new KlabUnimplementedException("enumerated extension");
		}

		String ret = "";
		boolean checkBoundaries = false;
		if (time != null && checkBoundaries) {
			ret = "(timestart == -1 AND timeend == -1) OR (";
			long start = time.getStart() == null ? -1 : time.getStart().getMilliseconds();
			long end = time.getEnd() == null ? -1 : time.getEnd().getMilliseconds();
			if (start > 0 && end > 0) {
				ret += "timestart >= " + start + " AND timeend <= " + end;
			} else if (start > 0) {
				ret += "timestart >= " + start;
			} else if (end > 0) {
				ret += "timeend <= " + end;
			}
			ret += ")";
		}
		return ret;
	}

	public List<ModelReference> retrieveAll(Channel monitor) throws KlabException {

		initialize(monitor);

		List<ModelReference> ret = new ArrayList<>();
		if (!database.hasTable("model")) {
			return ret;
		}
		for (long oid : database.queryIds("SELECT oid FROM model;")) {
			ret.add(retrieveModel(oid, monitor));
		}
		return ret;
	}

	public ModelReference retrieve(String query, Channel monitor) {
		initialize(monitor);

		final ModelReference ret = new ModelReference();

		database.query(query, new SQL.SimpleResultHandler() {
			@Override
			public void onRow(ResultSet rs) {

				try {

					SpatialResultSet srs = rs.unwrap(SpatialResultSet.class);

					long tyid = srs.getLong(7);

					ret.setName(srs.getString(4));

					Concept mtype = getType(tyid).asConcept();

					ret.setObservableConcept(mtype);
					ret.setObservable(getTypeDefinition(tyid));

					ret.setServerId(nullify(srs.getString(2)));
					// ret.setId(srs.getString(3));

					ret.setNamespaceId(srs.getString(5));
					ret.setProjectId(nullify(srs.getString(6)));

					ret.setScope(KimStatement.Scope.valueOf(srs.getString(9)));
					ret.setResolved(srs.getBoolean(10));
					ret.setReification(srs.getBoolean(11));
					ret.setInScenario(srs.getBoolean(12));
					ret.setHasDirectObjects(srs.getBoolean(13));
					ret.setHasDirectData(srs.getBoolean(14));
					ret.setTimeStart(srs.getLong(15));
					ret.setTimeEnd(srs.getLong(16));
					ret.setSpatial(srs.getBoolean(17));
					ret.setTemporal(srs.getBoolean(18));
					ret.setTimeMultiplicity(srs.getLong(19));
					ret.setSpaceMultiplicity(srs.getLong(20));
					ret.setScaleMultiplicity(srs.getLong(21));
					ret.setDereifyingAttribute(nullify(srs.getString(22)));
					ret.setMinSpatialScaleFactor(srs.getInt(23));
					ret.setMaxSpatialScaleFactor(srs.getInt(24));
					ret.setMinTimeScaleFactor(srs.getInt(25));
					ret.setMaxTimeScaleFactor(srs.getInt(26));
					Geometry geometry = srs.getGeometry(27);
					if (!geometry.isEmpty()) {
						ret.setShape(Shape.create(geometry.toText(), Projection.getLatLon())); // +
					}
				} catch (SQLException e) {
					throw new KlabStorageException(e);
				}
			}

		});

		return ret;
	}

	public ModelReference retrieveModel(long oid, Channel monitor) throws KlabException {

		ModelReference ret = retrieve("SELECT * FROM model WHERE oid = " + oid, monitor);
		ret.setMetadata(getMetadataFor(oid));
		return ret;
		//
		// initialize(monitor);
		//
		// final ModelReference ret = new ModelReference();
		//
		// database.query("SELECT * FROM model WHERE oid = " + oid, new
		// SQL.SimpleResultHandler() {
		// @Override
		// public void onRow(ResultSet rs) {
		//
		// try {
		//
		// SpatialResultSet srs = rs.unwrap(SpatialResultSet.class);
		//
		// long tyid = srs.getLong(7);
		//
		// ret.setName(srs.getString(4));
		//
		// IConcept mtype = getType(tyid);
		//
		// ret.setObservableConcept(mtype);
		// ret.setObservable(getTypeDefinition(tyid));
		//
		// ret.setServerId(nullify(srs.getString(2)));
		// ret.setId(srs.getString(3));
		//
		// ret.setNamespaceId(srs.getString(5));
		// ret.setProjectId(nullify(srs.getString(6)));
		//
		// ret.setPrivateModel(srs.getBoolean(9));
		// ret.setResolved(srs.getBoolean(10));
		// ret.setReification(srs.getBoolean(11));
		// ret.setInScenario(srs.getBoolean(12));
		// ret.setHasDirectObjects(srs.getBoolean(13));
		// ret.setHasDirectData(srs.getBoolean(14));
		// ret.setTimeStart(srs.getLong(15));
		// ret.setTimeEnd(srs.getLong(16));
		// ret.setSpatial(srs.getBoolean(17));
		// ret.setTemporal(srs.getBoolean(18));
		// ret.setTimeMultiplicity(srs.getLong(19));
		// ret.setSpaceMultiplicity(srs.getLong(20));
		// ret.setScaleMultiplicity(srs.getLong(21));
		// ret.setDereifyingAttribute(nullify(srs.getString(22)));
		// ret.setMinSpatialScaleFactor(srs.getInt(23));
		// ret.setMaxSpatialScaleFactor(srs.getInt(24));
		// ret.setMinTimeScaleFactor(srs.getInt(25));
		// ret.setMaxTimeScaleFactor(srs.getInt(26));
		// Geometry geometry = srs.getGeometry(27);
		// if (!geometry.isEmpty()) {
		// ret.setShape(Shape.create(geometry, Projection.getLatLon())); // +
		// }
		// } catch (SQLException e) {
		// throw new KlabStorageException(e);
		// }
		// }
		//
		// });
		//
		// ret.setMetadata(getMetadataFor(oid));
		//
		// return ret;
	}

	@Override
	protected String getMainTableId() {
		return "model";
	}

	/**
	 * @param name
	 * @return true if model with given id exists in database
	 * @throws KlabException
	 */
	public boolean hasModel(String name) {

		if (!database.hasTable("model")) {
			return false;
		}

		return database.queryIds("SELECT oid FROM model WHERE name = '" + name + "';").size() > 0;
	}

	@Override
	protected int deleteAllObjectsWithNamespace(String namespaceId, Channel monitor) {
		initialize(monitor);
		int n = 0;
		for (long oid : database
				.queryIds("SELECT oid FROM model where namespaceid = '" + Utils.Escape.forSQL(namespaceId) + "';")) {
			deleteObjectWithId(oid, monitor);
			n++;
		}
		return n;
	}

	@Override
	protected void deleteObjectWithId(long id, Channel monitor) {
		initialize(monitor);
		database.execute("DELETE FROM model WHERE oid = " + id);
		deleteMetadataFor(id);
	}

	@Override
	public long store(Object o, Scope monitor) {

		initialize(monitor);

		// if (o instanceof KimNamespace && ((KimNamespace) o).isInternal()) {
		// return 0;
		// }

		ArrayList<Object> toStore = new ArrayList<>();

		if (o instanceof KimModel) {

			resourceService.scope().debug("storing model " + ((KimModel) o).getName());

			for (ModelReference data : inferModels((KimModel) o, monitor)) {
				toStore.add(data);
			}

		} else {
			toStore.add(o);
		}

		long ret = -1;
		for (Object obj : toStore) {
			long r = super.store(obj, monitor);
			if (ret < 0)
				ret = r;
		}

		return ret;
	}

	public static final String DUMMY_NAMESPACE_ID = "DUMMY_SEARCH_NS";

	/**
	 * Return a collection of model beans that contains all the models implied by a
	 * model statement (and the model itself, when appropriate).
	 * 
	 * @param model
	 * @param monitor
	 * @return the models implied by the statement
	 */
	public Collection<ModelReference> inferModels(KimModel model, Scope monitor) {

		List<ModelReference> ret = new ArrayList<>();

		// happens in error
		if (model.getObservables().size() == 0 || model.getObservables().get(0) == null) {
			return ret;
		}

		Observable mainObservable = reasoner.declareObservable(model.getObservables().get(0));

		for (ModelReference m : getModelDescriptors(model, monitor)) {
			ret.add(m);
		}

		if (ret.size() > 0) {

			for (KimObservable attr : model.getAttributeObservables()) {

				Observable observable = reasoner.declareObservable(attr);

				if (attr == null) {
					// only in error
					continue;
				}

				/*
				 * attribute type must have inherent type added if it's an instantiated quality
				 * (from an instantiator or as a secondary observable of a resolver with
				 * explicit, specialized inherency)
				 */
				Concept type = observable.getSemantics();
				if (model.isInstantiator()) {
					Concept context = reasoner.context(type);
					if (context == null
							|| !scope.getService(Reasoner.class).subsumes(context, mainObservable.getSemantics())) {
						type = observable.builder(monitor).of(mainObservable.getSemantics()).buildConcept();
					}
				}
				ModelReference m = ret.get(0).copy();
				m.setObservable(type.getUrn());
				m.setObservableConcept(type);
				m.setObservationType(observable.getDescriptionType().name());
				m.setDereifyingAttribute(attr.getFormalName());
				m.setMediation(Mediation.DEREIFY_QUALITY);
				m.setPrimaryObservable(!model.isInstantiator());
				ret.add(m);
			}

			if (model.isInstantiator()) {
				// TODO add presence model for main observable type and
				// dereifying models for all mandatory attributes of observable in context
			}
		}

		return ret;
	}

	private Collection<ModelReference> getModelDescriptors(KimModel model, Scope monitor) {

		List<ModelReference> ret = new ArrayList<>();
		Coverage scale = resourceService.modelGeometry(model.getName());

		Shape spaceExtent = null;
		Time timeExtent = null;
		long spaceMultiplicity = -1;
		long timeMultiplicity = -1;
		long scaleMultiplicity = 1;
		long timeStart = -1;
		long timeEnd = -1;
		boolean isSpatial = false;
		boolean isTemporal = false;
		String enumeratedSpaceDomain = null;
		String enumeratedSpaceLocation = null;
		Project project = resourceService.resolveProject(model.getProjectName(), scope);
		KimNamespace namespace = resourceService.resolveNamespace(model.getNamespace(), scope);

		if (scale != null) {

			scaleMultiplicity = scale.size();

			/*
			 * If the runtime allows, resolve any enumeration to physical extents
			 */
			Space space = resolveEnumeratedExtensions(scale.getSpace());
			Time time = resolveEnumeratedExtensions(scale.getTime());

			if (space /* still */ instanceof EnumeratedExtension) {
				/*
				 * TODO handle the enumerated extension
				 */
				throw new KlabUnimplementedException("enumerated extension");
				// Pair<String, String> defs = ((EnumeratedExtension)
				// scale.getSpace()).getExtension();
				// enumeratedSpaceDomain = defs.getFirst();
				// enumeratedSpaceLocation = defs.getSecond();
			} else if (space != null) {
				spaceExtent = space.getGeometricShape();
				// may be null when we just say 'over space'.
				if (spaceExtent != null) {
					spaceExtent = spaceExtent.transform(Projection.getLatLon());
					spaceMultiplicity = space.size();
				}
				isSpatial = true;
			}

			if (time != null) {
				if (time /* still */ instanceof EnumeratedExtension) {
					// TODO
					throw new KlabUnimplementedException("enumerated extension");
				} else {
					timeExtent = time.collapsed();
					if (timeExtent != null) {
						if (timeExtent.getStart() != null) {
							timeStart = timeExtent.getStart().getMilliseconds();
						}
						if (timeExtent.getEnd() != null) {
							timeEnd = timeExtent.getEnd().getMilliseconds();
						}
					}
				}
				timeMultiplicity = time.size();
				isTemporal = true;
			}
		}

		boolean first = true;
		Observable main = null;
		for (KimObservable kobs : model.getObservables()) {

			Observable oobs = reasoner.declareObservable(kobs);

			if (first) {
				main = oobs;
			}

			for (Observable obs : unpackObservables(oobs, main, first, monitor)) {

				ModelReference m = new ModelReference();

				m.setName(model.getName());
				m.setNamespaceId(model.getNamespace());
				// if (model.getNamespace().getProject() != null) {
				m.setProjectId(model.getProjectName());
				// if (model.getNamespace().getProject().isRemote()) {
				// m.setServerId(model.getNamespace().getProject().getOriginatingNodeId());
				// }
				// }

				if (project != null) {
					m.setPermissions(project.getManifest().getPrivileges());
				}

				m.setTimeEnd(timeEnd);
				m.setTimeStart(timeStart);
				m.setTimeMultiplicity(timeMultiplicity);
				m.setSpaceMultiplicity(spaceMultiplicity);
				m.setScaleMultiplicity(scaleMultiplicity);
				m.setSpatial(isSpatial);
				m.setTemporal(isTemporal);
				m.setShape(spaceExtent);
				m.setEnumeratedSpaceDomain(enumeratedSpaceDomain);
				m.setEnumeratedSpaceLocation(enumeratedSpaceLocation);

				m.setObservable(obs.getUrn());
				m.setObservationType(obs.getDescriptionType().name());
				m.setObservableConcept(obs.getSemantics());
				// m.setObservationConcept(obs.getObservationType());

				m.setScope(model.getScope());
				m.setInScenario(namespace.isScenario());
				m.setReification(model.isInstantiator());
				m.setResolved(model.getDependencies().size() == 0);
				m.setHasDirectData(m.isResolved() && model.getObservables().get(0).getMain().is(SemanticType.QUALITY));
				m.setHasDirectObjects(
						m.isResolved() && model.getObservables().get(0).getMain().is(SemanticType.DIRECT_OBSERVABLE));

				m.setMinSpatialScaleFactor(
						model.getMetadata().get(Metadata.IM_MIN_SPATIAL_SCALE, Space.MIN_SCALE_RANK));
				m.setMaxSpatialScaleFactor(
						model.getMetadata().get(Metadata.IM_MAX_SPATIAL_SCALE, Space.MAX_SCALE_RANK));
				m.setMinTimeScaleFactor(model.getMetadata().get(Metadata.IM_MIN_TEMPORAL_SCALE, Time.MIN_SCALE_RANK));
				m.setMaxTimeScaleFactor(model.getMetadata().get(Metadata.IM_MAX_TEMPORAL_SCALE, Time.MAX_SCALE_RANK));

				m.setPrimaryObservable(first);

//                if (first && obs.isSpecialized()) {
//                    m.setSpecializedObservable(true);
//                }

				first = false;

				m.setMetadata(translateMetadata(model.getMetadata()));

				ret.add(m);

			}

			/*
			 * For now just disable additional observables in instantiators and use their
			 * attribute observers upstream. We may do different things here:
			 * 
			 * 0. keep ignoring them 1. keep them all, contextualized to the instantiated
			 * observable; 2. keep only the non-statically contextualized ones (w/o the
			 * value)
			 * 
			 */
			if (model.isInstantiator()) {
				break;
			}

		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	private <T extends Extent<T>> T resolveEnumeratedExtensions(T extent) {
		if (extent instanceof EnumeratedExtension) {
			return (T) ((EnumeratedExtension<?>) extent).getPhysicalExtent();
		}
		return extent;
	}

	private List<Observable> unpackObservables(Observable oobs, Observable main, boolean first, Scope monitor) {

		List<Observable> ret = new ArrayList<>();
		if (!first) {
			/**
			 * Subsequent observables inherit any explicit specialization in the main
			 * observable of a model
			 */
			Concept specialized = reasoner.directContext(main.getSemantics());
			Concept oobsContext = reasoner.context(oobs);
			if (specialized != null
					&& (oobsContext == null || !scope.getService(Reasoner.class).subsumes(oobsContext, specialized))) {
				oobs = oobs.builder(monitor).within(specialized).buildObservable();
			}
		}
		ret.add(oobs);
		return ret;
	}

	private static Map<String, String> translateMetadata(Metadata metadata) {
		Map<String, String> ret = new HashMap<>();
		for (String key : metadata.keySet()) {
			ret.put(key, metadata.get(key) == null ? "null" : metadata.get(key).toString());
		}
		return ret;
	}

	public ModelReference retrieveModel(String string, Channel monitor) {
		return retrieve("SELECT * FROM model WHERE name = '" + string + "'", monitor);
	}

}
