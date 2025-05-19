package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

/**
 * Visit the hierarchy to collect the most specific among the fillers of the object restrictions on
 * the passed property. Recurse the asserted hierarchy when looking for parents. If the filler is a
 * union, use the flattened set of concepts.
 *
 * @author ferdinando.villa
 */
public class SpecializingRestrictionVisitor extends OWLClassExpressionVisitorAdapter {

  private Set<OWLOntology> onts;
  private Set<OWLClass> processedClasses = new HashSet<>();
  private Property property;
  private Collection<Concept> result = null;
  //    private Concept             concept;
  private boolean useSuperproperties = false;
  private OWLQuantifiedRestriction<?, ?, ? extends OWLClassExpression> restriction;
  private OWL owl;

  public Collection<Concept> getResult() {
    return result == null ? new HashSet<>() : result;
  }

  public OWLQuantifiedRestriction<?, ?, ?> getRestriction() {
    return this.restriction;
  }

  public SpecializingRestrictionVisitor(
      Concept concept, Property property, boolean useSuperProperties, OWL owl) {
    this.onts = owl.manager.getOntologies();
    this.property = property;
    this.owl = owl;
    //        this.concept = concept;
    this.useSuperproperties = useSuperProperties;
    owl.getOWLClass(concept).accept(this);
  }

  @Override
  public void visit(OWLClass desc) {

    if (!processedClasses.contains(desc)) {

      processedClasses.add(desc);

      Set<OWLClassExpression> set = desc.getSuperClasses(onts);
      for (OWLClassExpression s : set) {
        if (s.equals(desc)) {
          break;
        } else {
          s.accept(this);
        }
      }
    }
  }

  private void visitRestriction(OWLQuantifiedRestriction<?, ?, ? extends OWLClassExpression> desc) {
    if (useSuperproperties) {
      if (owl.getPropertyFor((OWLProperty<?, ?>) desc.getProperty()).is(property, owl)) {
        if (addNew(owl.unwrap(desc.getFiller()))) {
          // keep it for inspection at the end
          this.restriction = desc;
        }
      }
    } else {
      if (owl.getPropertyFor((OWLProperty<?, ?>) desc.getProperty()).equals(property)) {
        if (addNew(owl.unwrap(desc.getFiller()))) {
          // keep the restriction
          this.restriction = desc;
        }
      }
    }
  }

  /*
   * return whether the restriction was used.
   */
  private boolean addNew(Collection<Concept> collection) {

    if (result == null) {
      result = collection;
      return !collection.isEmpty();
    }

    /*
     * only add those that are not already present in a more specialized class.
     */
    Set<Concept> keep = new HashSet<>();
    Set<Concept> remove = new HashSet<>();
    for (Concept toadd : collection) {
      boolean ok = true;
      for (Concept c : result) {
        if (!c.equals(toadd) && owl.reasoner().is(c, toadd)) {
          ok = false;
          break;
        }
        if (!toadd.equals(c) && owl.reasoner().is(toadd, c)) {
          remove.add(c);
        }
      }
      if (ok) {
        keep.add(toadd);
      }
      if (remove.size() > 0) {
        result.removeAll(remove);
      }
    }
    result.addAll(keep);
    return keep.size() > 0;
  }

  @Override
  public void visit(OWLObjectAllValuesFrom desc) {
    visitRestriction(desc);
  }

  @Override
  public void visit(OWLObjectExactCardinality desc) {
    visitRestriction(desc);
  }

  @Override
  public void visit(OWLObjectMaxCardinality desc) {
    visitRestriction(desc);
  }

  @Override
  public void visit(OWLObjectMinCardinality desc) {
    visitRestriction(desc);
  }

  public void visit(OWLObjectCardinalityRestriction desc) {
    visitRestriction(desc);
  }

  @Override
  public void visit(OWLObjectSomeValuesFrom desc) {
    visitRestriction(desc);
  }
}
