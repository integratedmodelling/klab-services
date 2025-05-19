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
package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.HashMap;

import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * Implements metadata for ontologies, concepts and properties by providing an interface to
 * annotation properties of an OWL entity and exposing them with their fully qualified property IDs
 * as keys.
 *
 * @author Ferd
 */
public class OWLMetadata extends MetadataImpl implements Metadata {

  private static final long serialVersionUID = -2172262717519576273L;

  public static HashMap<String, String> metadataVocabulary = new HashMap<>();

  static {
    metadataVocabulary.put(OWLRDFVocabulary.RDFS_LABEL.getIRI().toString(), Vocabulary.RDFS_LABEL);
    metadataVocabulary.put(
        OWLRDFVocabulary.RDFS_COMMENT.getIRI().toString(), Vocabulary.RDFS_COMMENT);
    metadataVocabulary.put(
        "http://protege.stanford.edu/plugins/owl/dc/protege-dc.owl#label", Vocabulary.RDFS_LABEL);
    metadataVocabulary.put(
        "http://protege.stanford.edu/plugins/owl/dc/protege-dc.owl#comment",
        Vocabulary.RDFS_COMMENT);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#baseDeclaration",
        CoreOntology.NS.BASE_DECLARATION);
    metadataVocabulary.put("http://integratedmodelling.org/odo#isAbstract", NS.IS_ABSTRACT);
    metadataVocabulary.put("http://integratedmodelling.org/odo#unit", NS.SI_UNIT_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/odo#isDeniable", NS.DENIABILITY_PROPERTY);
    metadataVocabulary.put("http://integratedmodelling.org/ks/odo#orderingRank", NS.ORDER_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#conceptDefinition",
        NS.CONCEPT_DEFINITION_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#coreObservable", NS.CORE_OBSERVABLE_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#displayLabel", NS.DISPLAY_LABEL_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#originalTrait", NS.ORIGINAL_TRAIT);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#localAlias", NS.LOCAL_ALIAS_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#referenceName", NS.REFERENCE_NAME_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#authorityId", NS.AUTHORITY_ID_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#restrictingProperty",
        NS.TRAIT_RESTRICTING_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/odo#isTypeDelegate", NS.IS_TYPE_DELEGATE);
    metadataVocabulary.put(
        "http://integratedmodelling.org/ks/klab.owl#untransformedConceptId",
        NS.UNTRANSFORMED_CONCEPT_PROPERTY);
    metadataVocabulary.put(
        "http://integratedmodelling.org/odo#isSubjectiveTrait", NS.IS_SUBJECTIVE);
  }

  public OWLMetadata(OWLEntity owl, OWLOntology ontology) {
    for (OWLAnnotation zio : owl.getAnnotations(ontology)) {
      OWLAnnotationValue val = zio.getValue();
      String piri = zio.getProperty().getIRI().toString();
      String prop = metadataVocabulary.get(piri);
      if (prop != null) {
        this.put(prop, literal2obj(val));
      }
    }
  }

  public static String translate(String iri) {
    return metadataVocabulary.containsKey(iri)
        ? metadataVocabulary.get(iri)
        : Utils.Strings.getFragment(iri);
  }

  /*
   * mah
   */
  public static Object literal2obj(OWLAnnotationValue l) {

    if (l instanceof OWLLiteral ol) {
      if (ol.isBoolean()) return ol.parseBoolean();
      else if (ol.isInteger()) return ol.parseInteger();
      else if (ol.isFloat()) return ol.parseFloat();
      else if (ol.isDouble()) return ol.parseDouble();
      else {
        return ol.getLiteral();
      }
    } else if (l instanceof IRI iri) {
      return iri.toURI().toString();
    }
    return l.toString();
  }
}
