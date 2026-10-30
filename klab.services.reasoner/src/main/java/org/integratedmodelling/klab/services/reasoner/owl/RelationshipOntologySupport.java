package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.semanticweb.owlapi.model.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Readable, lossless class annotations for axis choices; no participant-property symmetry axioms. */
public final class RelationshipOntologySupport {
  public static final String PREFIX="urn:klab:semantics:relationship:";
  private RelationshipOntologySupport() {}
  public static void compile(OWL owl,Ontology ontology,Concept owner,KimConceptStatement source) {
    if(!owner.is(SemanticType.RELATIONSHIP))return;
    var axes=RelationshipAxes.of(owner.getType());var factory=owl.manager.getOWLDataFactory();
    var cls=owl.getOWLClass(owner);var target=ontology.getOWLOntology();
    Map<String,String> values=Map.of("declaredNature",source.getDeclaredRelationshipNature().name(),
        "declaredOrientation",source.getDeclaredRelationshipOrientation().name(),
        "effectiveNature",axes.nature().name(),"effectiveOrientation",axes.orientation().name());
    write(owl,target,cls,values);
    if(axes.nature()!=RelationshipAxes.Nature.UNSPECIFIED) {
      String kind=axes.nature()==RelationshipAxes.Nature.STRUCTURAL?"StructuralRelationship":"FunctionalRelationship";
      owl.manager.addAxiom(target,factory.getOWLSubClassOfAxiom(cls,
          factory.getOWLClass(IRI.create("http://integratedmodelling.org/odo#"+kind))));
    }
  }
  /** Internal boolean classes have effective axes but no authored declaration choices. */
  public static void compileEffective(OWL owl,Ontology ontology,Concept owner) {
    if(!owner.is(SemanticType.RELATIONSHIP))return;
    var axes=RelationshipAxes.of(owner.getType());
    write(owl,ontology.getOWLOntology(),owl.getOWLClass(owner),Map.of(
        "effectiveNature",axes.nature().name(),"effectiveOrientation",axes.orientation().name()));
  }
  private static void write(OWL owl,OWLOntology target,OWLClass cls,Map<String,String> values) {
    var factory=owl.manager.getOWLDataFactory();
    for(var pair:values.entrySet()) {
      var property=factory.getOWLAnnotationProperty(IRI.create(PREFIX+pair.getKey()));
      // Recompilation replaces an axis value, never accumulates contradictory old annotations.
      owl.manager.removeAxioms(target,target.annotationAssertionAxioms(cls.getIRI())
          .filter(a->a.getProperty().equals(property)).toList());
      owl.manager.addAxiom(target,factory.getOWLDeclarationAxiom(property));
      owl.manager.addAxiom(target,factory.getOWLAnnotationAssertionAxiom(property,cls.getIRI(),factory.getOWLLiteral(pair.getValue())));
    }
  }
  /** Null means absent annotation evidence, not default direction. */
  public static RelationshipAxes read(OWLClass cls,Collection<OWLOntology> ontologies) {
    var values=new HashMap<String,String>();
    for(var ontology:ontologies)ontology.annotationAssertionAxioms(cls.getIRI()).forEach(a->{
      var key=a.getProperty().getIRI().toString();
      if(key.startsWith(PREFIX) && a.getValue() instanceof OWLLiteral literal) {
        String old=values.putIfAbsent(key.substring(PREFIX.length()),literal.getLiteral());
        if(old!=null&&!old.equals(literal.getLiteral()))throw new IllegalArgumentException("RELATIONSHIP_AXIS_CONFLICT: inconsistent OWL annotations");
      }
    });
    if(!values.containsKey("effectiveNature")&&!values.containsKey("effectiveOrientation"))return null;
    return new RelationshipAxes(RelationshipAxes.Nature.valueOf(values.getOrDefault("effectiveNature","UNSPECIFIED")),
        RelationshipAxes.Orientation.valueOf(values.getOrDefault("effectiveOrientation","UNSPECIFIED")));
  }
}
