"""Repository-only Java gap governance. Never import the SDK or contact a service."""
import argparse
import ast
from collections import Counter
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import platform
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
        for field in ("native_status", "simulator_status", "native_execution_status"):
            if field not in gap:
                raise ValueError("Missing independent status dimension: " + field)
        if gap["native_execution_status"] != gap["current_real_result"]:
            raise ValueError("Native execution status disagrees with retained execution history")
        if gap["simulator_status"] == "SIMULATOR_NOT_IMPLEMENTED" and not gap.get("simulator_not_implemented_reason"):
            raise ValueError("Unimplemented simulator requires an exact reason")
        if gap["native_status"] == "NATIVE_WORKS_VERIFIED" and gap["native_execution_status"] != "REAL_PASS":
            raise ValueError("Verified native contract requires actual native pass")
        if gap["native_execution_status"] != "REAL_NOT_RUN":
            evidence = gap.get("native_execution_evidence", {})
            if (evidence.get("kind") != "NATIVE_EXECUTION" or not all(evidence.get(field)
                    for field in ("build", "environment", "timestamp_utc", "receipt_ref"))):
                raise ValueError("Non-NOT_RUN native result requires build/environment/time/native execution receipt")
            local_ref(evidence["receipt_ref"])
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
    if issues.get("current_campaign_review_ref"):
        current = load(local_ref(issues["current_campaign_review_ref"]))["issues"]
        if len({row["url"] for row in current}) != len(current):
            raise ValueError("Duplicate current campaign issue")
        for row in current:
            if (row["url"] not in urls or row["disposition"] not in (
                    "FIXED_REAL", "FIXED_PYTHON", "TRACKING_MIGRATED", "OPEN_JAVA_REQUIRED", "OPEN_OTHER")
                    or not set(row.get("java_gap_ids", [])) <= set(ids)):
                raise ValueError("Invalid current campaign issue traceability")
            if row["closure_eligible"]:
                raise ValueError("Current campaign closure requires reviewed complete migration evidence")
    return registry, audit, issues


