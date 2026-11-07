"""Durable full-roadmap queue: historical evidence is never a completion shortcut.

No daemon, model invocation, service startup, permissions override or native run.
Resume selection is deterministic; checkpoints must be explicitly verified by the
calling agent. This file does not autonomously restart a terminated agent.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
QUEUE = Path(__file__).with_name("roadmap-execution-queue.json")
AUDIT = ROOT / "docs/java-service-contract-gaps/acceptance-audit.json"

# Concrete remainders, not repeats of delivered wrappers. Shared work is batched
# by the first row's identifier; native acceptance remains an independent axis.
WORK = [
    ("CAP-04", "persisted-relationship-selection", "Add public sync/async persisted endpoint selection with exact source/target headers, origin/root binding and saved-reference refusal until versioned; independent simulator graph/query workflow and collection enumeration."),
    ("RC-P1.5", "material-selection-effects", "Verify project/namespace/scenario/observer/grid options change declared synthetic plan and executed input, not only echoed request metadata; reject unsupported forced models."),
    ("RC-P1.3", "explicit-collaborator-rights", "Add explicit opt-in shared read grants in independent simulator and public client access inspection; read-only collaborator positive control plus write/release denial, expired versus forbidden and grant revocation."),
    ("RC-P4.2 CAP-05", "revision-aware-validation", "Expose typed validation evidence pinned to source hash and knowledge service/worldview/revision; explicit revalidation after revision change, parser success distinct from semantic admission; independent finite validator fixture."),
    ("RC-P4.4 CAP-14", "conditional-authoring", "Implement opt-in reference accepted-document revision/update/validate/publish workflow with public SDK DTOs, CAS conflicts and permission checks; private draft CAS alone is insufficient; synthetic admission not Java compilation."),
    ("RC-P4.1 CAP-13", "dependency-inspection", "Add public immutable resource/model dependency closure inspection with exact source/provider/version/access identity, pagination/cycle/budget controls and independent simulator fixtures; no implicit model selection."),
    ("RC-P4.3", "annotated-input-provenance", "Compose public import/annotation/observation and dependency inspection into one owned flow with explicit selected-input provenance, denial/failed-import cleanup and no fabricated native resource deletion."),
    ("RC-P3.1 CAP-10 CAP-11", "reference-commit-convergence", "Add public opt-in graph checkpoint/reconciliation API and independently simulated complete finite graph deltas with duplicate/out-of-order Job hints, explicit gap resync and stable reference revisions; no native scientific commit claim."),
    ("RC-P3.4 CAP-06", "execution-dependency-explanation", "Provide public dependency/execution explanation joining supplied resources, model/component versions and planned versus recorded effects; finite complete reference lineage including semantic-update effects, not only fixture graph links."),
    ("CAP-03", "public-copied-composition", "Replace raw-transport-only copied composition examples with typed public opt-in SDK source snapshots/vector/result lifecycle API; independently authorized sources, pinned identity, no upstream grants inferred; native live federation separate."),
    ("RC-P4.5", "managed-behavior-workflow", "Complete public reconnectable synthetic managed behavior/agent source validation and outcome workflow including private-session disposal evidence in simulator; distinguish declared fixture execution from native k.Actors/compiler."),
    ("RC-P5 CAP-17", "worker-output-and-budget-custody", "Complete worker output budget/deployment handoff and public protocol failure/recovery tests; hard total disk/hostile-code isolation requires reviewed OS/container policy, not a claimed Python sandbox."),
    ("RC-P2.2 CAP-09", "selected-reference-windows", "Extend public reference read plans to explicit bounded spatial windows and selected synthetic temporal states with per-selection layout/masks/units/pins, immutable assembled data and cancellation/expiry controls."),
    ("RC-P2.5 CAP-15", "categorical-reference-data", "Expose reference categorical dictionaries/worldview and explicit temporal labels through public data adapters, not only caller labels; verify raw integer codes, masks and metadata across installed notebook/array/raster paths."),
    ("RC-P0", "operation-workflow-attribution", "Map every public operation to executable tests/workflows or exact unsupported disposition; measured body lines alone insufficient. Resolver encoder remains unsupported until maintained scientific encoding authority, with all independent preparation/inspection available."),
    ("RC-P1.1 RC-P1.4 CAP-01 CAP-02", "onboarding-and-routing-acceptance", "Run installed public onboarding/catalog/expiry/mixed-provider positive-negative scenarios and record Python completion separately from actual production issuer/license/deployment approval."),
    ("RC-P1.2 RC-P3.3 CAP-07", "restoration-lifecycle-acceptance", "Run integrated installed sync/async context/observation/provider/runtime Job restart/expiry/cancel/identity-fencing matrix with explicit no-replay; distinguish simulator retention from native incarnations."),
    ("RC-P3.5", "notebook-integrated-acceptance", "Run installed independent notebook clients through bounded concurrent Jobs, own-wait cancellation, collaboration denial and graph reconciliation without deleting owner state."),
    ("RC-P2.1", "multishard-scientific-acceptance", "Integrate public synthetic multishard and authentic recorded-data read/export/analysis known-answer workflows with independent coordinates/masks/precision checks; real provider science remains separately gated."),
    ("RC-P2.3", "native-provider-oracle", "Retain executable native provider comparison runner; obtain approved pinned actual-provider model/data/datum/tolerance fixture before real science. Do not replace provider accuracy or historical study with synthetic arithmetic."),
    ("RC-P2.4", "stream-scale-acceptance", "Retain installed bounded-memory/interrupted-stream public API controls; representative native-provider scale/soak requires approved deployment/workload, not fabricated capacity."),
    ("RC-P3.2 CAP-08 CAP-12 CAP-18", "safety-regression-acceptance", "Reverify public stale/partial refresh, exact scalar precision/missingness, socket-forbidden offline restoration, no implicit replay/fallback and optional-dependency safety after remaining features."),
    ("RC-R1 CAP-16", "integrated-packaging-compatibility", "Run complete installed SDK/worker/simulator matrix, builds/raw module parity, types/lint/coverage and mixed-version failures; reconcile all42rows and all gap reproductions at exact merged candidate."),
    ("RC-R2", "release-readiness-not-publication", "Run package/support/release-readiness checks and prepare exact tested limitations/migration guidance; publication/develop/support approval remain external and are not authorized by queue completion."),
]


def seed(root=ROOT):
    audit = json.loads((root / AUDIT.relative_to(ROOT)).read_text(encoding="utf-8"))
    ledger = (root / "notes/python-roadmap-execution/FULL_ROADMAP_EXECUTION_LEDGER.md").read_text(encoding="utf-8")
    current = {}
    for line in ledger.splitlines():
        if line.startswith(("|RC-", "|CAP-")):
            _, identity, status, action, _ = line.split("|")
            current[identity] = (status, action)
    gaps = json.loads((root / "docs/java-service-contract-gaps/gaps.yaml").read_text())
    actions = {identity: (index, name, action) for index, (ids, name, action) in enumerate(WORK)
        for identity in ids.split()}
    records = []
    for row in audit["records"]:
        identity = row["acceptance_id"]
        rank, group, action = actions[identity]
        records.append({"id": identity, "original_requirement": row["original_requirement"],
            "required_public_python_functionality": ("klab_worker.WorkerClient / Field / Limits / execute / run_once; separate experimental scoped worker"
                if identity in ("RC-P5", "CAP-17") else row["public_api"]),
            "historical_audit": row, "current_ledger_baseline": current[identity][0],
            "remaining_implementation": action, "required_tests": {
                "existing_unit": row["unit_tests"], "existing_simulator": row["simulator_tests"],
                "existing_real_java": row["real_java_tests"],
                "new_acceptance": "Public installed sync/async API, positive/negative authorization, data/state/lifecycle and owned cleanup for: " + group},
            "simulator_support_needed": action,
            "implementation_status": "REMAINDER_PENDING" if rank < 19 else "BOUNDED_IMPLEMENTATION_DELIVERED_ACCEPTANCE_PENDING",
            "simulator_acceptance_status": "BOUNDED_PRIOR_EVIDENCE_FULL_ROW_NOT_YET_ATTESTED",
            "real_java_acceptance_status": "NOT_RUN", "historical_status_unchanged": True,
            "java_gap_ids": sorted({gap["id"] for gap in gaps["gaps"] if identity in gap["requirement_ids"]} | set(row["java_gap_ids"])),
            "pr_and_merge_evidence": {"historical_prs": row["prs"], "historical_commits": row["commits"],
                "current_campaign_receipts": "notes/python-roadmap-execution/evidence/full-roadmap-*.json"},
            "work_group": group, "priority": rank, "task_state": "pending",
            "next_executable_action": action, "disposition": None})
    if len(records) != 42 or set(actions) != {r["id"] for r in records}:
        raise ValueError("Queue must preserve all42 original requirements exactly")
    return {"version": 1, "source_audit_sha256": hashlib.sha256((root / AUDIT.relative_to(ROOT)).read_bytes()).hexdigest(),
        "historical_evidence_policy": "Historical audit records embedded unchanged; new Python/simulator/native axes are independent. No PARTIAL/native blocker automatically waives Python work.",
        "active": {"branch": "test/python-selection-workflow-audit", "pr": None, "requirement": "CAP-04", "ci_review_dependencies": []},
        "last_verified_checkpoint": {"pr": 289, "merge": "48ef20741dd5a61036d58e6ed21ff3bc2c9358af", "utc": "2026-11-07T06:19:52Z", "state": "VERIFIED_MERGED"},
        "next_actionable_requirement": "CAP-04", "remaining_runnable_work": [name for _, name, _ in WORK if name not in ("native-provider-oracle", "stream-scale-acceptance", "release-readiness-not-publication")],
        "external_blockers": ["Native actual-provider scientific tuple/permissions/oracles", "Maintained scientific encoder authority", "Production issuer/license/roles/deployment approval", "Reviewed hostile-code/total-disk container isolation policy", "Native compatibility/staging and release/develop approval remain distinct"],
        "completion_criteria": ["Every42original row has explicit disposition and complete in-scope public Python functionality", "Installed public SDK simulator-backed tests including scientific/state/authorization/lifecycle controls pass", "Required package/types/lint/coverage/CI and permitted PR merge verified", "17stable gaps have synchronized narrative and individual executable reproductions", "No independently executable implementation work remains; native/release gates separately recorded"],
        "continuation": {"supported_cli": "opencode run --session <session-id> --file notes/python-roadmap-execution/RESUME_FULL_ROADMAP.md", "automatic_restart_configured": False,
            "limitation": "CLI supports explicit session resume; documented CLI exposes no guaranteed unattended restart on process exit. Queue/checkpoint is durable, not a daemon. Do not invoke concurrently into the active session or bypass permissions with --auto."},
        "requirements": sorted(records, key=lambda r: (r["priority"], r["id"]))}


def validate(value):
    audit = json.loads(AUDIT.read_text(encoding="utf-8"))
    expected = {row["acceptance_id"]: row for row in audit["records"]}
    rows = value["requirements"]
    if len(rows) != 42 or {r["id"] for r in rows} != set(expected): raise ValueError("Original42ID mismatch")
    for row in rows:
        if row["historical_audit"] != expected[row["id"]]: raise ValueError("Historical evidence changed")
        if not row["next_executable_action"] or not row["required_tests"]: raise ValueError("Missing action/tests")
        if row["disposition"] == "PYTHON_COMPLETE_SIMULATOR_VERIFIED" and not row.get("final_acceptance_receipt"):
            raise ValueError("Completion requires row-specific final acceptance receipt")
    return rows


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("seed", "check", "next", "checkpoint"))
    parser.add_argument("--pr", type=int)
    args = parser.parse_args(argv)
    if args.command == "seed":
        if QUEUE.exists(): raise ValueError("Refuse to reset ongoing campaign queue")
        QUEUE.write_text(json.dumps(seed(), indent=2) + "\n", encoding="utf-8")
    value = json.loads(QUEUE.read_text(encoding="utf-8")); rows = validate(value)
    if args.command == "checkpoint":
        if not args.pr or args.pr <= 0: raise ValueError("Checkpoint requires a positive PR number")
        pr = json.loads(subprocess.check_output(["gh", "pr", "view", str(args.pr), "--json",
            "state,mergeCommit,mergedAt,headRefOid,baseRefName"], cwd=ROOT, text=True))
        if pr["state"] != "MERGED" or pr["baseRefName"] != "feature/python-experiment-runner":
            raise ValueError("Only a verified merged campaign PR can checkpoint delivery")
        merge = pr["mergeCommit"]["oid"]
        subprocess.run(["git", "merge-base", "--is-ancestor", merge, "HEAD"], cwd=ROOT, check=True)
        value["last_verified_checkpoint"] = {"pr": args.pr, "merge": merge, "head": pr["headRefOid"],
            "utc": pr["mergedAt"], "state": "VERIFIED_MERGED"}
        value["active"]["branch"] = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip()
        value["active"]["pr"] = None
        QUEUE.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    runnable = sorted((r for r in rows if r["task_state"] in ("pending", "in_progress") and r["work_group"] in value["remaining_runnable_work"]),
        key=lambda row: (row["priority"], row["id"]))
    if args.command == "next":
        print(json.dumps({"id": runnable[0]["id"], "action": runnable[0]["next_executable_action"]} if runnable else {"no_runnable_work": True}, indent=2))
    else: print(json.dumps({"requirements": len(rows), "runnable": len(runnable), "complete": sum(r["disposition"] is not None for r in rows)}))
    return 0


if __name__ == "__main__": raise SystemExit(main())
