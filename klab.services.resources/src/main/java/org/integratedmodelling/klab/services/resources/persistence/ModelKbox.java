package org.integratedmodelling.klab.services.resources.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.h2gis.utilities.SpatialResultSet;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
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
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.persistence.h2.SQL;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.locationtech.jts.geom.Geometry;

public class ModelKbox extends ObservableKbox implements ModelCatalog {

  // private boolean workRemotely = !Configuration.INSTANCE.isOffline();
  private boolean initialized = false;

  /**
   * Create a kbox with the passed name. If the kbox exists, open it and return it.
   *
   * @param service
   * @return a new kbox
   */
  public static ModelKbox create(ResourcesService service) {
    return new ModelKbox(service);
  }

  private ModelKbox(ResourcesService service) {
    super(service);
    this.resourceService = service;
  }

  @Override
  protected synchronized void initialize(Channel monitor) {

    if (!initialized) {

      setSchema(
          ModelReference.class,
          new Schema() {

            @Override
            public String getTableName() {
              return getMainTableId();
            }

            @Override
            public String getCreateSQL() {
              String ret =
                  "CREATE TABLE model ("
                      + "oid LONG, "
                      + "serverid VARCHAR(64), "
                      + "id "
                      + "VARCHAR(256), "
                      + "name VARCHAR(256), "
                      + "namespaceid VARCHAR(128), "
                      + "projectid VARCHAR"
                      + "(128), "
                      + "typeid LONG, "
                      + "otypeid LONG, "
                      + "scope VARCHAR(16), "
                      + "isresolved BOOLEAN, "
                      + "isreification "
                      + "BOOLEAN, "
                      + "inscenario BOOLEAN, "
                      + "hasdirectobjects BOOLEAN, "
                      + "hasdirectdata "
                      + "BOOLEAN, "
                      + "timestart LONG, "
                      + "timeend LONG, "
                      + "isspatial BOOLEAN, "
                      + "istemporal "
                      + "BOOLEAN, "
                      + "timemultiplicity LONG, "
                      + "spacemultiplicity LONG, "
                      + "scalemultiplicity "
                      + "LONG, "
                      + "dereifyingattribute VARCHAR(256), "
                      + "minspatialscale INTEGER, "
                      + "maxspatialscale "
                      + "INTEGER, "
                      + "mintimescale INTEGER, "
                      + "maxtimescale INTEGER, "
                      + "space GEOMETRY, "
                      + "observationtype VARCHAR(256), "
                      + "enumeratedspacedomain VARCHAR(256), "
                      + "enumeratedspacelocation VARCHAR(1024), "
                      + "specializedObservable BOOLEAN, "
                      + "timestamp LONG"
                      + "); "
                      + "CREATE INDEX model_oid_index ON model(oid); "
                  // + "CREATE SPATIAL INDEX model_space ON model(space);"
                  ;

              return ret;
            }
          });

      setSerializer(
          ModelReference.class,
          new Serializer<ModelReference>() {

            private String cn(Object o) {
              return o == null ? "" : Utils.Escape.forSQL(o.toString());
            }

            @Override
            public String serialize(ModelReference model, long primaryKey, long foreignKey) {

              long tid = requireConceptId(model.getObservableConcept(), monitor);

              String ret =
                  "INSERT INTO model VALUES ("
                      + primaryKey
                      + ", "
                      + "'"
                      + cn(model.getServerId())
                      + "', "
                      + "'"
                      + cn(model.getName())
                      + "', "
                      + "'"
                      + cn(model.getName())
                      + "', "
                      + "'"
                      + cn(model.getNamespaceId())
                      + "', "
                      + "'"
                      + cn(model.getProjectId())
                      + "', "
                      + tid
                      + ", "
                      + /* observation concept is obsolete oid
                         */ 0
                      + ", '"
                      + (model.getScope().name())
                      + "', "
                      + (model.isResolved() ? "TRUE" : "FALSE")
                      + ", "
                      + (model.isReification() ? "TRUE" : "FALSE")
                      + ", "
                      + (model.isInScenario() ? "TRUE" : "FALSE")
                      + ", "
                      + (model.isHasDirectObjects() ? "TRUE" : "FALSE")
                      + ", "
                      + (model.isHasDirectData() ? "TRUE" : "FALSE")
                      + ", "
                      + model.getTimeStart()
                      + ", "
                      + model.getTimeEnd()
                      + ", "
                      + (model.isSpatial() ? "TRUE" : "FALSE")
                      + ", "
                      + (model.isTemporal() ? "TRUE" : "FALSE")
                      + ", "
                      + model.getTimeMultiplicity()
                      + ", "
                      + model.getSpaceMultiplicity()
                      + ", "
                      + model.getScaleMultiplicity()
                      + ", "
                      + "'"
                      + cn(model.getDereifyingAttribute())
                      + "', "
                      + model.getMinSpatialScaleFactor()
                      + ", "
                      + model.getMaxSpatialScaleFactor()
                      + ", "
                      + model.getMinTimeScaleFactor()
                      + ", "
                      + model.getMaxTimeScaleFactor()
                      + ", "
                      + "'"
                      + (model.getShape() == null
                          ? "GEOMETRYCOLLECTION EMPTY"
                          : ShapeImpl.promote(model.getShape())
                              .getStandardizedGeometry()
                              .toString())
                      + "', '"
                      + cn(model.getObservationType())
                      + "', '"
                      + cn(model.getEnumeratedSpaceDomain())
                      + "', '"
                      + cn(model.getEnumeratedSpaceLocation())
                      + "', "
                      + (model.isSpecializedObservable() ? "TRUE" : "FALSE")
                      + ", "
                      + model.getTimestamp()
                      + ");";

              if (model.getMetadata() != null && model.getMetadata().size() > 0) {
                var metadata = new HashMap<>(model.getMetadata());
                metadata.remove("$klab:descriptor:v1");
                storeMetadataFor(primaryKey, metadata);
              }

              storeMetadataFor(primaryKey, Map.of("$klab:descriptor:v1", ModelReferenceCodec.encode(model)));
              return ret;
            }
          });
      initialized = true;
    }
  }

