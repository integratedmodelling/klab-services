package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
class SemanticSessionOwnershipTest {
  @Test void anotherUserCannotEditOrCancelAnExistingSession() throws Exception {
    var service = mock(ReasonerService.class, CALLS_REAL_METHODS);
    set(service, "semanticExpressions", Caffeine.newBuilder().maximumSize(10).build());
    set(service, "indexer", mock(Indexer.class)); set(service, "owl", mock(OWL.class));
    var alice = user("alice"); var bob = user("bob");
    var request = new SemanticSearchRequest(); request.setRequestId(1);
    var initial = service.semanticSearch(request, alice); assertTrue(initial.getSearchId() > 0);
    request.setSearchId(initial.getSearchId()); request.setRequestId(2);
    request.setSearchMode(SemanticSearchRequest.Mode.IDENTITY); request.setAuthority("TAXA"); request.setIdentityCode("123");
    assertThrows(SecurityException.class, () -> service.semanticSearch(request, bob));
    request.setCancelSearch(true);
    assertThrows(SecurityException.class, () -> service.semanticSearch(request, bob));
    assertEquals(0, service.semanticSearch(request, alice).getSearchId());
  }
  private UserScope user(String name) {
    var scope = mock(UserScope.class); var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn(name); when(scope.getUser()).thenReturn(identity); return scope;
  }
  private void set(Object target, String field, Object value) throws Exception {
    var f = ReasonerService.class.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
  }
}
