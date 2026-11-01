"""Capture and stage the exact numbered ODO dependency for the owned TEST stack."""
import argparse
import hashlib
import json
from pathlib import Path

import httpx

ODO_COMMIT = "aa2cc5611799ceed2aad52ae2c2a349da29e3c4b"
ODO_SOURCE = f"https://raw.githubusercontent.com/integratedmodelling/odo-im/{ODO_COMMIT}/releases/0.1.0/odo.owl"
ODO_LOCATOR = "https://raw.githubusercontent.com/integratedmodelling/odo-im/master/releases/0.1.0/odo.owl"
ODO_SHA256 = "eb7318d725a16bd6e68a4cc12955c7b94b885a48d9c3c4f7837d4cd24a8ec7e4"
PROPERTIES = f"odo={ODO_LOCATOR}\nodo.sha256={ODO_SHA256}\n"


def validate_catalog(catalog):
    catalog = Path(catalog)
    content = (catalog / "odo.owl").read_bytes()
    if hashlib.sha256(content).hexdigest() != ODO_SHA256:
        raise ValueError("ODO bytes differ from the pinned numbered release; no bundled substitution is allowed")
    if (catalog / "source-catalog.properties").read_text(encoding="utf-8") != PROPERTIES:
        raise ValueError("Semantic source catalog differs from the declared ODO locator/hash")
    return content


def capture(output):
    output = Path(output)
    if output.exists() or not output.parent.is_dir():
        raise ValueError("Use a fresh catalog destination with an existing parent")
    with httpx.Client(trust_env=False, follow_redirects=False, timeout=30) as client:
        with client.stream("GET", ODO_SOURCE) as response:
            response.raise_for_status()
            if response.status_code != 200:
                raise ValueError("Pinned ODO source did not return HTTP 200")
            chunks, size = [], 0
            for chunk in response.iter_bytes():
                size += len(chunk)
                if size > 2 * 1024 * 1024:
                    raise ValueError("Pinned ODO source exceeds the capture limit")
                chunks.append(chunk)
    content = b"".join(chunks)
    if hashlib.sha256(content).hexdigest() != ODO_SHA256:
        raise ValueError("Downloaded ODO does not match its reviewed content pin")
    output.mkdir()
    (output / "odo.owl").write_bytes(content)
    (output / "source-catalog.properties").write_text(PROPERTIES, encoding="utf-8")
    (output / "provenance.json").write_text(json.dumps({
        "source": ODO_SOURCE, "commit": ODO_COMMIT, "declared_locator": ODO_LOCATOR,
        "sha256": ODO_SHA256, "size": len(content)}, indent=2), encoding="utf-8")


def stage(catalog, state):
    content = validate_catalog(catalog)
    state = Path(state).resolve()
    if not (state / "fixture-authority.json").is_file():
        raise ValueError("Semantic dependencies may only be staged into explicit prepared TEST state")
    destination = state / "home/.klab/knowledge"
    destination.mkdir(parents=True, exist_ok=False)
    (destination / "odo.owl").write_bytes(content)
    (destination / "source-catalog.properties").write_text(PROPERTIES, encoding="utf-8")
    (state / "semantic-dependencies.json").write_text(json.dumps({
        "odo_source": ODO_SOURCE, "odo_declared_locator": ODO_LOCATOR,
        "odo_sha256": ODO_SHA256, "catalog": str(Path(catalog).resolve())}, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    capture(args.output)
    print("Captured exact pinned ODO bytes and source locator; no server validation was bypassed")