def canonical_check(registry, text=None):
    """Validate the human-authored canonical file; never regenerate its prose."""
    path = DATA / "JAVA_GAPS_FOR_FERDINANDO.md"
    text = path.read_text(encoding="utf-8") if text is None else text
    headings = re.findall(r"^### (JAVA-GAP-[0-9]{4})$", text, re.M)
    if Counter(headings) != Counter(gap["id"] for gap in registry["gaps"]):
        raise ValueError("Canonical Markdown IDs must match the complete register exactly once")
    sections = dict(re.findall(r"^### (JAVA-GAP-[0-9]{4})\n(.*?)(?=^### JAVA-GAP-|^## |\Z)", text, re.M | re.S))
    labels = {"native_status": "Native status", "simulator_status": "Simulator status",
              "native_execution_status": "Native execution status"}
    summary_counts = dict((status, int(count)) for status, count in
                          re.findall(r"^\|(CONFIRMED_DEFECT|CONFIRMED_MISSING_API|CONTRACT_PROVISIONAL|NOT_TESTED|NATIVE_WORKS_VERIFIED|RESOLVED_SETUP|SUPERSEDED)\|([0-9]+)\|$", text, re.M))
    expected_counts = Counter(gap["native_status"] for gap in registry["gaps"])
    if not summary_counts or any(expected_counts[key] != value for key, value in summary_counts.items()) or any(key not in summary_counts for key in expected_counts):
        raise ValueError("Canonical native summary counts drift")
    for gap in registry["gaps"]:
        section = sections[gap["id"]]
        if "**Title:** " + gap["title"] + "\n" not in section:
            raise ValueError("Canonical title drift: " + gap["id"])
        for field, label in labels.items():
            values = re.findall(r"\*\*" + label + r":\*\* ([A-Z_]+)", section)
            if values != [gap[field]]:
                raise ValueError("Canonical status drift: " + gap["id"] + " " + field)
        for requirement in gap["requirement_ids"]:
            if requirement not in section:
                raise ValueError("Canonical requirement omitted: " + requirement)
        for issue in gap["original_issues"]:
            if issue not in section:
                raise ValueError("Canonical issue omitted: " + issue)
        if "--gap " + gap["id"] not in section or "Native blocker:" not in section:
            raise ValueError("Canonical executable command/blocker omitted: " + gap["id"])
    anchors = {re.sub(r"[^\w\- ]", "", heading.lower()).replace(" ", "-")
               for heading in re.findall(r"^#{1,6} (.+)$", text, re.M)}
    for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
        if target.startswith("https://"):
            continue
        if target.startswith("#"):
            if target[1:] not in anchors:
                raise ValueError("Missing canonical anchor: " + target)
        else:
            resolved = (path.parent / target.split("#")[0]).resolve()
            resolved.relative_to(ROOT)
            if not resolved.is_file():
                raise ValueError("Broken canonical relative link: " + target)
    for ref in re.findall(r"`([^`\n]*\.py(?:::[^`\n ]+)?)`", text):
        if ref.startswith(("python-client/", "python-worker/", "developer-tools/")):
            test_ref(ref)
    if re.search(r"[A-Za-z]:[\\/]Users[\\/]|Bearer [A-Za-z0-9._-]{12,}", text):
        raise ValueError("Machine-private path or credential in canonical instructions")
    live_path = DATA / "live-issue-review.json"
    if live_path.is_file():
        for issue in load(live_path)["issues"]:
            pattern = (r"\]\(" + re.escape(issue["url"]) + r"\) — "
                       + re.escape(issue["title"]) + r"\|" + issue["state"] + r"\|")
            if not re.search(pattern, text):
                raise ValueError("Canonical live issue title/state/link drift: " + issue["url"])
    return {"canonical_records": len(headings)}


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
    campaign = load(local_ref(issues["current_campaign_review_ref"])) if issues.get("current_campaign_review_ref") else {"issues": []}
    current = {issue["url"]: issue for issue in campaign["issues"]}
    reconciliation = ["# Issue reconciliation", "", "Generated from reviewed public snapshots; actions are recorded separately in `issue-actions.json`.", "",
        counts(issues["issues"], "disposition"), "", "**No safe closure candidates. No Java issue is fixed by simulation.**", "",
        "|Issue|Snapshot / owner|Disposition|Requirements / gaps|Action and missing acceptance|", "|---|---|---|---|---|"]
    if current:
        reconciliation[4:4] = ["Current full-roadmap open issue review: " + counts(campaign["issues"], "disposition"),
            "C is now in scope. Original audit dispositions/conditions below remain attributed history; current classifications override prospective exclusions.", ""]
    actions = load(DATA / "issue-actions.json")["actions"]
    for issue in issues["issues"]:
        action = "<br>".join(a["comment_url"] for a in actions if a["issue_url"] == issue["url"]) or "NO_REMOTE_CHANGE"
        review = current.get(issue["url"], {})
        disposition = review.get("disposition", issue["disposition"])
        gap_ids = sorted(set(issue["java_gap_ids"]) | set(review.get("java_gap_ids", [])))
        missing = review.get("reason", issue["missing_acceptance"])
        reconciliation.append(f"|[{issue['repository']}#{issue['number']}]({issue['url']}) — {issue['title']}|{issue['state']} / {issue['author']}|{disposition}|{', '.join(issue['requirement_ids'])}; {', '.join(gap_ids)}|{action}; {missing}|")
    rows = ["# Independent A+B row audit", "", "Generated from `acceptance-audit.json`; original wording is authoritative.", "",
        counts(audit["records"], "implementation_status"), "", counts(audit["records"], "acceptance_status"), "",
        "No new real-Java or scientific execution is claimed. Referenced functional tests/historical receipts are not current executions.", "",
        "|ID / exact original requirement|Implementation / acceptance / confidence|Public API / source|Separate tests / PR delivery|Missing acceptance / gaps|", "|---|---|---|---|---|"]
    for row in audit["records"]:
        tests = f"unit:{len(row['unit_tests'])}; HTTP sim:{len(row['simulator_tests'])}; historical/real refs:{len(row['real_java_tests'])}; science refs:{len(row['science_tests'])}"
        rows.append(f"|{row['acceptance_id']} — {row['original_requirement']}|{row['implementation_status']} / {row['acceptance_status']} / {row['contract_confidence']}|{row['public_api']}<br>{'<br>'.join(row['implementation_paths'])}|{tests}; PRs {','.join(map(str, row['prs']))}; target {row['merge_target']}|{row['missing_work']}; {','.join(row['java_gap_ids'])}|")
    return {"JAVA_GAP_STATUS.md": status, "CONTRACT_MATRIX.md": matrix,
            "ISSUE_RECONCILIATION.md": reconciliation, "ACCEPTANCE_AUDIT.md": rows}


