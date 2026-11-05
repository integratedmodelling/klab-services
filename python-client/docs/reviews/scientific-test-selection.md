# TA-5/6/7 selection contribution review — development pass

Contributor self-review under KLAB_CLI_Contribution_Review.md; not human approval.
Independent base937c758b62743a0ab68e48792870a9261cf7cc87, branch
feature/python-scientific-test-selection. No stacking of unpublished scenario PRs.

**Recommendation: Needs measured and integration evidence.**14focusedtests PASS,
Ruff clean. Deterministic path/capability selector conservatively broadens unknown,
shared/high-risk/selector changes; retains smoke/negative tests and outputs exact
commands/NOT_RUN. No actual Java auto-run. Existing full CI/17coverage gates retained;
manual/weekly broad offline run added. PR-base diff errors broaden. Capability
declaration cannot override a risky changed path. Documentation is narrowly scoped.

No SDK/Java/model/worldview/component change or alternate workflow scheduler.
Measurement script runs bounded serial offline samples on same source/interpreter,
fresh evidence directory, captures actual counts/durations/commands. No invented
speedup,irrelevant Java reductions,flaky reruns or scientific passes. No services
launched by selector; test subprocesses retain own controlled lifecycle boundaries.

Pending: execute comparable sample, verify CI YAML and complete installed gates;
associate N/S receipts and authoritative map/report after actual runs. Provisioned
scheduled L4 worker absent: current schedule is broad offline only, no skipped/fake
scientific placeholder. That limitation must remain explicit in final gates.
No milestone PR/merge published; normal ref/rules failure remains an independent
delivery blocker requiring restoration evidence or explicitly authorized remedy.

## Measured sample and final integration correction

Same-source5049ae65f serial sample:baseline3434/72.0315s,docs351/2.0636s,
decoder3434/67.4729s,JavaDTO3434/67.6977s;0failures/errors/skips. Selector-only
dry-runs measured0.0736/0.0707/0.0708s. Initial baseline overlaps the tail of N's
stack run by approximately13seconds; not a quiescent-machine speed benchmark.
Do not infer causal/global speedup from wall-time differences; lower selected count
and conservative high-risk broadening are directly demonstrated. No Java families
ran in the offline samples, so no measured Java-churn reduction. N/S each really
ran once/pass once independently;0flaky reruns.

Final installed run caught a missing linked evidence-index/report in this worktree:
1FAIL/3433PASS. This is a local artifact-assembly defect, not external BLOCKED and
not a reason to weaken link tests. Copied the verified generated index/report to
their canonical contribution paths; rerun required. Existing CI YAML parsed with
already-installed Anaconda PyYAML6.0.1 (not added as dependency); all17coverage and
Pythonversion/core-only/Windows/build checks retained. No SDK/Java/scientific edits.
