"""Research transformation tests, not numerical PaL execution or scientific acceptance."""
import hashlib
import io
import zipfile

import pytest

import pal_radiation_adaptation as adaptation


TOY_RADIATION = """namespace ecology.grassland.lf.radiation
 using (T_RC_MAX) from ecology.grassland.livestock.tables;
model im:Maximum earth.incubation:PhotosyntheticallyActiveRadiation in MJ/m^2 named rgc_max
'First description'
 observing type of im:Relevant landcover:Pastureland biology:Plant named com
 lookup (com) into T_RC_MAX;
model earth:SolarRadiation for ecology:Vegetation in MJ/m^2 named rg2
'Second description'
 observing earth:SolarRadiation in MJ/m^2 named rg, // in MJ/m^2
 type of im:Relevant landcover:Pastureland biology:Plant named com,
 type of ecology.incubation:Overstory im:Relevant landcover:Pastureland biology:Plant named com2
 set to [(com == com2) ? rg : (rg * 0.2)];
model percentage of ecology:Vegetation biology:Growth caused by earth:SolarRadiation named fr
'Third description'
 observing earth:SolarRadiation for ecology:Vegetation in MJ/m^2 named rg2,
 im:Maximum earth.incubation:PhotosyntheticallyActiveRadiation in MJ/m^2 named rgc_max
 set to [(rg2 > rgc_max) ? 1 : (rg2 / rgc_max)];
////-----------------------------FTRH
model percentage of ecology:Vegetation biology:Growth named ftrh;
"""
TOY_TABLE = """namespace ecology.grassland.livestock.tables;
define UNRELATED as 12;
define T_RC_MAX as {{
 com | rc_max
 ------------------------------------
 biology.nomenclature:Calluna | 25,
 biology.nomenclature:Carex | 25
}};
define MORE_UNRELATED as 42;
"""


@pytest.fixture
def toy_archive(monkeypatch):
    """Bypass only the real-archive pin for transformation unit fixtures.

    The production CLI never bypasses pins; actual original bytes are separately
    exercised by the opt-in research run, not bundled into ordinary CI.
    """
    monkeypatch.setattr(adaptation, "inventory", lambda raw, pin: None)

    def make(radiation=TOY_RADIATION, tables=TOY_TABLE):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr(adaptation.ROOT + adaptation.RADIATION, radiation)
            archive.writestr(adaptation.ROOT + adaptation.TABLES, tables)
            archive.writestr(adaptation.ROOT + "LICENSE", "toy licence retained verbatim")
        return stream.getvalue()
    return make


def test_real_archive_pin_cannot_be_bypassed_by_ordinary_input():
    with pytest.raises(ValueError, match="immutable"):
        adaptation.adapt_archive(b"not the pinned archive")


def test_changes_only_declared_syntax_and_explicit_subset(toy_archive):
    sources, licence, report = adaptation.adapt_archive(toy_archive())
    radiation, table = sources[adaptation.RADIATION], sources[adaptation.TABLES]
    assert licence == "toy licence retained verbatim"
    assert radiation.count("MJ/(m*m)") == 5
    assert "// in MJ/m^2" in radiation  # Original comment retained.
    assert "percentage ecology:Vegetation" in radiation
    assert "percentage of " not in radiation
    assert "// Legacy model description: 'First description'" in radiation
    assert "ftrh" not in radiation and "UNRELATED" not in table
    assert "{{" not in table and "}}" not in table
    assert "biology.nomenclature:Calluna | 25" in table
    assert adaptation._expressions(radiation) == adaptation._expressions(TOY_RADIATION)
    assert report["ontology_mapping"] == "BLOCKED_NOT_APPLIED"
    assert report["java_scientific_execution"] == "NOT_RUN"
    assert report["scientific_acceptance"] is False
    assert report["status"] == "SYNTAX_ONLY_NOT_REGISTERABLE"
    assert report["outputs"][adaptation.RADIATION] == hashlib.sha256(radiation.encode()).hexdigest()


@pytest.mark.parametrize("old,new", [
    ("////-----------------------------FTRH", "changed-boundary"),
    ("MJ/m^2 named rgc_max", "J/m^2 named rgc_max"),
    ("percentage of ", "proportion of "),
    ("'First description'", "// no description"),
])
def test_rejects_unexpected_transformation_inventory(toy_archive, old, new):
    with pytest.raises(ValueError, match="Pinned"):
        adaptation.adapt_archive(toy_archive(radiation=TOY_RADIATION.replace(old, new)))


def test_rejects_missing_table(toy_archive):
    with pytest.raises(ValueError, match="lookup table"):
        adaptation.adapt_archive(toy_archive(tables=TOY_TABLE.replace("T_RC_MAX", "OTHER")))


def test_existing_output_is_rejected_before_input_read(tmp_path):
    with pytest.raises(SystemExit):
        adaptation.main(["--archive", str(tmp_path / "absent.zip"), "--output", str(tmp_path)])
    assert not list(tmp_path.iterdir())


def test_cli_bundle_is_repeatable_and_retains_licence(toy_archive, tmp_path):
    archive = tmp_path / "input.zip"
    archive.write_bytes(toy_archive())
    outputs = [tmp_path / "one", tmp_path / "two"]
    for output in outputs:
        assert adaptation.main(["--archive", str(archive), "--output", str(output)]) == 0
    assert (outputs[0] / "adapted-source.zip").read_bytes() == (outputs[1] / "adapted-source.zip").read_bytes()
    with zipfile.ZipFile(outputs[0] / "adapted-source.zip") as bundle:
        assert bundle.read("LICENSE") == b"toy licence retained verbatim"
        assert b"SYNTAX_ONLY_NOT_REGISTERABLE" in bundle.read("NOTICE.txt")
        assert "META-INF/manifest.json" not in bundle.namelist()