  /**
   * Pass the output of queryModelData to a contextual prioritizer and return the ranked list of
   * IModels. If we're a personal engine, also broadcast the query to the network and merge results
   * before returning.
   *
   * @param observable
   * @param scope
   * @return models resulting from query, best first.
   */
  public Collection<ModelReference> query(
      Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry,
      Concept contextObservable,
      List<ResolutionConstraint> resolutionConstraints,
      ContextScope scope) {

    initialize(scope);

    List<ModelReference> local = new ArrayList<>();
    /*
     * only query locally if we've seen a model before.
     */
    if (database.hasTable("model")) {
      try {
        for (ModelReference md :
            queryModels(observable, geometry, contextObservable, resolutionConstraints, scope)) {
          if (ModelVisibility.authorized(md, scope)) {
            local.add(md);
          }
        }
      } catch (RuntimeException failure) {
        var error = new KlabStorageException("Model discovery failed for " + observable.getUrn());
        error.initCause(failure);
        throw error;
      }
    }
    return local;
  }

  /**
   * Find and deserialize all modeldata matching the parameters. Do not rank or anything.
   *
   * @param observable
   * @param geometry
   * @param contextObservable
   * @param resolutionConstraints
   * @return all unranked model descriptors matching the query
   */
  public List<ModelReference> queryModels(
      Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry,
      Concept contextObservable,
      List<ResolutionConstraint> resolutionConstraints,
      ContextScope context) {

    List<ModelReference> ret = new ArrayList<>();

    if (!database.hasTable("model")) {
      return ret;
    }

    //    var geometry = ContextScope.getResolutionGeometry(context);
    if (geometry == null || geometry.isEmpty()) {
      return ret;
    }

    var scale = GeometryRepository.INSTANCE.scale(geometry);
    String query = "SELECT model.oid FROM model WHERE ";
    //    Concept contextObservable =
    //        context.getContextObservation() == null
    //            ? null
    //            : context.getContextObservation().getObservable().getSemantics();

    String typeQuery = observableQuery(observable, contextObservable);
    if (typeQuery == null) {
      return ret;
    }

    query += "(" + scopeQuery(resolutionConstraints, observable) + ")";
    query += " AND (" + typeQuery + ")";
    if (scale.getSpace() != null) {
      String sq = spaceQuery(scale.getSpace());
      if (!sq.isEmpty()) {
        query += " AND (" + sq + ")";
      }
    }

    String tQuery = timeQuery(scale.getTime());
    if (!tQuery.isEmpty()) {
      query += " AND (" + tQuery + ");";
    }

    final List<Long> oids = database.queryIds(query);
    for (long l : oids) {
      ModelReference model = retrieveModel(l, context);
      if (model != null) {
        Coverage coverage =
            resourceService.info(
                model.getName(), KlabAsset.KnowledgeClass.MODEL, Coverage.class, context);
        if (coverage != null && !coverage.checkConstraints(scale)) {
          resourceService
              .serviceScope()
              .debug(
                  "model "
                      + model.getName()
                      + " of "
                      + observable
                      + " discarded because of coverage constraints mismatch");
          continue;
        }
        ret.add(model);
      }
    }

    resourceService
        .serviceScope()
        .info(
            "model query for "
                + observable.getContextualization().name().toLowerCase()
                + " of "
                + observable
                + " found "
                + (ret.size() == 1 ? ret.getFirst().getName() : (ret.size() + " models")));

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
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    StringBuilder ret = new StringBuilder();
    for (long id : ids) {
      ret.append(ret.isEmpty() ? "" : ", ").append(id);
    }
    return "typeid IN (" + ret + ")";
  }

