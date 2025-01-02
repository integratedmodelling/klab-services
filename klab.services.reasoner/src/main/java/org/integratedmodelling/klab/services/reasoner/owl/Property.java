/*******************************************************************************
 *  Copyright (C) 2007, 2015:
 *
 *    - Ferdinando Villa <ferdinando.villa@bc3research.org>
 *    - integratedmodelling.org
 *    - any other authors listed in @author annotations
 *
 *    All rights reserved. This file is part of the k.LAB software suite,
 *    meant to enable modular, collaborative, integrated
 *    development of interoperable data and model components. For
 *    details, see http://integratedmodelling.org.
 *
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the Affero General Public License
 *    Version 3 or any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but without any warranty; without even the implied warranty of
 *    merchantability or fitness for a particular purpose.  See the
 *    Affero General Public License for more details.
 *
 *     You should have received a copy of the Affero General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *     The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;

public class Property /* extends Knowledge implements IProperty */ {

  String _id;
  String _cs;
  OWLEntity _owl;

  public Property(OWLEntity p, String cs) {
    _owl = p;
    _id = _owl.getIRI().getFragment();
    _cs = cs;
  }

  //    @Override
  public String getNamespace() {
    return _cs;
  }

  //    @Override
  public String getName() {
    return _id;
  }

  //    @Override
  public boolean is(Object s, OWL owl) {
    /*
     * TODO use reasoner as appropriate
     */
    //        Property p = s instanceof Property ? (Property)s : s.getType();
    return s instanceof Property && (s.equals(this) || getAllParents(owl).contains(s));
  }

  //    @Override
  public String getURI() {
    return _owl.getIRI().toString();
  }

  //    @Override
  public Ontology getOntology(OWL owl) {
    return owl.getOntology(getNamespace());
  }

  //    @Override
  //    public boolean isClassification() {
  //        // FIXME hmmm. Do we still need this?
  //        return false;
  //    }

  //    @Override
  public boolean isLiteralProperty() {
    return _owl.isOWLDataProperty() || _owl.isOWLAnnotationProperty();
  }

  //    @Override
  public boolean isObjectProperty() {
    return _owl.isOWLObjectProperty();
  }

  //    @Override
  public boolean isAnnotation() {
    return _owl.isOWLAnnotationProperty();
  }

  public OWLEntity getOWLEntity() {
    return _owl;
  }

  //    @Override
  public Property getInverseProperty(OWL owl) {
    Property ret = null;

    synchronized (_owl) {
      if (_owl.isOWLObjectProperty()) {

        Set<OWLObjectPropertyExpression> dio =
            _owl.asOWLObjectProperty().getInverses(ontology(owl));

        if (dio.size() > 1)
          throw new KlabValidationException(
              "taking the inverse of property " + this + ", which has multiple inverses");

        if (dio.size() > 0) {
          OWLObjectProperty op = dio.iterator().next().asOWLObjectProperty();
          ret = new Property(op, owl.getConceptSpace(op.getIRI()));
        }
      }
    }
    return ret;
  }

  //    @Override
  public Collection<Concept> getRange(OWL owl) {
    Set<Concept> ret = new HashSet<>();
    synchronized (_owl) {
      if (_owl.isOWLDataProperty()) {

        for (OWLDataRange c : _owl.asOWLDataProperty().getRanges(owl.manager.getOntologies())) {

          if (c.isDatatype()) {
            OWLDatatype type = c.asOWLDatatype();
            Concept tltype = owl.getDatatypeMapping(type.getIRI().toString());
            if (tltype != null) {
              ret.add(tltype);
            }
          }
        }
      } else if (_owl.isOWLObjectProperty()) {
        for (OWLClassExpression c :
            _owl.asOWLObjectProperty().getRanges(owl.manager.getOntologies())) {
          if (!c.isAnonymous()) ret.add(owl.getExistingOrCreate(c.asOWLClass()));
        }
      }
    }
    return ret;
  }

  //    @Override
  public Collection<Concept> getPropertyDomain(OWL owl) {

    Set<Concept> ret = new HashSet<>();
    synchronized (this._owl) {
      if (_owl.isOWLDataProperty()) {
        for (OWLClassExpression c :
            _owl.asOWLDataProperty().getDomains(owl.manager.getOntologies())) {
          ret.add(owl.getExistingOrCreate(c.asOWLClass()));
        }
      } else if (_owl.isOWLObjectProperty()) {
        for (OWLClassExpression c :
            _owl.asOWLObjectProperty().getDomains(owl.manager.getOntologies())) {
          ret.add(owl.getExistingOrCreate(c.asOWLClass()));
        }
      }
    }
    return ret;
  }

  ////    @Override
  //    public boolean isAbstract() {
  //        // TODO Auto-generated method stub
  //        return false;
  //    }

