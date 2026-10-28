# Separate feature PRs

User requirement: each feature has its own PR and contribution-guide assessment.
The earlier single integration branch remains intact; no published history was
rewritten. Feature branches point to their already reviewed checkpoints.

| Feature | Head branch / checkpoint | PR base | Draft PR |
| --- | --- | --- | --- |
| F1 semantic explorer | `feature/python-semantic-explorer` / `69c424a3b` | `feature/python-discover-compare-explain-integration` | [#101](https://github.com/integratedmodelling/klab-services/pull/101) |
| F2 resource/model inspector | `feature/python-resource-inspector` / `e4580f17f` | `feature/python-semantic-explorer` | [#99](https://github.com/integratedmodelling/klab-services/pull/99) |
| F3 execution evidence | `feature/python-execution-evidence` / `b45e0b8c2` | `feature/python-resource-inspector` | [#98](https://github.com/integratedmodelling/klab-services/pull/98) |
| F4 experiment runner | `feature/python-experiment-runner` / `7fae2ea97` | `feature/python-execution-evidence` | [#100](https://github.com/integratedmodelling/klab-services/pull/100) |
| Delivery | `feature/python-milestone-delivery` / reviewed delivery checkpoint | `feature/python-experiment-runner` | Created after delivery assessment |

`feature/python-discover-compare-explain-integration` preserves the common
reconnaissance/specification checkpoint `6bb7a8224`. Stacked bases keep each PR's
diff feature-specific while retaining its dependencies. Maintainers can review
the whole stack without first merging any feature into `feature/python-client`.
No PR has been merged; retargeting/integration must be deliberate after review.

The original `feature/python-discover-compare-explain` remains the combined working
checkpoint. The final combined contribution still targets `feature/python-client`.
A refreshed remote check on 28 October found that target still at milestone base
`1e20fc478f645b5fb5c2fd00ffd223a040ca2878`; no upstream Java changes were imported.
All PRs remain draft because required live scientific evidence is incomplete.
