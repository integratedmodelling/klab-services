"""Repository-only Java gap governance. Never import the SDK or contact a service."""
import argparse
import ast
from collections import Counter
import copy
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/java-service-contract-gaps"
MAX_BYTES = 4 * 1024 * 1024
HANDOFF_BEGIN = "<!-- BEGIN GENERATED JAVA GAP HANDOFF -->"
HANDOFF_END = "<!-- END GENERATED JAVA GAP HANDOFF -->"


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def load(path):
    if path.stat().st_size > MAX_BYTES:
        raise ValueError("Governance document exceeds byte budget")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def expanded(document=None):
    document = load(DATA / "gaps.yaml") if document is None else document
    if set(document) != {"version", "defaults", "gaps"}:
        raise ValueError("Registry envelope must contain version/defaults/gaps")
    return {"version": document["version"], "gaps": [
        {**copy.deepcopy(document["defaults"]), **copy.deepcopy(gap)} for gap in document["gaps"]]}


def local_ref(reference):
    if reference.startswith("https://"):
        return None
    path = (ROOT / reference.split("::", 1)[0].split("#", 1)[0]).resolve()
    path.relative_to(ROOT)
    if not path.is_file():
        raise ValueError("Missing repository reference: " + reference)
    return path


def test_ref(reference):
    path = local_ref(reference)
    if path is None or path.suffix != ".py":
        raise ValueError("Executable case must name a repository Python test")
    if "::" in reference:
        symbol = reference.split("::", 1)[1].split("[", 1)[0]
        names = {node.name for node in ast.walk(ast.parse(path.read_text(encoding="utf-8")))
                 if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))}
        if symbol not in names:
            raise ValueError("Missing executable test symbol: " + reference)


def originals():
    text = (ROOT / "docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md").read_text(encoding="utf-8")
    return dict(re.findall(r"^\| ((?:RC-|CAP-)[^|]+) \| ([^|]+) \|", text, re.M))


def validate(registry=None, audit=None, issues=None):
    from jsonschema import Draft202012Validator, FormatChecker
    registry = expanded() if registry is None else registry
    schema = load(DATA / "schema.json")
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(registry)
    gaps = registry["gaps"]
    ids = [gap["id"] for gap in gaps]
    cases = [gap["contract_case"] for gap in gaps]
    reserved = load(DATA / "id-reservations.json")["reserved"]
    if len(ids) != len(set(ids)) or len(cases) != len(set(cases)) or set(ids) != set(reserved):
        raise ValueError("Duplicate/reused/unreserved or deleted gap/case ID")
    aliases = [alias for gap in gaps for alias in gap["legacy_ids"]]
    if len(aliases) != len(set(aliases)):
        raise ValueError("Legacy aliases must have one canonical successor")
    original = originals()
    index = load(ROOT / "developer-tools/klab-service-simulator/contract-cases.json")
    scenario_ids = {case["id"] for case in index["cases"]}
    covered = set()
    for gap in gaps:
        if not set(gap["requirement_ids"]) <= set(original):
            raise ValueError("Unknown original requirement")
        covered.update(gap["simulator_scenario_refs"])
        if not set(gap["simulator_scenario_refs"]) <= scenario_ids:
            raise ValueError("Unknown simulator case")
        for reference in gap["python_test_refs"]:
            test_ref(reference)
        for reference in gap["links"] + gap["real_java_test_refs"] + [gap["reproducer_ref"]]:
            local_ref(reference)
        if gap["current_real_result"] != "REAL_NOT_RUN" and (
                not gap["real_java_test_refs"] or not gap["source_build_or_commit"]):
            raise ValueError("Current real result requires pinned actual Java evidence")
        if gap["java_status"] == "JAVA_FIXED_VERIFIED" or gap["lifecycle"] in ("REAL_VERIFIED", "CLOSED"):
            if gap["current_real_result"] != "REAL_PASS" or not gap["real_java_test_refs"] or not gap["source_build_or_commit"]:
                raise ValueError("Simulation/source inspection cannot resolve a Java gap")
        if gap["real_command"] is not None:
            raise ValueError("Real command requires a separately reviewed bounded adapter; arbitrary commands prohibited")
    if covered != scenario_ids:
        raise ValueError("Every provisional simulator case needs canonical gap traceability")
    audit = load(DATA / "acceptance-audit.json") if audit is None else audit
    delivery = load(local_ref(audit["pr_delivery_ref"]))
    prs = {pr["number"]: pr for pr in delivery["prs"]}
    records = audit["records"]
    if len(records) != len(original) or {r["acceptance_id"] for r in records} != set(original):
        raise ValueError("Missing/duplicate original acceptance rows")
    record_schema = {"$ref": "#/$defs/acceptanceRecord", "$defs": schema["$defs"]}
    for record in records:
        Draft202012Validator(record_schema).validate(record)
        if record["original_requirement"] != original[record["acceptance_id"]]:
            raise ValueError("Original acceptance wording changed")
        if not set(record["java_gap_ids"]) <= set(ids):
            raise ValueError("Acceptance row links unknown gap")
        if any(number not in prs or prs[number]["state"] != "MERGED" or not prs[number]["in_campaign"] for number in record["prs"]):
            raise ValueError("Referenced PR is not verified campaign integration")
        if record["commits"] != [prs[number]["mergeCommit"]["oid"] for number in record["prs"]]:
            raise ValueError("Acceptance PR/merge-commit provenance drift")
        for reference in record["implementation_paths"] + record["unit_tests"] + record["simulator_tests"]:
            local_ref(reference)
    issues = load(DATA / "issues.json") if issues is None else issues
    urls = [issue["url"] for issue in issues["issues"]]
    if len(urls) != len(set(urls)):
        raise ValueError("Duplicate issue inventory record")
    if not {89, 123, 124, 125, 126, 127, 129, 130, 144, 193} <= {
            issue["number"] for issue in issues["issues"] if issue["repository"] == "integratedmodelling/klab-services"}:
        raise ValueError("Historical discovery seed omitted")
    for issue in issues["issues"]:
        if not set(issue["java_gap_ids"]) <= set(ids) or not set(issue["requirement_ids"]) <= set(original):
            raise ValueError("Issue traceability is invalid")
        if issue["closure_candidate"]:
            evidence = issue.get("closure_evidence", {})
            eligible = issue["disposition"] in ("FIXED_IN_PYTHON", "FIXED_IN_REAL_JAVA", "DUPLICATE_OR_SUPERSEDED", "SIMULATOR_ONLY")
            if (issue["state"] != "OPEN" or not eligible
                    or evidence.get("all_original_conditions_accounted") is not True
                    or evidence.get("policy_authorized") is not True
                    or not evidence.get("proof_refs")
                    or (issue["disposition"] == "DUPLICATE_OR_SUPERSEDED" and not evidence.get("canonical_successor"))):
                raise ValueError("No fully justified closure candidate evidence; preserve the issue")
    return registry, audit, issues