  //    @Override
  public Property getParent(OWL owl) {

    Collection<Property> pars = getParents(owl);

    if (pars.size() > 1)
      throw new KlabValidationException(
          "asking for single parent of multiple-inherited property " + this);

    return pars.size() == 0 ? null : pars.iterator().next();
  }

  public Collection<Property> getParents(OWL owl) {

    Set<Property> ret = new HashSet<Property>();
    Set<OWLOntology> onts = owl.manager.getOntologies();

    /*
     * TODO use reasoner as appropriate
     */
    synchronized (_owl) {
      if (_owl.isOWLDataProperty()) {
        for (OWLOntology o : onts) {
          for (OWLDataPropertyExpression p : _owl.asOWLDataProperty().getSuperProperties(o)) {
            ret.add(
                new Property(
                    p.asOWLDataProperty(), owl.getConceptSpace(p.asOWLDataProperty().getIRI())));
          }
        }
      } else if (_owl.isOWLObjectProperty()) {
        for (OWLOntology o : onts) {
          for (OWLObjectPropertyExpression p : _owl.asOWLObjectProperty().getSuperProperties(o)) {
            ret.add(
                new Property(
                    p.asOWLObjectProperty(),
                    owl.getConceptSpace(p.asOWLObjectProperty().getIRI())));
          }
        }
      }
    }

    return ret;
  }

  //    @Override
  public Collection<Property> getAllParents(OWL owl) {
    Set<Property> ret = new HashSet<>();

    synchronized (_owl) {
      //            if (_manager.reasoner != null) {
      //
      //                // try {
      //                // if (_owl.isOWLObjectProperty()) {
      //                // Set<Set<OWLObjectProperty>> parents =
      //                // KR().getPropertyReasoner()
      //                // .getAncestorProperties(entity
      //                // .asOWLObjectProperty());
      //                // Set<OWLObjectProperty> subClses = OWLReasonerAdapter
      //                // .flattenSetOfSets(parents);
      //                // for (OWLObjectProperty cls : subClses) {
      //                // ret.add(new Property(cls));
      //                // }
      //                // } else if (entity.isOWLDataProperty()) {
      //                // Set<Set<OWLDataProperty>> parents =
      //                // KR().getPropertyReasoner()
      //                // .getAncestorProperties(entity
      //                // .asOWLDataProperty());
      //                // Set<OWLDataProperty> subClses = OWLReasonerAdapter
      //                // .flattenSetOfSets(parents);
      //                // for (OWLDataProperty cls : subClses) {
      //                // ret.add(new Property(cls));
      //                // }
      //                // }
      //                // return ret;
      //                //
      //                // } catch (OWLException e) {
      //                // // just continue to dumb method
      //                // }
      //
      //            } else {

      for (Property c : getParents(owl)) {
        ret.add(c);
        ret.addAll(c.getAllParents(owl));
      }
      //            }
    }
    return ret;
  }

  //    @Override
  public Collection<Property> getChildren(OWL owl) {

    Set<Property> ret = new HashSet<>();

    Set<OWLOntology> onts = owl.manager.getOntologies();

    if (_owl.isOWLDataProperty()) {
      for (OWLOntology o : onts) {
        synchronized (this._owl) {
          for (OWLDataPropertyExpression p : _owl.asOWLDataProperty().getSubProperties(o)) {
            ret.add(
                new Property(
                    p.asOWLDataProperty(), owl.getConceptSpace(p.asOWLDataProperty().getIRI())));
          }
        }
      }
    } else if (_owl.isOWLObjectProperty()) {
      for (OWLOntology o : onts) {
        synchronized (this._owl) {
          for (OWLObjectPropertyExpression p : _owl.asOWLObjectProperty().getSubProperties(o)) {
            ret.add(
                new Property(
                    p.asOWLObjectProperty(),
                    owl.getConceptSpace(p.asOWLObjectProperty().getIRI())));
          }
        }
      }
    }

    return ret;
  }

  //    @Override
  public Collection<Property> getAllChildren(OWL owl) {

    Set<Property> ret = new HashSet<>();
    for (Property c : getChildren(owl)) {

      ret.add(c);
      for (Property p : c.getChildren(owl)) {
        ret.addAll(p.getAllChildren(owl));
      }
    }

    return ret;
  }

  //    @Override
  public boolean isFunctional(OWL owl) {
    return _owl.isOWLDataProperty()
        ? _owl.asOWLDataProperty().isFunctional(ontology(owl))
        : _owl.asOWLObjectProperty().isFunctional(ontology(owl));
  }

  //    @Override
  public OWLMetadata getMetadata(OWL owl) {
    return new OWLMetadata(_owl, ((Ontology) getOntology(owl)).ontology);
  }

  private OWLOntology ontology(OWL owl) {
    return ((Ontology) getOntology(owl)).ontology;
  }

  @Override
  public String toString() {
    return getNamespace() + ":" + _id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    return Objects.equals(toString(), obj.toString());
  }
}
