package org.integratedmodelling.klab.api.services.reasoner.objects;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class AuthorityIdentitySyntaxTest {
  @Test void canonicalTokensRoundTripReservedCharacters() {
    for (String code : List.of("123", "abc", "ABC", "A", "MixedCase", "a:b", "x] of test:Bad", "a b", "é", "a" + (char)92 + "b")) {
      var token = AuthorityIdentitySyntax.encode("TAXA", code);
      assertEquals(code, AuthorityIdentitySyntax.decode(token.substring(5)));
    }
    assertEquals("TAXA:123", AuthorityIdentitySyntax.encode("TAXA", "123"));
    assertEquals("TAXA:[A B]", AuthorityIdentitySyntax.encode("TAXA", "A B"));
  }
  @Test void requestBoundsAndInjectedAuthorityNamesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> AuthorityIdentitySyntax.encode("TAXA:bad", "123"));
    assertThrows(IllegalArgumentException.class, () -> AuthorityIdentitySyntax.decode("[a] bad]"));
    assertThrows(IllegalArgumentException.class, () -> new AuthoritySearchRequest("TAXA", "q", null, 0, 101));
    assertThrows(IllegalArgumentException.class, () -> new AuthoritySearchRequest("TAXA", "q", null, -1, 5));
    assertThrows(IllegalArgumentException.class, () -> new AuthoritySearchRequest("TAXA", "", null, 0, 5));
  }
}
