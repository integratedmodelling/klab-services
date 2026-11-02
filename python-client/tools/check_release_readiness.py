"""Validate bounded candidate-bound release evidence; never publish or certify science."""
import argparse
import hashlib
import json
from pathlib import Path
import re

GATES = ("offline", "coverage", "core-package", "security-failure", "owned-lifecycle",
         "scientific-reference", "scale", "server-compatibility")
STATUSES = ("PASS", "FAIL", "UNKNOWN", "BLOCKED", "NOT_RUN")
SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"[0-9a-f]{64}\Z")


class ReadinessError(ValueError):
    """Invalid or mismatched local evidence, not a remote-service error."""


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ReadinessError("Duplicate manifest keys are ambiguous")
        result[key] = value
    return result


def _artifact(reference, root):
    if (not isinstance(reference, dict) or set(reference) != {"path", "sha256"}
            or not isinstance(reference["path"], str) or not isinstance(reference["sha256"], str)
            or not DIGEST.fullmatch(reference["sha256"])):
        raise ReadinessError("Evidence requires a relative path and SHA-256")
    relative = reference["path"]
    if (len(relative) > 1024 or not re.fullmatch(r"[A-Za-z0-9_./-]+", relative)
            or relative.startswith("/") or any(part in ("", ".", "..") for part in relative.split("/"))):
        raise ReadinessError("Evidence path must remain inside its explicitly supplied root")
    path = root
    for part in relative.split("/"):
        path = path / part
        if path.is_symlink():
            raise ReadinessError("Evidence symlinks are not accepted")
    try:
        path.resolve(strict=True).relative_to(root)
        if not path.is_file() or path.stat().st_size > 8 * 1024 * 1024:
            raise ReadinessError("Evidence must be a regular file within the 8 MiB budget")
        digest = hashlib.sha256()
        size = 0
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(65536), b""):
                size += len(chunk)
                if size > 8 * 1024 * 1024:
                    raise ReadinessError("Evidence grew beyond its 8 MiB byte budget")
                digest.update(chunk)
    except (OSError, ValueError, RuntimeError) as error:
        if isinstance(error, ReadinessError):
            raise
        raise ReadinessError("Evidence is absent, inaccessible or outside the approved root") from None
    if digest.hexdigest() != reference["sha256"]:
        raise ReadinessError("Evidence digest differs from its recorded bytes")


def assess(manifest, evidence_root, candidate):
    """Return only gate summaries; files/hashes prove identity, not truth of claims.

    Human review must independently interpret logs, compatibility and scientific
    reference contracts. An operator-authored PASS is not an attestation.
    """
    if not isinstance(candidate, str) or not SHA.fullmatch(candidate):
        raise ReadinessError("Candidate must be an explicit full Git SHA-1")
    if (not isinstance(manifest, dict) or set(manifest) != {"version", "candidate", "checks"}
            or type(manifest["version"]) is not int or manifest["version"] != 1
            or manifest["candidate"] != candidate or not isinstance(manifest["checks"], list)
            or len(manifest["checks"]) > len(GATES)):
        raise ReadinessError("Manifest must be version 1, bounded and bound to the requested candidate")
    try:
        root = Path(evidence_root).resolve(strict=True)
        if not root.is_dir():
            raise ReadinessError("Evidence root must be an explicitly supplied directory")
    except (OSError, RuntimeError):
        raise ReadinessError("Evidence root is absent or inaccessible") from None
    checks = {}
    for check in manifest["checks"]:
        if (not isinstance(check, dict) or not {"gate", "status", "candidate"} <= set(check)
                or set(check) - {"gate", "status", "candidate", "artifact"}
                or not isinstance(check["gate"], str) or check["gate"] not in GATES
                or check["gate"] in checks or check["status"] not in STATUSES
                or check["candidate"] != candidate):
            raise ReadinessError("Checks require unique supported gates, explicit states and exact candidate identity")
        if check["status"] == "PASS" and "artifact" not in check:
            raise ReadinessError("A claimed passing gate requires preserved byte-pinned evidence")
        if "artifact" in check:
            _artifact(check["artifact"], root)
        checks[check["gate"]] = {"gate": check["gate"], "status": check["status"],
                                  "evidence_verified": "artifact" in check}
    summaries = [checks.get(gate, {"gate": gate, "status": "UNKNOWN", "evidence_verified": False}) for gate in GATES]
    return {"candidate": candidate, "eligible_for_review": all(c["status"] == "PASS" for c in summaries),
            "publication_authorized": False, "checks": summaries,
            "limit": "File identity and supplied states only; human evidence/scientific review and maintainer release authorization still required"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--evidence-root", required=True, type=Path)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args(argv)
    try:
        if args.manifest.stat().st_size > 256 * 1024:
            raise ReadinessError("Manifest exceeded its 256 KiB budget")
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"), object_pairs_hook=_unique)
        result = assess(manifest, args.evidence_root, args.candidate)
    except (OSError, ValueError, RecursionError):
        # Never echo source/log contents, paths or private exception text.
        print(json.dumps({"eligible_for_review": False, "publication_authorized": False,
                          "error": "Invalid, unavailable or mismatched candidate evidence"}))
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0 if result["eligible_for_review"] else 3


if __name__ == "__main__":
    raise SystemExit(main())
