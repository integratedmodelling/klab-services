/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata are a glorified parameter map with all the expected constants and some additional
 * methods. They are shared by all k.LAB syntactic objects and their implementations.
 *
 * <p>Serializes correctly only if with a Jackson object mapper instrumented with specialized
 * serializers.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Metadata extends Parameters<String> {

  /** The dc name. */
  String DC_NAME = "dc:name";

  /** This is not in Dublin Core but is used extensively in legacy content, unfortunately */
  String DC_LABEL = "dc:label";

  /** This is not in Dublin Core but is used extensively in legacy content, unfortunately */
  String DC_COMMENT = "dc:comment";

  /** Use the RDFS namespace for labels and comments so that we can directly interact with OWL2. */
  String RDFS_COMMENT = "rdfs:comment";

  /** Use the RDFS namespace for labels and comments so that we can directly interact with OWL2. */
  String RDFS_LABEL = "rdfs:label";

  /** The dc definition. */
  String DC_DEFINITION = "dc:definition";

  /** The dc seealso. */
  String DC_SEEALSO = "dc:name";

  /**
   * DCMI point http://dublincore.org/documents/dcmi-point/ ISO 3166
   * http://www.din.de/gremien/nas/nabd/iso3166ma/codlstp1/index.html DCMI box
   * http://dublincore.org/documents/dcmi-box/ TGN http://shiva.pub.getty.edu/tgn_browser/
   */
  String DC_COVERAGE_SPATIAL = "dc:coverage-spatial";

  /**
   * DCMI period http://dublincore.org/documents/dcmi-period/ W3C-DTF
   * http://www.w3.org/TR/NOTE-datetime
   */
  String DC_COVERAGE_TEMPORAL = "dc:coverage-temporal";

  /** free text. */
  String DC_DESCRIPTION = "dc:description";

  /** free text. */
  String DC_DESCRIPTION_TABLEOFCONTENTS = "dc:tableofcontents";

  /** free text. */
  String DC_DESCRIPTION_ABSTRACT = "dc:abstract";

  /** DCMI type vocabulary http://dublincore.org/documents/dcmi-type-vocabulary/ */
  String DC_TYPE = "dc:type";

  /**
   * The dc relation.
   *
   * @deprecated use subclasses
   */
  @Deprecated String DC_RELATION = "dc:relation";

  /** URI http://www.ietf.org/rfc/rfc2396.txt */
  String DC_RELATION_ISVERSIONOF = "dc:isversionof";

  /** URI. */
  String DC_RELATION_HASVERSION = "dc:hasversion";

  /** URI. */
  String DC_RELATION_ISREPLACEDBY = "dc:isreplacedby";

  /** URI. */
  String DC_RELATION_REPLACES = "dc:replaces";

  /** URI. */
  String DC_RELATION_ISREQUIREDBY = "dc:isrequiredby";

  /** URI. */
  String DC_RELATION_REQUIRES = "dc:requires";

  /** URI. */
  String DC_RELATION_ISPARTOF = "dc:ispartof";

  /** URI. */
  String DC_RELATION_HASPART = "dc:haspart";

  /** URI. */
  String DC_RELATION_ISREFERENCEDBY = "dc:isreferencedby";

  /** URI. */
  String DC_RELATION_REFERENCES = "dc:references";

  /** URI. */
  String DC_RELATION_ISFORMATOF = "dc:isformatof";

  /** URI. */
  String DC_RELATION_HASFORMAT = "dc:hasformat";

  /** URI. */
  String DC_SOURCE = "dc:source";

  /**
   * Vocabularies:
   *
   * <p>LCSH Library of Congress Subject Headings MeSH http://www.nlm.nih.gov/mesh/meshhome.html DDC
   * http://www.oclc.org/dewey/index.htm LCC http://lcweb.loc.gov/catdir/cpso/lcco/lcco.html UDC
   * http://www.udcc.org/
   */
  String DC_SUBJECT = "dc:subject";

  /** The dc title. */
  String DC_TITLE = "dc:title";

  /** The dc title alternative. */
  String DC_TITLE_ALTERNATIVE = "dc:title-alternative";

  /** The dc contributor. */
  String DC_CONTRIBUTOR = "dc:contributor";

  /** The dc url. */
  // TODO is this in DC?
  String DC_URL = "dc:url";

  /** The dc originator. */
  // TODO is this in DC?
  String DC_ORIGINATOR = "dc:originator";

  /** The dc creator. */
  String DC_CREATOR = "dc:creator";

  /** The dc publisher. */
  String DC_PUBLISHER = "dc:publisher";

  /** The dc rights. */
  String DC_RIGHTS = "dc:rights";

  /**
   * DCMI period http://dublincore.org/documents/dcmi-period/ W3C-DTF
   * http://www.w3.org/TR/NOTE-datetime
   */
  String DC_DATE_CREATED = "dc:date-created";

  /** DCMI period W3C-DTF. */
  String DC_DATE_VALID = "dc:date-valid";

  /** DCMI period W3C-DTF. */
  String DC_DATE_AVAILABLE = "dc:date-available";

  /** DCMI period W3C-DTF. */
  String DC_DATE_ISSUED = "dc:date-issued";

  /** DCMI period W3C-DTF. */
  String DC_MODIFIED = "dc:modified";

  /** The dc format extent. */
  String DC_FORMAT_EXTENT = "dc:format-extent";

  /** http://www.isi.edu/in-notes/iana/assignments/media-types/media-types */
  String DC_FORMAT_MEDIUM = "dc:format-medium";

  /** The dc identifier. */
  String DC_IDENTIFIER = "dc:identifier";

  String IM_KEYWORDS = "im:keywords";

  String IM_KEY = "im:key";

  /**
   * Comes with a resolved observation to indicate that the observation is meant to become the
   * current observer.
   */
  String IM_OBSERVER_TAG = "im:observer-tag";

  String IM_THEMATIC_AREA = "im:thematic-area";

  String IM_GEOGRAPHIC_AREA = "im:geographic-area";

  String IM_OBSERVATION_COST = "im:observation-cost";

  String IM_RESOLUTION_GRAPH = "im:resolution-graph";

  String IM_COMMIT_ID = "im:commit-id";

  String IM_COMMIT = "im:commit";

  /** Proportional coverage of the requested geometry in an observation query result. */
  String IM_QUERY_COVERAGE = "im:query-coverage";

  /** Encoded geometry requested in an observation query. */
  String IM_QUERY_GEOMETRY = "im:query-geometry";

  /** IDs of the observations whose geometries contributed to an observation query result. */
  String IM_QUERY_SOURCE_IDS = "im:query-source-ids";

  /** ID of the cohort addressed by a collective observation query. */
  String IM_QUERY_COHORT_ID = "im:query-cohort-id";

  /** For legacy resources */
  String IM_ORIGINAL_URN = "im:original-urn";

  /** For resources and dataflows built from existing observations */
  String IM_ORIGINAL_OBSERVABLE = "im:original-observable";

  /** ISO639-2 http://www.w3.org/TR/NOTE-datetime RFC1766 http://www.ietf.org/rfc/rfc1766.txt */
  String DC_LANGUAGE = "dc:language";

  /** The Constant IM_NAME. */
  String IM_NAME = "im:name";

  /** Scores resulting from fuzzy search */
  String IM_SEARCH_SCORE = "im:score";

  /** The Constant IM_MIN_SPATIAL_SCALE. */
  String IM_MIN_SPATIAL_SCALE = "im:min-spatial-scale";

  /** The Constant IM_MAX_SPATIAL_SCALE. */
  String IM_MAX_SPATIAL_SCALE = "im:max-spatial-scale";

  /** The Constant IM_MIN_TEMPORAL_SCALE. */
  String IM_MIN_TEMPORAL_SCALE = "im:min-temporal-scale";

  /** The Constant IM_MAX_TEMPORAL_SCALE. */
  String IM_MAX_TEMPORAL_SCALE = "im:max-temporal-scale";

  //  /** unique URN to a feature returned by a service */
  //  public static final String IM_FEATURE_URN = "im:feature-urn";

  /**
   * If present in project metadata and it encodes a file:/ URL, the project is local to the
   * requester.
   */
  String RESOURCES_STORAGE_URL = "storage.url";

  /** */
  String IM_NOTES = "im:notes";

  /**
   * Tags concepts that annotate physical states that don't need units because of adopting rescaling
   * traits.
   */
  String IM_IS_RESCALED = "im:is-rescaled";

  /**
   * Permissions in k.LAB are either "*" for public and/or a list of comma-separated groups
   * (uppercase) and/or usernames (lowercase). An empty permission string means "owner only" (and
   * possibly admin, left to implementations). Prefixing either with a ! denies the permission for
   * the user or group (supposedly to narrow a previous more general one: e.g. *,!BADGUYS).
   */
  String IM_PERMISSIONS = "im:permissions";

  // publication data to send along with publish requests
  String IM_SUGGESTED_RESOURCE_ID = "im:suggested-resource-id";
  String IM_SUGGESTED_NAMESPACE_ID = "im:suggested-namespace-id";
  String IM_SUGGESTED_CATALOG_ID = "im:suggested-catalog-id";

  /**
   * Tags those extensive observables that are actually intensive because the observation is of an
   * inherent countable.
   */
  String IM_RESCALES_INHERENT = "im:rescales-inherent";

  /** KLAB-specific, for visualization and display */
  String KLAB_LINE_COLOR = "klab:linecolor";

  /** The Constant KLAB_FILL_COLOR. */
  String KLAB_FILL_COLOR = "klab:fillcolor";

  /** The Constant KLAB_OPACITY. */
  String KLAB_OPACITY = "klab:opacity";

  //  String KLAB_SERVICE_ID = "klab:service.id";
  //  String KLAB_SERVICE_URL = "klab:service.url";
  //  String KLAB_ADAPTER_URNS = "klab:adapter.urns";

  @SuppressWarnings("unchecked")
  public static Metadata create(Object... o) {
    Map<String, Object> inp = new LinkedHashMap<String, Object>();
    if (o != null) {
      for (int i = 0; i < o.length; i++) {
        if (o[i] instanceof Map) {
          inp.putAll((Map) o[i]);
        } else if (o[i] != null) {
          if (!ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
            inp.put(o[i].toString(), o[i + 1]);
          }
          i++;
        }
      }
    }
    return new MetadataImpl(inp);
  }

  /**
   * Get a variable irrespective of case.
   *
   * @param attr
   * @return
   */
  Object getCaseInsensitive(String attr);

  /**
   * Validate according to passed convention.
   *
   * @param convention
   * @return a set of notifications, empty if no issue was found. Use {@link
   *     Utils.Notifications#hasErrors(Collection)} to check for errors.
   */
  Collection<Notification> validate(MetadataConvention convention);
}
