# Notebook recordings

Seven actual Jupyter screen recordings, **39–61 seconds each**, approximately six
minutes in total. Code execution is automated; outputs are produced by the cells,
not rendered from prepared success messages. Notebook loading is trimmed, while
execution and reading pauses remain. There is no narration or performance claim.

Watch the [notebook gallery](../../notebook-videos.md). Each video has a matching
executed HTML notebook, downloadable `.ipynb`, Python-cell source and PNG poster.
Posters are frames extracted from the recordings.

## Evidence

- [Manifest](manifest.json): all seven notebooks passed functional execution and
  browser execution; **34 code cells, 10 plots, one actual kernel restart**.
- [Run identity and cleanup](run-evidence.json): exact client/server/component
  identity, source checks, private-value scan and successful owned stack cleanup.
- [Current corrected-source verification](../../results/review-corrections-verification.json).
- [Setup and reproduction](../../../examples/notebooks/README.md).

The recorder executes every notebook in fresh kernels before capture, then opens
clean copies and executes the actual cells through Jupyter's UI. The saved-work
example verifies a changed Python PID and unchanged observation/Job IDs, and
checks that restoration did not issue a scientific submission. All seven MP4s
were fully decoded after encoding. A credential scan checked the two actual TEST
tokens and authority enrollment secret against the selected public outputs.

The source notebooks and helper are public, but the deployment state is not.
`notebook_setup.py` loads credentials from the private path named by
`KLAB_NOTEBOOK_STATE`; it contains no token or certificate values. Its other tasks
are explicitly TEST-only controls, ownership tracking and ordinary plot helpers.
It does not replace SDK computation, downloads or restoration with mock output.

## Scope

Real local services and a controlled TEST authority; synthetic known-answer models
and a 5 × 4 numerical field. No claim of real terrain data, production Hub
onboarding, independently deployed services, server-crash durability or complete
provenance. The current local analysis work and carried geospatial patch remain
separate from the merged PRs and from a published package release.

Earlier slide-based clips in the sibling `preview/` directory were rejected and
are historical artifacts, not the current release demos.