def reproduction_document(gap):
    """Stable individual handoff generated only from attributed registry evidence."""
    contract = gap["api_contract"]
    lines = [f"# {gap['id']} — {gap['title']}", "",
        "Generated from `gaps.yaml`; simulator evidence is not native acceptance.", "",
        "## Classification and affected contract", "",
        f"- Java: **{gap['java_status']}**; current execution **{gap['current_real_result']}**; historical **{gap['historical_real_result']}**.",
        f"- Service: {gap['subsystem']}; component: {gap['java_component']}.",
        f"- Original roadmap: {', '.join(gap['requirement_ids'])}.",
        f"- API: {contract['method'] or 'UNKNOWN_PENDING_REFERENCE'} {contract['path'] or 'UNKNOWN_PENDING_REFERENCE'}.",
        f"- Request/schema: {contract['request_schema_ref'] or 'UNKNOWN_PENDING_REFERENCE'}.",
        f"- Tested Java build/environment: {gap['source_build_or_commit'] or 'NOT_RUN / exact native build and environment not established'}.",
        f"- Evidence basis: {gap['last_verified_basis']}; last verified {gap['last_verified']}.", "",
        "## Expected behavior / native compatibility acceptance", ""]
    lines.extend(f"- {item}" for item in gap["resolution_criteria"])
    lines.extend(["", "## Actual observed Java behavior", "", gap["observed_java_behavior"], "",
        "## Independent simulator implementation and fixtures", "", gap["simulator_behavior"], "",
        "Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.",
        "Scenarios: " + ", ".join(gap["simulator_scenario_refs"]), "", "Exact executable controls:", ""])
    lines.extend(f"- `{reference}`" for reference in gap["python_test_refs"])
    lines.extend(["", "## Reproduction commands", "", "From repository root with the test extras installed:", "", "```powershell",
        f"python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap {gap['id']} --output simulated-{gap['id']}.json",
        f"python developer-tools/java-gap-governance/govern.py contracts --mode real --gap {gap['id']} --output real-{gap['id']}.json", "```", "",
        "The real-mode command intentionally records REAL_NOT_RUN and exits3 without network calls.",
        "It is an executable missing-adapter disposition, **not** an actual native test or a simulator fallback.", "",
        "## Required native startup prerequisites and executable test plan", "",
        "1. Obtain authorization for an owned disposable native fixture and its cleanup policy; never reuse unrelated Jobs.",
        "2. Pin service/component build hashes, endpoint identities, principal roles, worldview and fixture revisions; consult MODELER_IDE_STATE.md and run its bounded read-only gates only for this live-dependent work.",
        "3. Start the maintained service tuple using the operator's supported procedure. Python-only development does not authorize IDE/service repair.",
        "4. Supply a reviewed bounded native adapter for the exact contract and run the linked public Python workflow with explicit real endpoints, no automatic retries or simulation fallback.",
        "5. Capture request/response/error, positive/negative authorization, independent scientific checks where relevant and owned cleanup; compare with the acceptance criteria above.",
        "6. If no reviewed adapter/build/permission exists, retain NOT_RUN. Do not invent native endpoints or turn simulator success into a Java fix.", "",
        "Existing native test references: " + (", ".join(gap["real_java_test_refs"]) or "none reviewed for this complete gap"), "",
        "## Custody, related trackers and remaining questions", "", f"Original reproducer/evidence: {gap['reproducer_ref']}", "", gap["notes"], ""])
    lines.extend(f"- {reference}" for reference in dict.fromkeys([*gap["original_issues"], *gap["links"]]))
    return "\n".join(lines) + "\n"