def counts(items, field):
    return ", ".join(f"{key}: {value}" for key, value in sorted(Counter(i[field] for i in items).items()))


def handoff_inventory(registry):
    """Reproducible companion to canonical YAML; preserve the supplied narrative."""
    gaps = registry["gaps"]
    totals = Counter(gap["java_status"] for gap in gaps)
    lines = [f"Registry entries: **{len(gaps)}**. Generated from expanded `gaps.yaml`; no fresh Java execution implied.", "",
        "|Canonical Java status|Count|", "|---|---|"]
    for status in ("CONFIRMED_DEFECT", "MISSING_API", "CONTRACT_PROVISIONAL", "JAVA_FIXED_VERIFIED", "NOT_TESTED"):
        lines.append(f"|{status}|{totals[status]}|")
    lines += ["", "Unknown endpoint/build/first observation is **UNKNOWN_PENDING_REFERENCE**, not an invented API or date.", "",
        "|Stable gap / severity / owner|Service / method / endpoint / schemas|Roadmap / issues|Observed / simulated contract and baseline|Executable scenario / tests / evidence|Java acceptance condition|",
        "|---|---|---|---|---|---|"]
    def cell(value):
        return str(value).replace("|", "&#124;").replace("\n", "<br>")
    def refs(values):
        return "<br>".join(f"[{cell(value)}]({value if value.startswith('https://') else '../../' + value.split('::')[0]})" for value in values) or "None recorded"
    for gap in gaps:
        api = gap["api_contract"]
        baseline = gap["source_build_or_commit"] or "UNKNOWN_PENDING_REFERENCE"
        route = f"{api['method'] or 'UNKNOWN_PENDING_REFERENCE'} {api['path'] or 'UNKNOWN_PENDING_REFERENCE'}"
        command = f"`python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap {gap['id']} --output result.json`"
        columns = [f"{gap['id']} — {gap['title']}<br>{gap['priority']} / {gap['java_owner']}<br>{gap['java_status']} / {gap['contract_confidence']}",
            f"{gap['subsystem']} / {gap['java_component']}<br>{route}<br>request: {api['request_schema_ref'] or 'UNKNOWN_PENDING_REFERENCE'}<br>response: {api['expected_response_schema_ref'] or 'UNKNOWN_PENDING_REFERENCE'}",
            ', '.join(gap['requirement_ids']) + '<br>' + refs(gap['original_issues']),
            f"Observed: {gap['observed_java_behavior']}<br>Substitute: {gap['simulator_behavior']}<br>Baseline: {baseline}<br>Current: {gap['current_real_result']}; historical: {gap['historical_real_result']}<br>Verified {gap['last_verified']}: {gap['last_verified_basis']}",
            f"{gap['contract_case']}; {', '.join(gap['simulator_scenario_refs']) or 'Protocol guards only'}<br>{command}<br>{refs(gap['python_test_refs'])}<br>{refs([gap['reproducer_ref'], *gap['links']])}<br>{gap['notes']}",
            '<br>'.join(gap['resolution_criteria'])]
        lines.append('|' + '|'.join(cell(column) for column in columns) + '|')
    return '\n'.join(lines) + '\n'