  public <T> T getConstraint(
      List<ResolutionConstraint> resolutionConstraints,
      ResolutionConstraint.Type type,
      Class<T> resultClass) {
    var values = getConstraints(resolutionConstraints, type, resultClass);
    return values.isEmpty() ? null : values.getFirst();
  }

  public <T> List<T> getConstraints(List<ResolutionConstraint> constraints,
      ResolutionConstraint.Type type, Class<T> resultClass) {
    return ModelVisibility.constraints(constraints, type, resultClass);
  }

  /*
   * select models that are [instantiators if required] AND:] [private and in the home namespace
   * if not dummy OR] [project private and in the home project if not dummy OR] (non-private and
   * non-scenario) OR (in any of the scenarios in the context).
   */
  private String scopeQuery(
      List<ResolutionConstraint> resolutionConstraints, Observable observable) {

    String namespace = getConstraint(resolutionConstraints,
        ResolutionConstraint.Type.ResolutionNamespace, String.class);
    String project = getConstraint(resolutionConstraints,
        ResolutionConstraint.Type.ResolutionProject, String.class);
    String visibility = "(model.scope = 'PUBLIC' AND NOT model.inscenario)";
    if (namespace != null) visibility += " OR model.namespaceid = '" + Utils.Escape.forSQL(namespace) + "'";
    if (project != null) visibility += " OR (model.scope = 'PROJECT_PRIVATE' AND NOT model.inscenario AND model.projectid = '"
        + Utils.Escape.forSQL(project) + "')";
    var scenarios = getConstraints(resolutionConstraints, ResolutionConstraint.Type.Scenarios, String.class);
    if (!scenarios.isEmpty()) visibility += " OR (" + joinStringConditions("model.namespaceid", scenarios, "OR") + ")";
    String ret = "(" + visibility + ")";
    // Project-private models never escape their project, including explicit scenarios.
    ret += project == null ? " AND model.scope <> 'PROJECT_PRIVATE'"
        : " AND (model.scope <> 'PROJECT_PRIVATE' OR model.projectid = '" + Utils.Escape.forSQL(project) + "')";
    if (observable.is(SemanticType.COUNTABLE)) {
      ret += observable.getContextualization().isCollective()
          ? " AND model.isreification" : " AND NOT model.isreification";
    }
    return ret;
  }

