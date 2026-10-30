"""Reproduce the real adapter switch failure using pinned public binary enums.

No Maven cache, running service, credentials, production source edits or scientific
computations are used. Exit 0 means both expected compiler outcomes were observed;
the newer-dependency javac process itself must exit nonzero.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request

SOURCE_REF = "c872c1040c354375b7c72b180a649c7508bcebc6"
ADAPTER = "klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java"
SEMANTIC_TYPE = "klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/SemanticType.java"
SOURCE_HASHES = {
    ADAPTER: "2832aa2739e926f07829c74c8b970bfe77cee1406cbbb72eeef0250c63036dc2",
    SEMANTIC_TYPE: "ae0f6b3590aa4876ee1845153421cb7014f49eee97f959427f52f597e6191d3c",
}
BASE = "https://central.sonatype.com/repository/maven-snapshots/org/integratedmodelling/org.integratedmodelling.languages.observable/1.0.0-SNAPSHOT/"
ARTIFACTS = {
    "old": ("1.0.0-20261016.113257-72", "0a0703361aaeb2af7b2c6660f675434e7d4554af447f57c930a294ff25ad7051"),
    "new": ("1.0.0-20261029.085338-82", "4b89ef600e5d620eaf18553170d11b49f5dd78f93914402d3901e4832f5edcf1"),
}


def run(command):
    options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    return subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=120, **options)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[4])
    parser.add_argument("--output", type=Path, required=True, help="New directory; existing paths are refused")
    parser.add_argument("--java-home", type=Path, help="JDK 21 directory; otherwise use JAVA_HOME or PATH")
    parser.add_argument("--artifact-dir", type=Path, help="Optional existing directory of the two pinned JARs; hashes still checked, no download")
    args = parser.parse_args()
    repo, output = args.repo.resolve(), args.output.resolve()
    home = args.java_home or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(home / "bin" / ("javac" + suffix)) if home else shutil.which("javac")
    javap = str(home / "bin" / ("javap" + suffix)) if home else shutil.which("javap")
    if not javac or not javap:
        raise RuntimeError("JDK 21 javac and javap are required")
    version = run([javac, "-version"])
    version_text = (version.stdout + version.stderr).strip()
    if version.returncode or not re.search(r"javac 21(?:\.|\s|$)", version_text):
        raise RuntimeError("Use JDK 21; observed: " + version_text)
    output.mkdir(parents=True, exist_ok=False)
    source_dir = output / "source"
    source_dir.mkdir()
    empty = output / "empty-sourcepath"
    empty.mkdir()
    sources = {}
    for path, expected in SOURCE_HASHES.items():
        result = run(["git", "-C", str(repo), "show", SOURCE_REF + ":" + path])
        if result.returncode:
            raise RuntimeError("Cannot read pinned source. Clone/fetch the repository commit first: " + result.stderr)
        text = result.stdout.replace("\r\n", "\n")
        if hashlib.sha256(text.encode("utf-8")).hexdigest() != expected:
            raise RuntimeError("Pinned source checksum mismatch: " + path)
        sources[path] = text
    adapter = sources[ADAPTER]
    start = adapter.index("  private Set<SemanticType> adaptSemanticType(")
    end = adapter.index("\n  /** Adapt a strategy document only.", start)
    method = adapter[start:end]
    case_names = set()
    for cases in re.findall(r"case\s+([A-Z_ ,]+)\s*->", method):
        case_names.update(c.strip() for c in cases.split(","))
    wrapper = ("import java.util.EnumSet;\nimport java.util.Set;\n"
               "import org.integratedmodelling.languages.api.SemanticSyntax;\n"
               "import org.integratedmodelling.klab.api.knowledge.SemanticType;\n"
               "class AdapterSwitchReproduction {\n" + method + "\n}\n")
    wrapper_path = source_dir / "AdapterSwitchReproduction.java"
    wrapper_path.write_text(wrapper, encoding="utf-8")
    semantic_path = source_dir / "SemanticType.java"
    semantic_path.write_text(sources[SEMANTIC_TYPE], encoding="utf-8")
    report = {"source_ref": SOURCE_REF, "source_hashes_lf": SOURCE_HASHES,
              "javac": version_text, "method_original_lines": [1232, 1481],
              "scope": "Real method copied verbatim, real internal enum source and real pinned binary input enums. Focused compile only, not full Maven CI.",
              "cases": {}}
    for label, (version_id, expected) in ARTIFACTS.items():
        name = "org.integratedmodelling.languages.observable-" + version_id + ".jar"
        url = BASE + name
        if args.artifact_dir:
            data = (args.artifact_dir / name).read_bytes()
        else:
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read(4 * 1024 * 1024 + 1)
        if len(data) > 4 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
            raise RuntimeError("Artifact checksum/size mismatch: " + url)
        jar = output / name
        jar.write_bytes(data)
        inventory = run([javap, "-classpath", str(jar), "org.integratedmodelling.languages.api.SemanticSyntax$Type"])
        if inventory.returncode:
            raise RuntimeError("Cannot inspect real enum: " + inventory.stderr)
        (output / (label + "-enum.txt")).write_text(inventory.stdout, encoding="utf-8")
        values = sorted(set(re.findall(r"\$Type ([A-Z][A-Z_0-9]*);", inventory.stdout)))
        classes = output / (label + "-classes")
        classes.mkdir()
        command = [javac, "--release", "21", "-proc:none", "-classpath", str(jar),
                   "-sourcepath", str(empty), "-d", str(classes), str(semantic_path), str(wrapper_path)]
        result = run(command)
        diagnostic = result.stdout + result.stderr
        (output / (label + "-javac.txt")).write_text(diagnostic, encoding="utf-8")
        row = {"artifact_url": url, "sha256": expected, "enum_count": len(values),
               "enum_values": values, "unhandled_values": sorted(set(values) - case_names),
               "command": command, "compiler_exit": result.returncode, "compiler_output": diagnostic}
        report["cases"][label] = row
        print(f"{label}: {version_id}; enum values={len(values)}; unhandled={row['unhandled_values']}; javac exit={result.returncode}")
        if diagnostic:
            print(diagnostic.rstrip())
    old, new = report["cases"]["old"], report["cases"]["new"]
    reproduced = (old["compiler_exit"] == 0 and old["unhandled_values"] == []
                  and new["compiler_exit"] != 0 and new["unhandled_values"] == ["RELATIONSHIP"]
                  and "switch expression does not cover all possible input values" in new["compiler_output"])
    report["expected_outcomes_reproduced"] = reproduced
    (output / "result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("Expected old-pass/new-fail reproduced:", reproduced)
    print("Logs, enum inventories, commands, checksums and artifacts:", output)
    return 0 if reproduced else 1


if __name__ == "__main__":
    raise SystemExit(main())