def handoff_document(registry, text):
    if text.count(HANDOFF_BEGIN) != 1 or text.count(HANDOFF_END) != 1:
        raise ValueError("Handoff requires one generated-inventory marker pair")
    before, rest = text.split(HANDOFF_BEGIN)
    _, after = rest.split(HANDOFF_END)
    return before + HANDOFF_BEGIN + '\n' + handoff_inventory(registry) + HANDOFF_END + after


def outputs(registry, audit, issues):
    gaps = registry["gaps"]
    status = ["# Java gap status", "", "Generated by `govern.py generate`; do not edit manually.", "",
        counts(gaps, "java_status"), "", "**Current pinned Java execution: REAL_NOT_RUN for every gap.** Historical results remain separately attributed.", "",
        "|ID|Priority / native status|Contract confidence|Case / historical result|Original issues / requirements|",
        "|---|---|---|---|---|"]
    matrix = ["# Contract evidence matrix", "", "Generated from canonical registry. Simulation tests the substitute/guard, not Java resolution.", "",
        "|Gap / executable case|Runnable substitute tests|Current real|Historical real|Scope|", "|---|---|---|---|---|"]
    for gap in gaps:
        status.append(f"|{gap['id']} — {gap['title']}|{gap['priority']} / {gap['java_status']}|{gap['contract_confidence']}|{gap['contract_case']} / {gap['historical_real_result']}|{' '.join(gap['original_issues'])}; {', '.join(gap['requirement_ids'])}|")
        matrix.append(f"|{gap['id']} / {gap['contract_case']}|{'<br>'.join(gap['python_test_refs'])}|{gap['current_real_result']}|{gap['historical_real_result']}|{gap['simulator_behavior']}|")
    reconciliation = ["# Issue reconciliation", "", "Generated from fresh reviewed public snapshots; actions are recorded separately in `issue-actions.json`.", "",
        counts(issues["issues"], "disposition"), "", "**No safe closure candidates. No Java issue is fixed by simulation.**", "",
        "|Issue|Snapshot / owner|Disposition|Requirements / gaps|Action and missing acceptance|", "|---|---|---|---|---|"]
    actions = load(DATA / "issue-actions.json")["actions"]
    for issue in issues["issues"]:
        action = next((a["comment_url"] for a in actions if a["issue_url"] == issue["url"]), "NO_REMOTE_CHANGE")
        reconciliation.append(f"|[{issue['repository']}#{issue['number']}]({issue['url']}) — {issue['title']}|{issue['state']} / {issue['author']}|{issue['disposition']}|{', '.join(issue['requirement_ids'])}; {', '.join(issue['java_gap_ids'])}|{action}; {issue['missing_acceptance']}|")
    rows = ["# Independent A+B row audit", "", "Generated from `acceptance-audit.json`; original wording is authoritative.", "",
        counts(audit["records"], "implementation_status"), "", counts(audit["records"], "acceptance_status"), "",
        "No new real-Java or scientific execution is claimed. Referenced functional tests/historical receipts are not current executions.", "",
        "|ID / exact original requirement|Implementation / acceptance / confidence|Public API / source|Separate tests / PR delivery|Missing acceptance / gaps|", "|---|---|---|---|---|"]
    for row in audit["records"]:
        tests = f"unit:{len(row['unit_tests'])}; HTTP sim:{len(row['simulator_tests'])}; historical/real refs:{len(row['real_java_tests'])}; science refs:{len(row['science_tests'])}"
        rows.append(f"|{row['acceptance_id']} — {row['original_requirement']}|{row['implementation_status']} / {row['acceptance_status']} / {row['contract_confidence']}|{row['public_api']}<br>{'<br>'.join(row['implementation_paths'])}|{tests}; PRs {','.join(map(str, row['prs']))}; target {row['merge_target']}|{row['missing_work']}; {','.join(row['java_gap_ids'])}|")
    return {"JAVA_GAP_STATUS.md": status, "CONTRACT_MATRIX.md": matrix,
            "ISSUE_RECONCILIATION.md": reconciliation, "ACCEPTANCE_AUDIT.md": rows}