def generate(check=False):
    registry, audit, issues = validate()
    canonical_check(registry)
    documents = {name: "\n".join(lines) + "\n" for name, lines in outputs(registry, audit, issues).items()}
    # Old generated summaries are supporting pointers, not competing master lists.
    for name in ("JAVA_GAP_STATUS.md", "CONTRACT_MATRIX.md", "ISSUE_RECONCILIATION.md"):
        documents[name] = ("# Supporting evidence pointer\n\n"
                           "The canonical human-facing inventory, contracts, reproductions and issue crosswalk are in\n"
                           "[JAVA_GAPS_FOR_FERDINANDO.md](JAVA_GAPS_FOR_FERDINANDO.md).\n\n"
                           "Historical versions remain available in Git history; machine evidence remains in the adjacent JSON/YAML indexes.\n")
    documents.update({"reproductions/" + gap["id"] + ".md": reproduction_document(gap) for gap in registry["gaps"]})
    for name, text in documents.items():
        path = DATA / name
        if check:
            if not path.is_file() or path.read_text(encoding="utf-8") != text:
                raise ValueError("Generated governance report drift: " + name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")


def repository_commit():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0)).strip()


def contracts(mode, output, selected=None):
    if output.exists() or output.with_suffix('.pytest.log').exists():
        raise FileExistsError('Choose an unused result/log path; evidence is never overwritten')
    registry, _, _ = validate()
    gaps = registry["gaps"]
    if selected:
        if not set(selected) <= {g["id"] for g in gaps}:
            raise ValueError("Unknown selected gap")
        gaps = [gap for gap in gaps if gap["id"] in selected]
    result = {"mode": mode, "fallbacks": 0, "science": "NOT_RUN", "cases": [],
              "started_utc": datetime.now(timezone.utc).isoformat(),
              "repository_commit": repository_commit(),
              "python": platform.python_version(), "platform": sys.platform,
              "govern_sha256_lf_utf8": hashlib.sha256(Path(__file__).read_text(encoding='utf-8').encode('utf-8')).hexdigest()}
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
        def test_group(reference):
            if reference.startswith("python-client/"):
                return "sdk"
            return "worker" if reference.startswith("python-worker/") else "simulator"
        for reference in references:
            group = test_group(reference)
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
                failed = bool(completed.returncode or not counts["tests"] or any(counts[key] for key in ("skipped", "failures", "errors")))
                outcomes[group] = {"exit": completed.returncode, "failed": failed, **counts}
            output.with_suffix(".pytest.log").write_text("\n".join(logs), encoding="utf-8")
            code = int(any(group["failed"] for group in outcomes.values()))
            if code:
                print("\n".join(logs)[-8000:])
            for gap in gaps:
                gap_failed = any(outcomes[test_group(ref)]["failed"]
                                 for ref in gap["python_test_refs"])
                result["cases"].append({"gap_id": gap["id"], "case_id": gap["contract_case"],
                    "tag": ("SIMULATOR_NOT_IMPLEMENTED" if gap["simulator_status"] == "SIMULATOR_NOT_IMPLEMENTED"
                            else "SIMULATED_PASS") if not gap_failed else None,
                    "status": "FAIL" if gap_failed else "PASS", "current_real": "REAL_NOT_RUN",
                    "assertion_scope": gap["simulator_behavior"], "pytest_refs": gap["python_test_refs"]})
            result["groups"] = outcomes
            result["assertion_kind"] = "Explicit HTTP substitutes and client protocol/refusal guards, not missing Java capability acceptance"
    result["finished_utc"] = datetime.now(timezone.utc).isoformat()
    with output.open('x', encoding='utf-8') as stream:
        stream.write(json.dumps(result, indent=2) + "\n")
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
        canonical_check(registry)
        print(json.dumps({"gaps": len(registry["gaps"]), "acceptance_rows": len(audit["records"]),
            "issues": len(issues["issues"]), "current_real_execution": "NOT_RUN"}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
