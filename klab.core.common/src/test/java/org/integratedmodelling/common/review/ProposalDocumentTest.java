package org.integratedmodelling.common.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class ProposalDocumentTest {

  private static final String YAML =
      """
      proposal_schema: classpath:/schemas/llm/domain-context-proposal.schema.json
      context_pack_version: "1.3"
      proposal:
        id: domain-proposal
        revision_id: domain-r1
        title: Domain proposal
        iteration: 1
        supersedes_revision: null
        status: draft
        generated_at: 2026-09-01
        scope:
          domain: test domain
          purpose: test loading
          requested_tier: 1
          target_community: test community
          tier_1_context_required: false
          tier_1_context_status: not_required
          included: []
          excluded: []
          spatial_context: null
          temporal_context: null
          intended_scales: []
        source_policy:
          authority_criteria: []
          inference_policy: preserve evidence
        sources: []
        existing_ontologies: []
        root_scope_aliases: []
        evidence: []
        assets:
          - asset_id: model-1
            asset_type: model
            qualified_name: domain:Model
            record_version: 1
            record_hash: null
            name_history: []
            label: Model
            aliases: []
            disposition: propose
            definition: A test model.
            extraction_rationale: null
            evidence_refs: []
            epistemic_status: modeling_choice
            feedback:
              state: open
              comments: []
              decisions: []
              proposed_action_ids: []
              applied_action_ids: []
              last_reviewed_iteration: null
            lifecycle:
              state: proposed
              introduced_in_iteration: 1
              last_modified_in_iteration: 1
              deleted_in_iteration: null
              replacement_asset_ids: []
            open_questions: []
            alternatives: []
            metadata: {}
            namespace_asset_id: namespace-1
            observables: [domain:Observable]
            dependencies: []
            strategy_summary: Test strategy
            proposal_data:
              extension: retained
        dependency_order: [model-1]
        upstream_gaps: []
        root_domain_gaps: []
        iteration_control:
          current_iteration: 1
          previous_revision_id: null
          next_iteration: 2
          state: open_for_feedback
          unresolved_feedback_ids: []
          proposed_action_ids: []
          applied_action_ids: []
        actions: []
        orthogonality_review:
          hard_unambiguity_gate_passed: true
          pairwise_review_complete: true
          matrix: []
          unresolved_ambiguities: []
          unresolved_overlaps: []
        validation:
          schema_valid: true
          references_resolve: false
          acyclic: true
          requested_tier_valid: true
          mandatory_tier_1_context_present: true
          tier_ancestry_valid: true
          orthogonality_reviewed: true
          internally_unambiguous: true
          named_composition_reviews_complete: true
          semantic_review: not performed
          syntax_validation: not performed
          warnings: []
        corpus_feedback:
          state: open
          coverage_comments: []
          structural_comments: []
          cross_cutting_issues: []
          decisions: []
          requested_changes: []
        change_log: []
      """;

  @Test
  void yamlRoundTripPreservesTypedEnvelopeAndExtensionData() throws Exception {
    var mapper = new ObjectMapper(new YAMLFactory());
    var document = mapper.readValue(YAML, ProposalDocument.class);

    assertEquals(ProposalDocument.SCHEMA_RESOURCE, document.getProposalSchema());
    assertEquals(ProposalEnums.ProposalStatus.DRAFT, document.getProposal().getStatus());
    assertEquals("model-1", document.getProposal().getAsset("model-1").getAssetId());
    assertNull(document.getProposal().getAsset("missing"));
    var model =
        assertInstanceOf(ModelProposal.class, document.getProposal().getAssets().getFirst());
    assertEquals(ProposalEnums.AssetType.MODEL, model.getAssetType());
    assertEquals("retained", model.getProposalData().get("extension"));

    var serialized = mapper.readTree(mapper.writeValueAsBytes(document));
    assertTrue(serialized.has("proposal_schema"));
    assertTrue(serialized.path("proposal").has("revision_id"));
    assertEquals(
        "model", serialized.path("proposal").path("assets").get(0).path("asset_type").asText());

    var roundTripped = mapper.treeToValue(serialized, ProposalDocument.class);
    assertInstanceOf(ModelProposal.class, roundTripped.getProposal().getAssets().getFirst());
  }

  @Test
  void allAssetDiscriminatorsAndSchemaSpecificAcronymsAreMapped() throws Exception {
    var mapper = new ObjectMapper(new YAMLFactory());

    assertInstanceOf(
        ConceptProposal.class, mapper.readValue("asset_type: concept", ProposalAsset.class));
    assertInstanceOf(
        OntologyProposal.class, mapper.readValue("asset_type: ontology", ProposalAsset.class));
    assertInstanceOf(
        NamespaceProposal.class, mapper.readValue("asset_type: namespace", ProposalAsset.class));
    assertInstanceOf(
        ModelProposal.class, mapper.readValue("asset_type: model", ProposalAsset.class));

    var concept =
        assertInstanceOf(
            ConceptProposal.class,
            mapper.readValue(
                """
                asset_type: concept
                abstract: true
                type_inheritance:
                  declaration_keyword: thing
                  odo_im_chain: implicit
                  explicit_odo_derivation_required: false
                  rationale: implicit from declaration
                orthogonality:
                  atomic_dimension: true
                  composite_of: []
                  independently_variable_from: []
                  relations:
                    - other_asset_id: concept-2
                      relation: orthogonal
                      a_varies_with_b_fixed: true
                      b_varies_with_a_fixed: false
                      rationale: independent dimensions
                      required_action: null
                  assessment: independent
                alignment:
                  action: specialize
                  selected_parent: upper:Thing
                  direct_parent_tier: 1
                  tier_1_ancestor: domain:Subject
                  ancestry_to_tier_1: []
                  ancestry_status: verified
                  contextualized_within: null
                  candidates: []
                  rejected_candidates: []
                """,
                ProposalAsset.class));

    assertTrue(concept.isAbstract());
    assertEquals("implicit", concept.getTypeInheritance().getOdoImChain());
    ProposalSemantics.OrthogonalityRelation relation =
        concept.getOrthogonality().getRelations().getFirst();
    assertTrue(relation.getAVariesWithBFixed());
    assertEquals(Boolean.FALSE, relation.getBVariesWithAFixed());
    assertEquals("domain:Subject", concept.getAlignment().getTier1Ancestor());

    var serialized = mapper.readTree(mapper.writeValueAsBytes(concept));
    assertTrue(serialized.has("abstract"));
    assertTrue(serialized.path("type_inheritance").has("odo_im_chain"));
    assertTrue(serialized.path("alignment").has("tier_1_ancestor"));
  }
}
