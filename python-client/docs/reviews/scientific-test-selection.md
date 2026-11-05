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
