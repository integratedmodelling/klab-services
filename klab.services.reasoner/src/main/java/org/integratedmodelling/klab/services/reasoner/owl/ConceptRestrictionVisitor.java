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
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * Visit the hierarchy to find the restriction that explicitly uses the passed concept as a filler.
 *
 * @author ferdinando.villa
 */
public class ConceptRestrictionVisitor implements OWLClassExpressionVisitor {

  private Set<OWLOntology> onts;
  private Set<OWLClass> processedClasses = new HashSet<>();
  private OWLQuantifiedRestriction<? extends OWLClassExpression> restriction;
  private List<OWLQuantifiedRestriction<? extends OWLClassExpression>> restrictions =
      new ArrayList<>();
  private OWLClass filler;

  public OWLQuantifiedRestriction<? extends OWLClassExpression> getRestriction() {
    return this.restriction;
  }

  public Collection<OWLQuantifiedRestriction<? extends OWLClassExpression>> getRestrictions() {
    return this.restrictions;
  }

  public ConceptRestrictionVisitor(Concept concept, Concept filler, OWL owl) {
    this.onts = owl.manager.getOntologies();
    this.filler = owl.getOWLClass(filler);
    OWLClass owlClass = owl.getOWLClass(concept);
    if (owlClass != null && this.filler != null) {
      owlClass.accept(this);
    }
  }

  @Override
  public void visit(OWLClass desc) {

    if (processedClasses.add(desc)) {
      visitClassExpressions(EntitySearcher.getSuperClasses(desc, onts.stream()).toList(), desc);
      visitClassExpressions(
          EntitySearcher.getEquivalentClasses(desc, onts.stream()).toList(), desc);
    }
  }

  private void visitRestriction(OWLQuantifiedRestriction<? extends OWLClassExpression> desc) {
    if (hasFiller(desc.getFiller())) {
      if (this.restriction == null) {
        this.restriction = desc;
      }
      this.restrictions.add(desc);
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

  private boolean hasFiller(OWLClassExpression expression) {
    if (expression.equals(this.filler)) {
      return true;
    }
    if (expression instanceof OWLObjectIntersectionOf) {
      for (OWLClassExpression operand : ((OWLObjectIntersectionOf) expression).getOperands()) {
        if (hasFiller(operand)) {
          return true;
        }
      }
    } else if (expression instanceof OWLObjectUnionOf) {
      for (OWLClassExpression operand : ((OWLObjectUnionOf) expression).getOperands()) {
        if (hasFiller(operand)) {
          return true;
        }
      }
    }
    return false;
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

  public boolean isDenied() {
    if (this.restriction != null) {
      if (this.restriction instanceof OWLObjectExactCardinality) {
        return ((OWLObjectExactCardinality) this.restriction).getCardinality() == 0;
      } else if (this.restriction instanceof OWLObjectMaxCardinality) {
        return ((OWLObjectMaxCardinality) this.restriction).getCardinality() == 0;
      }
    }
    return false;
  }

  public boolean isOptional() {
    if (this.restriction == null) {
      return false;
    }
    if (this.restriction instanceof OWLObjectMinCardinality) {
      return ((OWLObjectMinCardinality) this.restriction).getCardinality() == 0;
    }
    return !(this.restriction instanceof OWLObjectCardinalityRestriction);
  }
}
