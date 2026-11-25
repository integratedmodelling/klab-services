"""Prepare the one-time IMOD review migration in a separate directory."""

import argparse
import hashlib
import json
from pathlib import Path
import posixpath
import re
import subprocess
from datetime import datetime, timezone


OLD = "experiments/strawman-2026"
AUDITED = "8cbba12"
MARKDOWN_LINK = re.compile(r"(\]\()([^\s)#]+)([^)]*\))")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write(root, path, data):
    destination = root / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)


def encoded(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode()


def git(project, *args):
    return subprocess.check_output(
        ["git", "-c", f"safe.directory={project.as_posix()}", "-C", str(project), *args]
    )


def recorded_bytes(data, expected):
    for value in (data, data.replace(b"\r\n", b"\n"), data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")):
        if digest(value) == expected:
            return value
    raise ValueError(f"Could not reproduce recorded hash {expected}")


def path_target(kind, urn):
    if re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}", urn) and not urn.endswith("."):
        return f"{kind}--{urn}"
    return f"{kind}--sha256--{digest(urn.encode())}"


def pilot(project, output):
    files = {}
    society = f"{OLD}/review/society-omrse-20261122"
    validation = json.loads((project / society / "candidate-r2/validation.json").read_bytes())
    base_commit = validation["baselineCommit"]
    society_source = git(project, "show", f"{base_commit}:src/society.kwv")
    r1_hash = re.search(
        rb"society:\s*\n\s*resources_reasoner_sha256: ([0-9a-f]{64})",
        git(project, "show", f"HEAD:{society}/proposal.yaml"),
    )[1].decode()
    candidate_path = f"{society}/candidate-r2/src/society.kwv"
    hydro_commit = "608bef150ced0a109db98a5aad64ba4461beaa54"
    hydro_source = recorded_bytes(git(project, "show", f"{hydro_commit}:src/hydrology.kwv"),
                                  "1f96cc9f79d91835a31ee81eb8005148a3d1372fa444d8aa73f0d469cd25d1c2")
    hydro_candidate = git(project, "show", f"HEAD:{OLD}/candidate/src/hydrology.kwv")
    versions = [
        ("hydrology", f"{OLD}/review/{name}.yaml", hydro_source, hydro_candidate, "illustrative" if i == 2 else "historical")
        for i, name in enumerate(
            ("surface-catchment-r1", "surface-catchment-r2-review-example", "surface-catchment-r3-validation"), 1
        )
    ] + [
        ("society", f"{society}/proposal.yaml", recorded_bytes(society_source, r1_hash), None, "historical"),
        ("society", f"{society}/candidate-r2/proposal.yaml",
         recorded_bytes(society_source, validation["baselineInputSha256"]["society.kwv"]),
         recorded_bytes((project / candidate_path).read_bytes(), validation["candidateOverrides"]["society.kwv"]),
         "historical"),
    ]
    previous = None
    for urn, original, base, candidate, status in versions:
        proposal = git(project, "show", f"HEAD:{original}")
        revision = re.search(rb"(?m)^  revision_id: (.+)$", proposal)[1].strip().decode()
        parent = re.search(rb"(?m)^  supersedes_revision: (.+)$", proposal)[1].strip().decode()
        commit, timestamp, author = git(
            project, "log", "--follow", "-1", "--format=%H%n%aI%n%an", "--", original
        ).decode().splitlines()
        when = datetime.fromisoformat(timestamp).astimezone(timezone.utc)
        folder = f"review/proposals/{path_target('ontology', urn)}/{when:%Y%m%dT%H%M%SZ}--git-{digest(author.encode())[:12]}--{revision}"
        files[original] = f"{folder}/proposal.yaml"
        payloads = {"proposal.yaml": proposal}
        if base is not None:
            payloads["base.kwv"] = base
        if candidate is not None:
            payloads["candidate.kwv"] = candidate
            if urn == "society":
                files[candidate_path] = f"{folder}/candidate.kwv"
        manifest = {
            "version": 1, "kind": "proposal",
            "target": {"project": "imod", "kind": "ontology", "urn": urn},
            "id": revision, "contributor": author,
            "created_at": when.isoformat().replace("+00:00", "Z"),
            "origin": {"commit": commit, "path": original, "attribution": "git-author"},
            "status": status, "payload_schema": "domain-context-proposal/1.3",
            "base": {"state": "snapshot", "path": f"src/{urn}.kwv",
                     "revision": base_commit if urn == "society" else hydro_commit, "file": "base.kwv"}
            if base is not None else {"state": "unavailable", "reason": "No proposal-specific original source hash was recovered"},
            "inputs": [previous] if parent != "null" else [],
            "files": {name: {"sha256": digest(data), "size": len(data)} for name, data in payloads.items()},
        }
        for name, data in payloads.items():
            write(output, f"{folder}/{name}", data)
        manifest_bytes = encoded(manifest)
        previous = {"manifest": f"{folder}/manifest.json", "sha256": digest(manifest_bytes)}
        write(output, previous["manifest"], manifest_bytes)
    return files


def migrate(project, output, mapping):
    project_key = "project--imod"
    retired = git(project, "ls-files", "-z", "--", OLD, "docs/reviews", "docs/decisions", "DECISIONS.md")
    archived = {}
    for source in retired.decode().strip("\0").split("\0"):
        if source in mapping:
            continue
        parts = source.split("/")
        leaf = parts[-1]
        if source.startswith(f"{OLD}/bootstrap/"):
            relative = parts[3:]
            if len(relative) == 2 and leaf in {
                "BOOTSTRAP.md", "dossier.json", "QUESTIONS_FIRST.md", "QUESTIONS_SOURCE_FIRST.md",
                "QUESTION_ORIGIN.json", "QUESTIONS_FIRST.json", "questions-source-first.json",
                "backend-dossier.sample.json",
            }:
                target = f"review/bootstrap/ontology--{relative[0]}/{leaf}"
            elif leaf in {"README.md", "METHOD.md", "BACKEND_MAPPING.md", "dossier.schema.json",
                          "dossier.template.json", "domain-index.json", "ROOT_PREREQUISITES.md",
                          "COVERAGE_DASHBOARD.md", "CROSS_DOMAIN_REVIEW_CORRECTIONS.md",
                          "REVIEW_FINDINGS.md", "REVIEW_RESPONSES.md", "LAND_AGRICULTURE_REVISION.md",
                          "land-agriculture-dispositions.json", "WILDFIRE_REVIEW_PATH.md",
                          "INSTRUMENTATION_HANDOFF.md"} and len(relative) == 1:
                target = f"review/bootstrap/{project_key}/{leaf}"
            elif leaf in {"review-context.json", "coverage-validation.json", "question-parser-results.json",
                          "land-agriculture-review-context.json", "land-agriculture-validation.json",
                          "review-probes.json", "VALIDATION.md", "backend-projection-validation.txt"}:
                target = f"review/evidence/{project_key}/bootstrap-validation/{'/'.join(relative)}"
            else:
                archived[source] = "Superseded diagnostic, negative control, or machine-specific replay artifact"
                continue
        elif source.startswith(f"{OLD}/evidence/"):
            if leaf not in {"baseline.json", "source-inventory.json", "SOURCE_INVENTORY.md",
                            "baseline-adaptation.txt", "overlay-adaptation.txt", "proposal-validation.txt",
                            "reactor-assessment-provenance.json", "workflow-assessment-provenance.json"}:
                archived[source] = "Historical raw diagnostic, duplicate report, or attributed local patch"
                continue
            target = f"review/evidence/{project_key}/strawman-source-and-validation/{leaf}"
        elif source.startswith(f"{OLD}/tools/") or leaf == ".gitattributes" or leaf == "domain-context-proposal.schema.json":
            archived[source] = "Historical experiment tooling or duplicated configuration"
            continue
        elif source.startswith(f"{OLD}/review/society-omrse-20261122/"):
            target = f"review/evidence/ontology--society/omrse-20261122/{'/'.join(parts[4:])}"
        elif source.startswith(f"{OLD}/candidate/"):
            target = f"review/evidence/ontology--hydrology/surface-catchment-overlay/{'/'.join(parts[3:])}"
        elif source.startswith(f"{OLD}/review/water-chemical-alias-"):
            target = f"review/evidence/ontology--chemistry/{'/'.join(parts[3:])}"
        elif source.startswith(f"{OLD}/"):
            target = f"review/evidence/{project_key}/strawman/{'/'.join(parts[2:])}"
        elif source.startswith("docs/reviews/"):
            target = f"review/evidence/{project_key}/{source.removeprefix('docs/reviews/')}"
        else:
            relative = source.removeprefix("docs/decisions/")
            target = f"review/evidence/{project_key}/editor-records/{relative}"
        mapping[source] = target

    sha = git(project, "rev-parse", "HEAD").decode().strip()
    decision_events = []
    for source, target in mapping.items():
        if target.startswith("review/proposals/"):
            continue
        data = (project / source).read_bytes()
        if source.endswith(".md"):
            def rewrite(match):
                value = match[2]
                if ":" in value or value.startswith("/"):
                    return match[0]
                origin = posixpath.normpath(posixpath.join(posixpath.dirname(source), value))
                if not (project / origin).exists():
                    return match[0]
                if origin in archived or origin.startswith(f"{OLD}/") and origin not in mapping:
                    value = f"https://github.com/integratedmodelling/imod/blob/{sha}/{origin}"
                else:
                    value = posixpath.relpath(mapping.get(origin, origin), posixpath.dirname(target))
                return f"{match[1]}{value}{match[3]}"

            data = MARKDOWN_LINK.sub(rewrite, data.decode("utf-8", errors="surrogateescape")).encode(
                errors="surrogateescape"
            )
            if source == f"{OLD}/bootstrap/README.md":
                data = data.replace(b"# Domain bootstrap workbench", b"# Historical domain bootstrap workbench")
                data = data.replace(b"agriculture has no executable namespace", b"Agriculture had no executable namespace at this research snapshot")
                data = data.replace(b"Agriculture has no executable namespace.", b"Agriculture had no executable namespace at this research snapshot. The current project has [an Agriculture ontology](../../../src/agriculture.kwv).")
        write(output, target, data)
        if source == "DECISIONS.md" or source.startswith("docs/decisions/") and source.endswith(".md"):
            decision_events.append({
                "version": 1, "id": "legacy-" + digest(source.encode())[:16],
                "type": "historical-record", "target": {"project": "imod", "kind": "project", "urn": "imod"},
                "record": target, "origin": {"commit": sha, "path": source},
            })
    write(output, "review/decisions.jsonl",
          "".join(json.dumps(event, ensure_ascii=False) + "\n" for event in decision_events).encode())
    write(output, "review/migration.json",
          encoded({"baseline": sha, "moved": mapping, "retained_in_git": archived}))
    write(output, "review/README.md", b"""# IMOD review

The [k.LAB project review format](https://github.com/integratedmodelling/klab-services/blob/develop/docs/PROJECT_REVIEW.md)
defines the directory and submission contract. Start with the
[research method](bootstrap/project--imod/METHOD.md), the [bootstrap index](bootstrap/project--imod/README.md),
or the [Hydrology](bootstrap/ontology--hydrology/BOOTSTRAP.md) and [Society](bootstrap/ontology--society/BOOTSTRAP.md) dossiers.

The [decision ledger](decisions.jsonl) indexes original editor records. The [migration inventory](migration.json)
locates retained artifacts and gives the Git revision for earlier diagnostics and experimental tooling.
Historical proposals and candidates retain their original review status.
""")


def install(project, output):
    plan = json.loads((output / "review/migration.json").read_bytes())
    if git(project, "status", "--porcelain", "-z"):
        raise ValueError("IMOD must be clean at installation")
    if git(project, "rev-parse", "HEAD").decode().strip() != plan["baseline"]:
        raise ValueError("IMOD has changed since migration preparation")
    if (project / "review").exists():
        raise ValueError("Project review directory already exists")
    original = {*plan["moved"], *plan["retained_in_git"]}
    protected = {"src", "strategies", "tests"}
    source_hashes = {name: digest((project / name).read_bytes())
                     for name in git(project, "ls-files", *protected).decode().splitlines()}
    for name in sorted(original):
        path = project / name
        if not path.resolve().is_relative_to(project) or not path.is_file():
            raise ValueError(f"Invalid migration source {name}")
    for folder in (output / "review").glob("proposals/*/*"):
        manifest = json.loads((folder / "manifest.json").read_bytes())
        for name, descriptor in manifest["files"].items():
            data = (folder / name).read_bytes()
            if descriptor != {"sha256": digest(data), "size": len(data)}:
                raise ValueError(f"Proposal hash mismatch in {folder / name}")
    from shutil import copytree
    copytree(output / "review", project / "review")
    attributes = project / ".gitattributes"
    current = attributes.read_bytes() if attributes.exists() else b""
    attributes.write_bytes(current + (b"\n" if current else b"")
                           + b"# Review bundle hashes address exact stored bytes.\nreview/** -text -filter -ident\n")
    readme = project / "README.md"
    readme.write_bytes(readme.read_bytes() + b"\nSee [IMOD review](review/README.md) for drafts, evidence and editorial history.\n")
    for name in sorted(original):
        (project / name).unlink()
    parents = {parent for name in original for parent in (project / name).parents
               if parent != project and parent.is_relative_to(project)}
    for parent in sorted(parents, key=lambda value: len(value.parts), reverse=True):
        if parent.exists() and not any(parent.iterdir()):
            parent.rmdir()
    for name, expected in source_hashes.items():
        if digest((project / name).read_bytes()) != expected:
            raise ValueError(f"Unexpected change to {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--pilot-only", action="store_true")
    parser.add_argument("--install", action="store_true", help="install an already prepared migration")
    args = parser.parse_args()
    project = args.project.resolve()
    output = args.output.resolve()
    if args.install:
        install(project, output)
        print(f"Installed the review migration in {project}")
        return
    if output.is_relative_to(project) or output.exists():
        parser.error("Use a new output directory outside the project")
    if not git(project, "rev-parse", "HEAD").decode().startswith(AUDITED):
        parser.error("IMOD revision has changed since the audit")
    mapping = pilot(project, output)
    if not args.pilot_only:
        migrate(project, output, mapping)
    print(f"Prepared {len(mapping)} mapped paths in {output}")


if __name__ == "__main__":
    main()