def generate(check=False):
    registry, audit, issues = validate()
    documents = {name: "\n".join(lines) + "\n" for name, lines in outputs(registry, audit, issues).items()}
    handoff = DATA / "JAVA_GAPS_FOR_FERDINANDO.md"
    documents[handoff.name] = handoff_document(registry, handoff.read_text(encoding="utf-8"))
    for name, text in documents.items():
        path = DATA / name
        if check:
            if not path.is_file() or path.read_text(encoding="utf-8") != text:
                raise ValueError("Generated governance report drift: " + name)
        else:
            path.write_text(text, encoding="utf-8")


def contracts(mode, output, selected=None):
    registry, _, _ = validate()
    gaps = registry["gaps"]
    if selected:
        if not set(selected) <= {g["id"] for g in gaps}:
            raise ValueError("Unknown selected gap")
        gaps = [gap for gap in gaps if gap["id"] in selected]
    result = {"mode": mode, "fallbacks": 0, "science": "NOT_RUN", "cases": []}
    if mode == "real":
        for gap in gaps:
            result["cases"].append({"gap_id": gap["id"], "case_id": gap["contract_case"], "tag": "REAL_NOT_RUN",
                "reason": "No reviewed pinned real adapter/fixture/authorization; no network operation attempted"})
        code = 3
    else:
        references = sorted({reference for gap in gaps for reference in gap["python_test_refs"]})
        # The SDK and simulator intentionally have independent conftest modules.
        # Isolate their pytest import namespaces instead of editing either suite.
        groups = {}
        for reference in references:
            group = "sdk" if reference.startswith("python-client/") else "simulator"
            groups.setdefault(group, []).append(reference)
        outcomes = {}
        logs = []
        with tempfile.TemporaryDirectory(prefix="klab-gap-cases-", dir=output.parent) as directory:
            for group, refs in groups.items():
                xml = Path(directory) / (group + ".xml")
                command = [sys.executable, "-m", "pytest", "-q", *refs, "--junitxml=" + str(xml)]
                completed = subprocess.run(command, cwd=ROOT, timeout=600, capture_output=True,
                    text=True, encoding="utf-8", errors="replace", creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                # Explicit pipes preserve hidden Windows child diagnostics.
                logs.append(getattr(completed, "stdout", "") + getattr(completed, "stderr", ""))
                counts = {key: 0 for key in ("tests", "skipped", "failures", "errors")}
                if xml.is_file():
                    suites = list(ET.parse(xml).getroot())
                    counts = {key: sum(int(s.attrib.get(key, 0)) for s in suites) for key in counts}
                else:
                    counts["errors"] = 1
                failed = bool(completed.returncode or any(counts[key] for key in ("skipped", "failures", "errors")))
                outcomes[group] = {"exit": completed.returncode, "failed": failed, **counts}
            output.with_suffix(".pytest.log").write_text("\n".join(logs), encoding="utf-8")
            code = int(any(group["failed"] for group in outcomes.values()))
            if code:
                print("\n".join(logs)[-8000:])
            for gap in gaps:
                gap_failed = any(outcomes["sdk" if ref.startswith("python-client/") else "simulator"]["failed"]
                                 for ref in gap["python_test_refs"])
                result["cases"].append({"gap_id": gap["id"], "case_id": gap["contract_case"],
                    "tag": "SIMULATED_PASS" if not gap_failed else None, "status": "FAIL" if gap_failed else "PASS", "current_real": "REAL_NOT_RUN",
                    "assertion_scope": gap["simulator_behavior"], "pytest_refs": gap["python_test_refs"]})
            result["groups"] = outcomes
            result["assertion_kind"] = "Explicit HTTP substitutes and client protocol/refusal guards, not missing Java capability acceptance"
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return code


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "generate", "contracts"))
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--mode", choices=("simulated", "real"), default="simulated")
    parser.add_argument("--gap", action="append")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    if args.command == "contracts":
        if args.output is None:
            parser.error("Contracts require an explicit output path")
        return contracts(args.mode, args.output, args.gap)
    if args.command == "generate":
        generate(args.check)
    else:
        registry, audit, issues = validate()
        print(json.dumps({"gaps": len(registry["gaps"]), "acceptance_rows": len(audit["records"]),
            "issues": len(issues["issues"]), "current_real_execution": "NOT_RUN"}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