  /*
   * select models that intersect the given space or have no space at all. TODO must match
   * geometry when forced - if it has @intensive(space, time) it shouldn't match no space/time OR
   * non-distributed space/time. ALSO the dimensionality!
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

    if (space == null || space.getGeometricShape() == null || space.getGeometricShape().isEmpty()) {
      return "";
    }

    String scalequery =
        space.getRank() + " BETWEEN model.minspatialscale AND model.maxspatialscale";

    String spacequery =
        "model.space && '"
            + ShapeImpl.promote(space.getGeometricShape()).getStandardizedGeometry()
            + "' OR ST_IsEmpty(model.space)";

    return "(" + scalequery + ") AND (" + spacequery + ")";
  }

  /*
   * Entirely TODO. For initialization we should use time only to select for most current info -
   * either closer to the context or to today if time is null. For dynamic models we should either
   * not have a context or cover the context. Guess this is the job of the prioritizer, and we
   * should simply let anything through except when we look for T1(n>1) models.
   *
   * TODO must match geometry when forced - if it has @intensive(space, time) it shouldn't match
   * no space/time OR non-distributed space/time. ALSO the dimensionality!
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
    final boolean[] found = {false};

    database.query(
        query,
        new SQL.SimpleResultHandler() {
          @Override
          public void onRow(ResultSet rs) {

            try {

              SpatialResultSet srs = rs.unwrap(SpatialResultSet.class);

              found[0] = true;
              long tyid = srs.getLong(7);

              ret.setName(srs.getString(4));

              var observable = getType(tyid);
              Concept mtype = observable == null ? null : observable.asConcept();

              ret.setObservableConcept(mtype);
              ret.setObservable(getTypeDefinition(tyid));

              ret.setServerId(nullify(srs.getString(2)));
              // ret.setId(srs.getString(3));

              ret.setNamespaceId(srs.getString(5));
              ret.setProjectId(nullify(srs.getString(6)));

              ret.setScope(KlabStatement.Scope.valueOf(srs.getString(9)));
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
              ret.setTimestamp(srs.getLong(32));
              ret.setObservationType(srs.getString(28));
              ret.setEnumeratedSpaceDomain(nullify(srs.getString(29)));
              ret.setEnumeratedSpaceLocation(nullify(srs.getString(30)));
              ret.setSpecializedObservable(srs.getBoolean(31));
              if (geometry != null && !geometry.isEmpty()) {
                ret.setShape(Shape.create(geometry.toText(), Projection.getLatLon())); // +
              }
            } catch (SQLException e) {
              throw new KlabStorageException(e);
            }
          }
        });

    return found[0] ? ret : null;
  }

  public ModelReference retrieveModel(long oid, Channel monitor) throws KlabException {

    ModelReference ret = retrieve("SELECT * FROM model WHERE oid = " + oid, monitor);
    if (ret != null) {
      var metadata = getMetadataFor(oid);
      if (metadata != null && metadata.containsKey("$klab:descriptor:v1"))
        return ModelReferenceCodec.decode(metadata.get("$klab:descriptor:v1"), scope.getService(Reasoner.class));
      ret.setMetadata(metadata);
      // Legacy rows did not persist permissions. Resolve them from the owning project rather
      // than trusting the bean's PUBLIC default when the project cannot be retrieved.
      if (ret.getProjectId() != null) {
        Project project = null;
        if (resourceService instanceof org.integratedmodelling.klab.services.resources.ResourcesProvider provider)
          project = provider.retrieveProject(ret.getProjectId(), scope);
        else if (monitor instanceof org.integratedmodelling.klab.api.scope.UserScope user)
          project = resourceService.retrieve(ret.getProjectId(), Project.class, user);
        ret.setPermissions(project == null
            ? org.integratedmodelling.klab.api.authentication.ResourcePrivileges.empty()
            : project.getManifest().getPrivileges());
      }
    }
    return ret;
  }

  @Override
  protected String getMainTableId() {
    return "model";
  }

  /**
   * @param name
   * @return true if model with given id exists in database
   */
  public boolean hasModel(String name) {

    if (!database.hasTable("model")) {
      return false;
    }

    return database.queryIds("SELECT oid FROM model WHERE name = '" + Utils.Escape.forSQL(name) + "';").size() > 0;
  }

  @Override
  protected int deleteAllObjectsWithNamespace(String namespaceId, Channel monitor) {
    initialize(monitor);
    int n = 0;
    for (long oid :
        database.queryIds(
            "SELECT oid FROM model where namespaceid = '"
                + Utils.Escape.forSQL(namespaceId)
                + "';")) {
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

    ArrayList<Object> toStore = new ArrayList<>();

    if (o instanceof KimModel) {

      resourceService.serviceScope().debug("storing model " + ((KimModel) o).getUrn());

      for (ModelReference data : inferModels((KimModel) o, monitor)) {
        toStore.add(data);
      }

    } else {
      toStore.add(o);
    }

    long ret = -1;
    for (Object obj : toStore) {
      long r = super.store(obj, monitor);
      if (ret < 0) ret = r;
    }

    return ret;
  }

  public static final String DUMMY_NAMESPACE_ID = "DUMMY_SEARCH_NS";

  /**
   * Return a collection of model beans that contains all the models implied by a model statement
   * (and the model itself, when appropriate).
   *
   * @param model
   * @param monitor
   * @return the models implied by the statement
   */
  public Collection<ModelReference> inferModels(KimModel model, Scope monitor) {
    return new ModelDescriptorFactory(resourceService).inferModels(model, monitor);
  }

  private <T extends Extent<T>> T resolveEnumeratedExtensions(T extent) {
    return extent instanceof EnumeratedExtension<?> enumeration
        ? (T) enumeration.getPhysicalExtent() : extent;
  }

  @Override
  public void close() {
    database.deallocateConnection();
  }

  public ModelReference retrieveModel(String string, Channel monitor) {
    initialize(monitor);
    if (!database.hasTable("model")) return null;
    var ids = database.queryIds("SELECT oid FROM model WHERE name = '" + Utils.Escape.forSQL(string) + "' ORDER BY oid");
    return ids.isEmpty() ? null : retrieveModel(ids.getFirst(), monitor);
  }
}
