package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

class SyntacticDocumentationTest {
  @Test void includesBeanFlagsClauseSourceAndParentDeclarationWithoutClaimingOwlTranslation() {
    var concept = new KimConceptImpl();
    concept.setName("audit:Child");
    concept.getType().add(SemanticType.SUBJECTIVE);
    var observable = new KimObservableImpl();
    observable.setSemantics(concept);
    observable.setUnit("m");
    var parent = new KimConceptImpl();
    parent.setName("audit:Parent");
    var child = new KimConceptStatementImpl();
    child.setUrn("Child");
    child.setDeclaredParent(parent);
    child.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause(
        "affectsClause", "affects audit:Height", 23, 20));
    child.getMetadata().put("custom", "meaning [b] `value`");
    var parentStatement = new KimConceptStatementImpl();
    parentStatement.setUrn("Parent");
    parentStatement.setAbstract(true);
    var declarations = Map.<String, KimConceptStatement>of("audit:Child", child, "audit:Parent", parentStatement);
    var markdown = SyntacticDocumentation.describe("audit:Child", observable, declarations::get);
    assertTrue(markdown.contains("SUBJECTIVE"));
    assertTrue(markdown.contains("affects audit:Height"));
    assertTrue(markdown.contains("audit:Parent"));
    assertTrue(markdown.contains("custom"));
    assertTrue(markdown.contains("unit"));
    assertTrue(markdown.contains("not evidence that an OWL restriction"));
    assertTrue(markdown.contains("automatically abstract"));
  }

  @Test void recursiveMetadataIsBoundedAndUnknownDeclarationsAreExplicit() {
    var bean = new KimConceptStatementImpl();
    bean.getMetadata().put("recursive", bean);
    assertTrue(SemanticDocumentation.describe("Cycle", bean).contains("See ` root `"));
    var concept = new KimConceptImpl();
    concept.setName("authority:External");
    assertTrue(SyntacticDocumentation.describe("authority:External", concept, n -> null)
        .contains("No source declaration available"));
  }
}
