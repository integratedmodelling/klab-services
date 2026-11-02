"""G0 research-only archive verification/inventory; never extract or execute models.

Network is opt-in through --download. Outputs must be fresh, external destinations.
This is not an SDK API, scientific manifest schema, or compatibility validator.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import urllib.request
import zipfile


PINS = {
    "puerto": {
        "record": "6786419", "size": 612570,
        "url": "https://zenodo.org/api/records/6786419/files/jbusque/puerto-v1.0.zip/content",
        "md5": "4f2f1e85249a677016b8b1e4618a1097",
        "sha256": "4b1aff4c5b8ba3be9b2568ed79098c176850dc1be5570816e00d27848adccad9",
    },
    "pal": {
        "record": "7533051", "size": 4704395,
        "url": "https://zenodo.org/api/records/7533051/files/amarqueztorres/im.ecology.grassland.livestock-0.1.1.zip/content",
        "md5": "94152225a4e6cf5824c62ab9d8dee1e9",
        "sha256": "6503feeb0dc743601fe3e3205c879354801fc21353913fd06b9ae4ff1443d872",
    },
}
MAX_ARCHIVE = 8 * 1024 * 1024
MAX_EXPANDED = 100 * 1024 * 1024
MAX_MEMBER = 16 * 1024 * 1024


def inventory(raw, pin):
    if (len(raw) > MAX_ARCHIVE or len(raw) != pin["size"]
            or hashlib.sha256(raw).hexdigest() != pin["sha256"]
            or hashlib.md5(raw).hexdigest() != pin["md5"]):
        raise ValueError("Archive differs from immutable record pin")
    rows = []
    names = set()
    total = 0
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        for item in archive.infolist():
            original = item.orig_filename
            path = PurePosixPath(original)
            if (path.is_absolute() or ".." in path.parts or "\\" in original
                    or ":" in original or "\x00" in original or item.filename in names):
                raise ValueError("Unsafe or duplicate archive member")
            names.add(item.filename)
            total += item.file_size
            if item.file_size > MAX_MEMBER or total > MAX_EXPANDED or len(names) > 4096:
                raise ValueError("Archive inventory budget exceeded")
            if item.is_dir():
                continue
            contents = archive.read(item)
            rows.append({"path": item.filename, "size": len(contents),
                         "sha256": hashlib.sha256(contents).hexdigest()})
    return {"record": pin["record"], "archive_sha256": pin["sha256"],
            "archive_bytes": len(raw), "members": rows,
            "research_code_executed": False, "scientific_acceptance": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", choices=tuple(PINS), required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", type=Path)
    source.add_argument("--download", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.output.exists() or not args.output.parent.is_dir():
        parser.error("Use a fresh output under an existing parent")
    pin = PINS[args.archive]
    if args.download:
        with urllib.request.urlopen(pin["url"], timeout=60) as response:
            if not response.geturl().startswith("https://zenodo.org/"):
                raise ValueError("Unexpected archive destination")
            raw = response.read(MAX_ARCHIVE + 1)
    else:
        with args.input.open("rb") as stream:
            raw = stream.read(MAX_ARCHIVE + 1)
    report = inventory(raw, pin)
    args.output.mkdir()
    (args.output / "archive.zip").write_bytes(raw)
    (args.output / "inventory.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"archive": args.archive, "files": len(report["members"]),
                      "sha256": report["archive_sha256"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
