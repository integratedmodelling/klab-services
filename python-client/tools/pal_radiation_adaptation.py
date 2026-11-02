"""Generate an explicitly unapproved, syntax-only PaL Radiation subset from pinned bytes.

No network, scientific computation, ontology substitution, service registration or
Java changes. Output is research material, not an executable accepted project.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

from pal_g0_artifacts import PINS, MAX_ARCHIVE, inventory


ROOT = "amarqueztorres-im.ecology.grassland.livestock-575f36d/"
RADIATION = "src/ecology/grassland/lf/radiation.kim"
TABLES = "src/ecology/grassland/livestock/tables.kim"
NOTICE = """Adapted research subset of PaL 0.1.1, Zenodo record 7533051.
Original: amarqueztorres/im.ecology.grassland.livestock, revision 575f36d.
Original GNU Affero General Public License version 3 retained in LICENSE.
Modified 2026-11-01: syntax adaptation only; original equations/concepts retained.
Includes rgc_max, rg2, fr and T_RC_MAX. Excludes ftrh and unrelated tables/models.
SYNTAX_ONLY_NOT_REGISTERABLE: no approved worldview/resource/time bindings.
Not authentic unchanged PaL execution, Radiation accuracy or P4 reproduction.
"""


def _normalized(raw):
    return "\n".join(raw.decode("utf-8").splitlines()) + "\n"


def _expressions(text):
    return re.findall(r"\bset to (\[[^\]]*\])", text)


def adapt_archive(raw):
    """Return two source files and a traceability manifest; never rewrite meaning."""
    inventory(raw, PINS["pal"])
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        original_radiation = archive.read(ROOT + RADIATION)
        original_tables = archive.read(ROOT + TABLES)
        license_text = archive.read(ROOT + "LICENSE").decode("utf-8")
    radiation = _normalized(original_radiation)
    marker = "////-----------------------------FTRH"
    if radiation.count(marker) != 1:
        raise ValueError("Pinned Radiation subset boundary differs")
    radiation = radiation.split(marker, 1)[0].rstrip() + "\n"
    original_expressions = _expressions(radiation)
    unit_count = sum(line.partition("//")[0].count("MJ/m^2") for line in radiation.splitlines())
    if len(original_expressions) != 2 or unit_count != 5:
        raise ValueError("Pinned Radiation expression/unit inventory differs")
    radiation = "\n".join(line.partition("//")[0].replace("MJ/m^2", "MJ/(m*m)")
                          + ("//" + line.partition("//")[2] if "//" in line else "")
                          for line in radiation.splitlines()) + "\n"
    if radiation.count("percentage of ") != 1:
        raise ValueError("Pinned percentage syntax differs")
    radiation = radiation.replace("percentage of ", "percentage ")
    lines = radiation.splitlines()
    descriptions = [i for i, line in enumerate(lines) if line.startswith("'")]
    if len(descriptions) != 3:
        raise ValueError("Pinned model-description inventory differs")
    for index in descriptions:
        lines[index] = "// Legacy model description: " + lines[index]
    radiation = "\n".join(lines) + "\n"
    if _expressions(radiation) != original_expressions:
        raise ValueError("Scientific expressions changed during syntax adaptation")

    tables = _normalized(original_tables)
    match = re.search(r"(?ms)^define T_RC_MAX as \{\{.*?^\}\};", tables)
    if match is None:
        raise ValueError("Pinned Radiation lookup table differs")
    selected_table = match.group()
    adapted_table = selected_table.replace("{{", "===", 1).replace("}};", "===;", 1)
    table_source = "namespace ecology.grassland.livestock.tables;\n\n" + adapted_table + "\n"
    sources = {RADIATION: radiation, TABLES: table_source}
    report = {
        "version": 1, "status": "SYNTAX_ONLY_NOT_REGISTERABLE",
        "archive_record": PINS["pal"]["record"], "archive_sha256": PINS["pal"]["sha256"],
        "original_members": {
            RADIATION: hashlib.sha256(original_radiation).hexdigest(),
            TABLES: hashlib.sha256(original_tables).hexdigest(),
        },
        "included_models": ["rgc_max", "rg2", "fr"],
        "excluded": ["ftrh", "all lookup tables except T_RC_MAX", "data/observer namespace"],
        "changes": [
            "Normalize source line endings to LF",
            "Extract explicitly labelled three-model Radiation subset",
            "Spell five MJ/m^2 units as MJ/(m*m)",
            "Remove grammar-only 'of' after one percentage keyword; numerical scale remains unresolved",
            "Preserve three model descriptions verbatim as comments; live documentation parity not claimed",
            "Extract T_RC_MAX; change only its table delimiters from {{...}} to ===...===",
        ],
        "equations_unchanged": original_expressions,
        "outputs": {name: hashlib.sha256(text.encode("utf-8")).hexdigest()
                    for name, text in sources.items()},
        "ontology_mapping": "BLOCKED_NOT_APPLIED",
        "resource_binding": "BLOCKED_NOT_APPLIED",
        "time_unit_semantics": "BLOCKED_NOT_APPROVED",
        "java_scientific_execution": "NOT_RUN", "scientific_acceptance": False,
    }
    return sources, license_text, report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.output.exists() or not args.output.parent.is_dir():
        parser.error("Use a fresh external output under an existing parent")
    with args.archive.open("rb") as stream:
        raw = stream.read(MAX_ARCHIVE + 1)
    sources, license_text, report = adapt_archive(raw)
    args.output.mkdir()
    with zipfile.ZipFile(args.output / "adapted-source.zip", "x") as archive:
        for name, text in {**sources, "LICENSE": license_text, "NOTICE.txt": NOTICE}.items():
            # Fixed metadata makes the derivative source bundle reproducible.
            info = zipfile.ZipInfo(name, date_time=(2026, 11, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, text.encode("utf-8"))
    report["bundle_sha256"] = hashlib.sha256((args.output / "adapted-source.zip").read_bytes()).hexdigest()
    (args.output / "adaptation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"status": report["status"], "models": report["included_models"],
                      "bundle_sha256": report["bundle_sha256"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
