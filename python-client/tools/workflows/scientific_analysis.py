"""Shared controlled scientific analysis/lineage scenario, including fresh-process restoration."""
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

from klab_client import Context, export_for_analysis, fetch_provenance, load_handle, open_raster, trace_analysis
from klab_client.client import scope_token
from klab_client.dto import observation_from_wire
from .compute_and_analyze import initialize, make_client
from .export_deliverables import compute, validate_dataset, WEST, NORTH, COLUMNS, ROWS, CELL_SIZE
from .scientific_checks import require
from verify_fixture import require_access_denied


def analyze_saved(configuration, bundle, output, *, client_factory=None):
    """Restore, native-export, independently check, then query current recorded links."""
    import rasterio
    with client_factory() if client_factory else make_client(configuration) as client:
        saved = load_handle(bundle / "observation.json")
        observation = client.restore_handle(saved)
        require(observation.to_handle() == saved, "Fresh-process restoration changed the observation identity")
        artifact = export_for_analysis(observation, output)
        with rasterio.open(output) as reader:
            oracle = validate_dataset(reader, observation.units)
        analysis = open_raster(artifact)
        data = analysis.dataset
        require(data.value.dtype.name == oracle["dtype"], "Adapter changed numeric precision")
        for row in range(ROWS):
            for col in range(COLUMNS):
                native_y = ROWS - 1 - row
                missing = (col, native_y) == (2, 1)
                require(bool(data.valid.values[row, col]) != missing, "Adapter validity differs from known fixture")
                if not missing:
                    require(float(data.value.values[row, col]) == col * 10 + native_y, "Adapter value differs from known fixture")
                require(float(data.x.values[col]) == WEST + (col + .5) * CELL_SIZE, "Adapter x center differs")
                require(float(data.y.values[row]) == NORTH - (row + .5) * CELL_SIZE, "Adapter y center differs")
        expected_sum = sum(x * 10 + y for y in range(ROWS) for x in range(COLUMNS) if (x, y) != (2, 1))
        require(float(data.value.where(data.valid).sum()) == expected_sum, "Masked analysis statistic differs")
        view = fetch_provenance(observation)
        traced = trace_analysis(analysis, view)
        # Native numeric contextualization must leave an executed Activity effect.
        effects = {"MEASURE": "MEASURED", "QUANTIFICATION": "QUANTIFIED"}
        require(any(edge.target.id == observation.id and edge.source.kind == "activity"
                    and edge.source.outcome == "SUCCESS" and effects.get(edge.source.activity_type) == edge.relationship
                    for edge in view.links), "No successful recorded numerical execution effect was exposed")
        refreshed = fetch_provenance(observation)
        require(refreshed.fetched_at >= view.fetched_at, "Explicit provenance refresh did not refetch")
        other_configuration = {**configuration, "token": configuration["other_token"], "username": "other-scientist"}
        with make_client(other_configuration) as other:
            positive = other.create_session(name="analysis-valid-nonowner")
            try:
                foreign_context = Context(other, observation._context.configuration, token=scope_token(observation._context))
                inaccessible = observation_from_wire(observation.raw, foreign_context)
                denied_lineage = require_access_denied(lambda: fetch_provenance(inaccessible))
                denied_export = require_access_denied(lambda: export_for_analysis(inaccessible, bundle / "denied.tif"))
                require(not (bundle / "denied.tif").exists(), "Denied export published a local file")
            finally:
                primary = sys.exc_info()[1]
                try:
                    require(positive.release(), "Nonowner positive-control cleanup failed")
                except BaseException as cleanup:
                    if primary is None:
                        raise
                    primary.add_note("Nonowner control cleanup failed: " + type(cleanup).__name__)
        return {"observation_id": observation.id, "runtime_id": saved.runtime_id,
            "artifact_sha256": artifact.sha256, "verified_cells": COLUMNS * ROWS,
            "valid_sum": expected_sum, "source_unit": analysis.descriptor.units,
            "recorded_links": len(view.links), "partial": view.partial,
            "recorded_relationships": [{"source": edge.source.id, "source_kind": edge.source.kind,
                "target": edge.target.id, "target_kind": edge.target.kind,
                "relationship": edge.relationship, "evidence": edge.evidence,
                "activity_type": edge.source.activity_type, "outcome": edge.source.outcome} for edge in view.links],
            "unauthorized_provenance": denied_lineage, "unauthorized_export": denied_export,
            "valid_nonowner_positive_control": True,
            "consistency": traced.consistency, "independent_raster": oracle,
            "finished_at": datetime.now(timezone.utc).isoformat()}


def run(state, *, connection=False):
    """Create/dispose only an owned TEST session; worker restores without resubmission."""
    configuration = json.loads((state / "scientist.json").read_text())
    initialize(configuration)
    bundle = state / ("analysis-provenance-" + uuid4().hex)
    bundle.mkdir()
    config = bundle / "configuration.json"
    # Contains TEST credentials like the existing runner state; never copy into public evidence.
    config.write_text(json.dumps(configuration))
    session = None
    if connection:
        from .connection import connected_client
        factory = lambda: connected_client(state, "certificate")
    else:
        factory = lambda: make_client(configuration)
    with factory() as owner:
        try:
            session = owner.create_session(name="scientific-analysis-provenance")
            record, _ = compute(configuration, session.id, state / "controls", bundle, 0, client_factory=factory)
            require(record["outcome"] == "verified", "Controlled numerical field did not compute")
            worker = Path(__file__).resolve().parents[1] / "analyze_saved_result.py"
            command = [sys.executable, str(worker), "--configuration", str(config), "--bundle", str(bundle)]
            if connection:
                command.extend(("--connection-state", str(state)))
            result = subprocess.run(command, capture_output=True, text=True, timeout=180)
            require(result.returncode == 0, "Fresh-process analysis/provenance worker failed; inspect private owned state")
            evidence = json.loads((bundle / "analysis.json").read_text())
            evidence["connection_path"] = "certificate to issued-token fresh-process recovery" if connection else "configured Client"
            require(evidence["observation_id"] == record["observation_id"], "Worker analyzed replacement work")
            return evidence
        finally:
            primary = sys.exc_info()[1]
            try:
                if session is not None:
                    require(session.release(), "Owned analysis session release failed")
            except BaseException as cleanup:
                if primary is None:
                    raise
                primary.add_note("Owned analysis cleanup failed: " + type(cleanup).__name__)
