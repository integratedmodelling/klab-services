"""Thin JSON contracts and receipts around existing owned acceptance runners.

Validation is L1 evidence, never scientific acceptance. No SDK or service imports.
"""
import hashlib
import json
from pathlib import Path
import re

STATUSES = {"PASS", "FAIL", "BLOCKED", "NOT_RUN", "SKIPPED", "NOT_APPLICABLE"}
AXES = ("python", "java_contract", "scientific", "security_lifecycle", "environment_artifacts")
OPERATIONS = {"nonuniform-export", "request-plan"}


class ScenarioError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ScenarioError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def local_file(root, relative):
    require(isinstance(relative, str) and relative and "\\" not in relative,
            "Expected repository-relative POSIX file path")
    path = Path(relative)
    require(not path.is_absolute() and ":" not in relative and ".." not in path.parts,
            "Unsafe evidence/input path")
    root = Path(root).resolve()
    target = (root / path).resolve()
    require(target.is_relative_to(root) and target.is_file(), "Missing or escaping input file")
    require(target.stat().st_size <= 2 * 1024 * 1024, "Input byte budget exceeded")
    return target


def load(path):
    def unique(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value, "Duplicate JSON key")
            value[key] = item
        return value
    require(Path(path).stat().st_size <= 2 * 1024 * 1024, "JSON byte budget exceeded")
    return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique,
                      parse_constant=lambda _: (_ for _ in ()).throw(ScenarioError("Nonfinite JSON")))


def _validate(manifest, root):
    require(type(manifest.get("schema_version")) is int and manifest["schema_version"] == 1,
            "Unsupported scenario schema")
    require(isinstance(manifest.get("id"), str) and re.fullmatch(r"[a-z][a-z0-9-]{1,79}", manifest["id"]),
            "Invalid scenario ID")
    require(isinstance(manifest.get("name"), str) and manifest["name"].strip(), "Scientific question required")
    require(manifest.get("claim_level") in ("L4", "L5"), "Expected scientific claim level")
    capabilities = manifest.get("capabilities")
    require(isinstance(capabilities, list) and capabilities and len(set(capabilities)) == len(capabilities)
            and all(isinstance(c, str) and re.fullmatch(r"(?:CAP-\d{2}|RC-[A-Z][0-9](?:\.[0-9])?)", c)
                    for c in capabilities), "Explicit capability IDs required")
    source = manifest.get("source", {})
    require(isinstance(source.get("publication_or_contract"), str)
            and source["publication_or_contract"].strip() not in ("", "NOT_VERIFIED", "UNKNOWN"),
            "Independent reference required")
    require(isinstance(source.get("source_revision"), str) and source["source_revision"], "Source revision required")
    if manifest["claim_level"] == "L5":
        require(source["source_revision"] != "NOT_VERIFIED" and source.get("original_study_verified") is True,
                "L5 needs verified original-study source")
    fixture = manifest.get("fixture", {})
    require(fixture.get("oracle_origin") == "independently-authored-reference", "Independent oracle required")
    for key in ("expected_units", "crs", "temporal_domain"):
        require(isinstance(fixture.get(key), str) and fixture[key].strip(), "Explicit units/CRS/time required")
    files = fixture.get("input_files")
    require(isinstance(files, list) and 1 <= len(files) <= 32, "Bounded input files required")
    paths = set()
    for item in files:
        require(isinstance(item, dict) and isinstance(item.get("sha256"), str)
                and re.fullmatch(r"[a-f0-9]{64}", item["sha256"]), "Input hash required")
        path = local_file(root, item.get("path"))
        require(item["path"] not in paths and sha(path) == item["sha256"], "Duplicate or changed input")
        paths.add(item["path"])
    require(fixture.get("oracle_file") in paths, "Oracle must be a hashed input")
    stack = manifest.get("stack", {})
    require(all(stack.get(k) == "runtime-recorded" for k in
                ("java_revision", "worldview_component_ids", "python_wheel_sha256")), "No static runtime identities")
    execution = manifest.get("execution", {})
    require(execution.get("operation") in OPERATIONS, "Unsupported workflow adapter")
    require(execution.get("side_effect_policy") == "owned-resources-only", "Owned execution only")
    for key, limit in (("max_seconds", 1800), ("max_contexts", 4), ("max_jobs", 8), ("max_bytes", 2097152)):
        require(type(execution.get(key)) is int and 0 < execution[key] <= limit, "Invalid execution budget")
    checks = manifest.get("checks")
    require(isinstance(checks, list) and 1 <= len(checks) <= 40, "Checks required")
    ids = set()
    for check in checks:
        require(isinstance(check, dict) and isinstance(check.get("expectation_id"), str)
                and check["expectation_id"] and check["expectation_id"] not in ids, "Unique expectations required")
        require(check.get("type") in ("numerical", "semantic", "lifecycle", "security", "geographic", "metadata"),
                "Unknown check type")
        require(isinstance(check.get("tolerance_basis"), str) and check["tolerance_basis"].strip(),
                "Independent tolerance justification required")
        ids.add(check["expectation_id"])
    return manifest


