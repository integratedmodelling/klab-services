"""Create a new, pinned synthetic fixture variant; never edit an existing project.

Byte preservation is not semantic admission or genuine Runtime Job acceptance.
No services, credentials, scientific algorithms or network calls are involved.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath

CONTRACT = Path(__file__).resolve().parents[1] / "tests/fixtures/runtime_compatibility/contract.json"
EXECUTION_CONTRACT = CONTRACT.with_name("execution-contract-v3.json")
RECEIPT = "fixture-compatibility-receipt.json"
MAX_BYTES = 16 * 1024 * 1024


def _sha(value):
    return hashlib.sha256(value).hexdigest()


def _tree_hash(files):
    rows = [[name, _sha(files[name])] for name in sorted(files)]
    return _sha(json.dumps(rows, separators=(",", ":")).encode())


def _link(path):
    return path.is_symlink() or getattr(path, "is_junction", lambda: False)()


def _collect(root, count):
    if not root.is_dir() or root.resolve() != root.absolute():
        raise ValueError("Require a real, direct fixture directory")
    files = {}
    size = 0
    for path in root.rglob("*"):
        if _link(path):
            raise ValueError("Fixture links and junctions are not admitted")
        if path.is_file():
            if len(files) >= count:
                raise ValueError("Fixture inventory budget exceeded")
            with path.open("rb") as stream:
                body = stream.read(MAX_BYTES - size + 1)
            size += len(body)
            if size > MAX_BYTES:
                raise ValueError("Fixture byte budget exceeded")
            files[path.relative_to(root).as_posix()] = body
    return files


def _safe_name(name):
    path = PurePosixPath(name)
    if not name or "\\" in name or ":" in name or path.is_absolute() or any(part in ("", ".", "..") for part in name.split("/")):
        raise ValueError("Unsafe fixture-relative path")
    return path


def _prepare(source, destination, contract):
    source, destination = Path(source).absolute(), Path(destination).absolute()
    if os.path.lexists(destination):
        raise ValueError("Destination must be new; retain prior outputs and failures")
    if not destination.parent.is_dir() or destination.parent.resolve() != destination.parent.absolute():
        raise ValueError("Destination requires an existing direct private parent")
    if source == destination or source in destination.parents or destination in source.parents:
        raise ValueError("Input and output fixture trees must be separate")
    if any((parent / ".git").exists() for parent in destination.parents):
        raise ValueError("Fixture output must be outside Git workspaces")
    files = _collect(source, contract["input_files"])
    if (len(files) != contract["input_files"] or sum(map(len, files.values())) != contract["input_bytes"]
            or _tree_hash(files) != contract["input_tree_sha256"]):
        raise ValueError("Input differs from the approved frozen fixture")
    original = dict(files)
    changed = []
    for change in contract["changes"]:
        name = change["path"]
        _safe_name(name)
        if name in changed or name not in files or _sha(files[name]) != change["input_sha256"]:
            raise ValueError("Fixture change preimage is not the approved source")
        for replacement in change["replacements"]:
            old, new = replacement["old"].encode(), replacement["new"].encode()
            if not old or files[name].count(old) != 1:
                raise ValueError("Fixture replacement must match exactly once")
            files[name] = files[name].replace(old, new)
        if _sha(files[name]) != change["output_sha256"]:
            raise ValueError("Fixture result differs from the approved postimage")
        changed.append(name)
    for name, expected in contract["oracle"]["unchanged_files"].items():
        _safe_name(name)
        if name in changed or _sha(files[name]) != expected:
            raise ValueError("Numerical oracle/model inputs must remain byte-identical")
    derived = contract["oracle"].get("derived_model")
    if derived is not None:
        name = derived["path"]
        _safe_name(name)
        expression = derived["constant_expression"].encode()
        if (name not in changed or not expression or _sha(original[name]) != derived["input_sha256"]
                or _sha(files[name]) != derived["output_sha256"]
                or original[name].count(expression) != 1 or files[name].count(expression) != 1):
            raise ValueError("Derived synthetic model must retain the exact numerical expression")
    destination.mkdir()
    # Do not delete or roll back partially written outputs on I/O failure.
    for name, body in files.items():
        target = destination.joinpath(*_safe_name(name).parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as stream:
            stream.write(body)
    output = _collect(destination, len(files))
    if output != files or _collect(source, len(original)) != original:
        raise ValueError("Fixture bytes changed during preparation; output is not accepted")
    receipt = {"kind": "synthetic-runtime-fixture-byte-preservation", "version": contract["version"],
        "status": "PREPARED_NOT_SEMANTICALLY_ADMITTED", "input_tree_sha256": _tree_hash(original),
        "output_tree_sha256": _tree_hash(output), "files": len(files), "changed_files": changed,
        "unchanged_files": len(files) - len(changed), "oracle": contract["oracle"],
        "required_backend_core": contract["backend_core_ontology"],
        "source_unchanged": True, "services_started": 0, "scientific_jobs": 0,
        "production_worldview_migration": False, "semantic_admission": "UNKNOWN"}
    if "execution_contract" in contract:
        receipt["execution_contract"] = contract["execution_contract"]
        receipt["original_acceptance_replaced"] = False
    with (destination / RECEIPT).open("x", encoding="utf-8") as stream:
        json.dump(receipt, stream, indent=2)
    return receipt


def prepare(source, destination):
    """Use only the checked-in immutable fixture contract, never a caller-selected plan."""
    return _prepare(source, destination, json.loads(CONTRACT.read_text(encoding="utf-8")))


def prepare_execution(source, destination):
    """Opt in to a new synthetic contract; never overwrite the original or v2 output."""
    base = CONTRACT.read_bytes()
    variant = json.loads(EXECUTION_CONTRACT.read_text(encoding="utf-8"))
    contract = json.loads(base)
    if (variant["version"] != 3 or contract["version"] != 2
            or _sha(base) != variant["base_contract_sha256"]):
        raise ValueError("Execution variant requires the exact pinned v2 base contract")
    model = variant["model"]
    expected = contract["oracle"]["unchanged_files"].pop(model["path"])
    if expected != model["input_sha256"]:
        raise ValueError("Execution variant model differs from the frozen source")
    contract["changes"].append({key: model[key] for key in ("path", "input_sha256", "output_sha256", "replacements")})
    contract["oracle"]["derived_model"] = {key: model[key] for key in ("path", "input_sha256", "output_sha256", "constant_expression")}
    contract["version"] = variant["version"]
    contract["execution_contract"] = variant
    return _prepare(source, destination, contract)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--explicit-bearer", action="store_true",
                        help="Opt in to the separately labelled v3 synthetic execution contract")
    args = parser.parse_args(argv)
    try:
        receipt = (prepare_execution if args.explicit_bearer else prepare)(args.source, args.destination)
    except (OSError, ValueError, KeyError):
        print("UNKNOWN: fixture preparation failed; preserve original and any partial output")
        return 3
    print(json.dumps(receipt, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
