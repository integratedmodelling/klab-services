package org.integratedmodelling.klab.api.lang.kim;

import java.util.Objects;

/**
 * Lossless authored consequence syntax. This is a declaration, not proof that its condition
 * matched, a recipient exists or a consequence was executed. Native target semantics, secondary
 * inherency and contextual specialization remain separate through service transport.
 */
public record KimConsequenceRule(Kind kind, KimConcept target, Recipient recipient,
    KimConcept secondaryInherency, KimConcept within, KimConceptStatement.DeclarationClause source) {
  public enum Kind { CONFERS, IMPLIES, EMERGES_FROM }
  public enum RecipientKind { DEFAULT, SOURCE, TARGET, EXPRESSION }
  public record Recipient(RecipientKind kind, KimConcept expression) {
    public Recipient {
      Objects.requireNonNull(kind,"Recipient kind");
      if ((kind==RecipientKind.EXPRESSION)!=(expression!=null))
        throw new IllegalArgumentException("Only an expression recipient carries target semantics");
    }
    public static Recipient implicit() { return new Recipient(RecipientKind.DEFAULT,null); }
  }
  public KimConsequenceRule {
    Objects.requireNonNull(kind,"Consequence kind");
    Objects.requireNonNull(target,"Consequence target");
    Objects.requireNonNull(recipient,"Consequence recipient");
    Objects.requireNonNull(source,"Authored source span");
    if (kind!=Kind.CONFERS && recipient.kind()!=RecipientKind.DEFAULT)
      throw new IllegalArgumentException("Only conferrals select an attribution recipient");
    if (kind!=Kind.IMPLIES && secondaryInherency!=null)
      throw new IllegalArgumentException("Only implications carry secondary inherency");
  }
}