def validate(manifest, root):
    try:
        return _validate(manifest, root)
    except (AttributeError, TypeError, KeyError, OSError) as error:
        raise ScenarioError("Malformed scenario or unavailable local input") from error


def outcome(status, reason):
    require(status in STATUSES and isinstance(reason, str) and reason.strip(), "Typed outcome and reason required")
    return {"status": status, "reason": reason}


def record(manifest, *, run_id, identities, command, started, ended, checks, axes, evidence, cleanup):
    """Only evaluated checks may pass; unavailable checks remain explicitly not run."""
    require(isinstance(run_id, str) and re.fullmatch(r"[a-zA-Z0-9-]{1,100}", run_id), "Invalid run ID")
    require(set(axes) == set(AXES), "All five ownership axes required")
    require(isinstance(command, list) and command and all(isinstance(s, str) for s in command), "Exact command required")
    require(isinstance(started, str) and isinstance(ended, str) and started <= ended, "Run timestamps required")
    for key in ("source_sha", "build_hashes", "wheel_sha256", "services", "auth_principal", "java_version", "components"):
        require(identities.get(key), "Missing runtime identity: " + key)
    require(re.fullmatch(r"[a-f0-9]{40}", identities["source_sha"])
            and re.fullmatch(r"[a-f0-9]{64}", identities["wheel_sha256"]), "Exact source/wheel hashes required")
    expected = {c["expectation_id"] for c in manifest["checks"]}
    require(set(checks) == expected, "Per-check outcomes must be complete")
    for result in [*checks.values(), *axes.values(), cleanup]:
        require(isinstance(result, dict), "Outcome object required")
        outcome(result.get("status"), result.get("reason"))
    require(isinstance(evidence, list) and evidence, "Sanitized evidence references required")
    for item in evidence:
        require(item.get("sanitized") is True and isinstance(item.get("sha256"), str)
                and re.fullmatch(r"[a-f0-9]{64}", item["sha256"]), "Sanitized evidence hash required")
    results = [*checks.values(), *axes.values(), cleanup]
    status = next((s for s in ("FAIL", "BLOCKED", "NOT_RUN", "SKIPPED")
                   if any(r["status"] == s for r in results)), "PASS")
    result = {"schema_version": 1, "scenario_id": manifest["id"], "run_id": run_id,
            "claim_level": manifest["claim_level"], "status": status,
            "oracle_origin": manifest["fixture"]["oracle_origin"], "source": manifest["source"],
            "identities": identities, "command": command, "started": started, "ended": ended,
            "checks": checks, "axes": axes, "evidence": evidence, "cleanup": cleanup}
    encoded = json.dumps(result, allow_nan=False)
    require(len(encoded.encode()) <= 2 * 1024 * 1024, "Record byte budget exceeded")
    require(not re.search(r'(?i)bearer\s+\S+|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', encoded),
            "Credential-shaped output refused")
    require(not re.search(r'(?i)"(?:token|authorization|password|secret|private_key)"\s*:', encoded),
            "Credential fields refused")
    return result
