package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLClassExpressionVisitor;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
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
    OWLClass owlClass = owl.getOWLClass(concept);
    if (owlClass != null) {
      owlClass.accept(this);
    }
  }

  @Override
  public void visit(OWLClass desc) {

    if (processedClasses.add(desc)) {
      visitClassExpressions(desc.getSuperClasses(onts), desc);
      visitClassExpressions(desc.getEquivalentClasses(onts), desc);
    }
  }

  private void visitRestriction(OWLQuantifiedRestriction<?, ?, ? extends OWLClassExpression> desc) {
    if (desc.getProperty() instanceof OWLProperty) {
      Property restricted = owl.getPropertyFor((OWLProperty<?, ?>) desc.getProperty());
      boolean matches =
          useSuperproperties ? restricted.is(property, owl) : restricted.equals(property);
      if (matches && addNew(owl.unwrap(desc.getFiller()))) {
        // keep it for inspection at the end
        this.restriction = desc;
      }
    }
  }

  @Override
  public void visit(OWLObjectIntersectionOf desc) {
    visitClassExpressions(desc.getOperands(), null);
  }

  private void visitClassExpressions(
      Collection<OWLClassExpression> expressions, OWLClassExpression self) {
    List<OWLClassExpression> inherited = new ArrayList<>();
    for (OWLClassExpression expression : expressions) {
      if (expression.equals(self)) {
        continue;
      }
      // Visit direct anonymous restrictions before inherited named classes. Do not traverse unions:
      // subclassing a union does not imply subclassing each union branch.
      if (expression instanceof OWLClass) {
        inherited.add(expression);
      } else {
        expression.accept((OWLClassExpressionVisitor) this);
      }
    }
    for (OWLClassExpression expression : inherited) {
      expression.accept((OWLClassExpressionVisitor) this);
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

  @Override
  public void visit(OWLObjectSomeValuesFrom desc) {
    visitRestriction(desc);
  }
}
