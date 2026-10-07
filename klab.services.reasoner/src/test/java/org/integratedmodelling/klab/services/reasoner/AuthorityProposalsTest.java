package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityProposals;
class AuthorityProposalsTest {
  @Test void infersOneDistinctIdentityAndRejectsAmbiguityWithoutExplicitTarget() {
    var document = mock(KimNamespace.class); var model = mock(KimModel.class);
    var observable = mock(KimObservable.class); var cat = mock(KimConcept.class);
    when(cat.getAuthority()).thenReturn("TAXA"); when(cat.getAuthorityTerm()).thenReturn("[3DXV3]");
    when(observable.getSemantics()).thenReturn(cat);
    when(model.getObservables()).thenReturn(List.of(observable));
    when(model.getDependencies()).thenReturn(List.of(observable));
    when(model.getAnnotations()).thenReturn(List.of(Annotation.of("proposal", "authoritycode", "taxonomy.species:FelisCatus")));
    when(model.getLength()).thenReturn(40); when(model.getOffsetInDocument()).thenReturn(20);
    when(document.getStatements()).thenReturn(List.of(model)); when(document.getUrn()).thenReturn("test.namespace");
    when(document.getSourceCode()).thenReturn("source");
    var extracted = AuthorityProposals.extract(document);
    assertEquals(1, extracted.candidates().size()); assertTrue(extracted.notifications().isEmpty());
    assertEquals("3DXV3", extracted.candidates().getFirst().identity());
    var dog = mock(KimConcept.class); when(dog.getAuthority()).thenReturn("TAXA"); when(dog.getAuthorityTerm()).thenReturn("DOG");
    when(cat.getTraits()).thenReturn(List.of(dog));
    assertTrue(AuthorityProposals.extract(document).candidates().isEmpty());
    assertEquals(1, AuthorityProposals.extract(document).notifications().size());
    when(model.getAnnotations()).thenReturn(List.of(Annotation.of("proposal", "authoritycode", "taxonomy.species:FelisCatus", "target", "TAXA:[3DXV3]")));
    assertEquals(1, AuthorityProposals.extract(document).candidates().size());
  }
}
