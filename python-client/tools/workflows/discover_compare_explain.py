"""Shared four-cell workflow; explicit external scientific oracles, never result substitutes.

No imports, peer initialization or deployment administration are performed here.
The caller supplies compatible requests, an independent reference and owned factories.
"""
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from itertools import islice
import math
from pathlib import Path
import re
import time

from klab_client import (Connection, Experiment, ExperimentBatch, ExperimentRunner, ResourceInspector, SemanticExplorer,
                         export_for_analysis, fetch_execution_evidence, open_raster)
from klab_client.errors import ConfigurationError
from .scientific_checks import require


def _finite_number(value):
    if type(value) not in (int, float):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def _decimal(value):
    # Exact oracle strings are preferred; floats and booleans are not references.
    try:
        if not isinstance(value, (str, int, Decimal)) or isinstance(value, bool):
            raise ValueError()
        result = Decimal(value)
        if not result.is_finite():
            raise ValueError()
        return result
    except (InvalidOperation, ValueError):
        raise ConfigurationError("Oracle values/tolerances require finite exact decimals") from None


@dataclass(frozen=True)
class RasterOracle:
    """Independent single-band north-up raster reference, rows ordered north to south.

    source_sha256 identifies the externally maintained fixture/reference, not live
    output. Supplying a digest alone does not prove independence or scientific accuracy.
    """
    source_sha256: str
    units: str
    crs: str
    width: int
    height: int
    transform: tuple
    values: tuple
    dtype: str
    absolute_tolerance: str = "0"
    relative_tolerance: str = "0"

    def __post_init__(self):
        if (not isinstance(self.source_sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", self.source_sha256)
                or any(not isinstance(v, str) or not v.strip() for v in (self.units, self.crs, self.dtype))
                or any(type(v) is not int or v <= 0 for v in (self.width, self.height))
                or self.width * self.height > 1_000_000):
            raise ConfigurationError("Supply bounded dimensions and explicit reference identity/metadata")
        if (not isinstance(self.transform, (tuple, list)) or len(self.transform) != 6
                or any(not _finite_number(v) for v in self.transform)
                or self.transform[0] <= 0 or self.transform[4] >= 0 or self.transform[1] != 0 or self.transform[3] != 0):
            raise ConfigurationError("Reference requires a finite north-up affine transform")
        if not isinstance(self.values, (tuple, list)) or len(self.values) != self.width * self.height:
            raise ConfigurationError("Supply every reference cell, with None for missing cells")
        values = tuple(None if value is None else _decimal(value) for value in self.values)
        if all(value is None for value in values):
            raise ConfigurationError("All-missing data cannot establish scientific acceptance")
        absolute, relative = _decimal(self.absolute_tolerance), _decimal(self.relative_tolerance)
        if absolute < 0 or relative < 0:
            raise ConfigurationError("Scientific tolerances cannot be negative")
        object.__setattr__(self, "values", values)
        object.__setattr__(self, "transform", tuple(self.transform))


def validate_raster(dataset, oracle):
    """Compare every real exported cell and mask, independent metadata and valid zero."""
    require((dataset.width, dataset.height, dataset.count) == (oracle.width, oracle.height, 1),
            "Export shape/bands differ from the independent oracle")
    require(dataset.crs is not None and str(dataset.crs) == oracle.crs, "Export CRS differs")
    require(tuple(dataset.transform)[:6] == oracle.transform, "Export transform/orientation differs")
    require(dataset.units == (oracle.units,), "Export measurement units differ")
    require(dataset.dtypes == (oracle.dtype,), "Export precision differs")
    values, masks = dataset.read(1), dataset.read_masks(1)
    absolute = _decimal(oracle.absolute_tolerance)
    relative = _decimal(oracle.relative_tolerance)
    maximum, total, valid, missing, zeros = Decimal(0), Decimal(0), 0, 0, 0
    for index, expected in enumerate(oracle.values):
        row, column = divmod(index, oracle.width)
        require(bool(masks[row, column]) == (expected is not None), "Export missingness differs")
        if expected is None:
            missing += 1
            continue
        actual = Decimal(str(values[row, column]))
        require(actual.is_finite(), "Export contains nonfinite valid data")
        error = abs(actual - expected)
        tolerance = max(absolute, abs(expected) * relative)
        require(error <= tolerance, "Export value differs from the independent oracle")
        maximum = max(maximum, error)
        total += actual
        valid += 1
        zeros += actual == 0
    return {"verified_cells": len(oracle.values), "valid_cells": valid, "missing_cells": missing,
            "valid_zero_cells": zeros, "valid_sum": str(total), "maximum_error": str(maximum),
            "reference_sha256": oracle.source_sha256}


@dataclass(frozen=True)
class MatrixCase:
    experiment: Experiment
    resolution: str
    oracle: RasterOracle


def validate_matrix(cases):
    """Reject cosmetic variation names and repeated grids before making remote work.

    A differing definition/scenario is necessary, not proof of scientific effect;
    externally justified per-cell oracles and actual exports remain required.
    """
    cases = tuple(islice(cases, 5))
    if len(cases) != 4 or any(not isinstance(c, MatrixCase) or not isinstance(c.experiment, Experiment)
                             or not isinstance(c.oracle, RasterOracle) for c in cases):
        raise ConfigurationError("The scientific matrix requires four typed cases with independent oracles")
    variations = {c.experiment.variation for c in cases}
    resolutions = {c.resolution for c in cases}
    if (len(variations) != 2 or any(not isinstance(v, str) or not v.strip() for v in variations)
            or len(resolutions) != 2 or any(not isinstance(v, str) or not v.strip() for v in resolutions)
            or len({c.experiment.name for c in cases}) != 4
            or len({(c.experiment.variation, c.resolution) for c in cases}) != 4):
        raise ConfigurationError("Use two variations by two resolutions with unique experiment names")
    signatures, grids = {}, {}
    for case in cases:
        request = case.experiment.request
        if not isinstance(request.observable, str):
            raise ConfigurationError("Matrix definitions must be explicit Reasoner-resolved text requests")
        signature = (request.observable, request.options.scenarios)
        variation = case.experiment.variation
        if variation in signatures and signatures[variation] != signature:
            raise ConfigurationError("A scientific variation must be consistent across resolutions")
        signatures[variation] = signature
        grid = (case.oracle.width, case.oracle.height, case.oracle.crs, case.oracle.transform)
        if case.resolution in grids and grids[case.resolution] != grid:
            raise ConfigurationError("A resolution must use the same reference grid across variations")
        grids[case.resolution] = grid
    if len(set(signatures.values())) != 2 or len(set(grids.values())) != 2:
        raise ConfigurationError("Renamed identical requests/grids are not scientific variations/resolutions")
    return cases


def validate_recorded_evidence(observation, evidence, expected_node_ids, *, model_urn=None):
    """Require actual success links and fixture-specified recorded plan node identities.

    Recorded plan structure is not itself execution or durable-root-commit proof.
    """
    require(evidence.provenance.observation.observation_id == observation.id,
            "Execution evidence belongs to another observation")
    effects = {"MEASURE": "MEASURED", "QUANTIFICATION": "QUANTIFIED"}
    successes = {link.source.id for link in evidence.provenance.links
                 if link.target.id == observation.id and link.source.kind == "activity"
                 and link.source.outcome == "SUCCESS"
                 and effects.get(link.source.activity_type) == link.relationship}
    nodes = {node.id for activity in evidence.activities if activity.id in successes
             for chart in activity.charts for node in chart.nodes}
    require(bool(expected_node_ids) and set(expected_node_ids) <= nodes,
            "Successful recorded activity lacks the expected fixture computation nodes")
    if model_urn is not None:
        selected_models = set()
        for activity in evidence.activities:
            if activity.outcome != "SUCCESS":
                continue
            for chart in activity.charts:
                if chart.phase != "resolution plan":
                    continue
                for node in chart.nodes:
                    metadata = node.raw.get("metadata", {})
                    if metadata.get("kind") == "model":
                        selected_models.add(metadata.get("urn"))
        require(model_urn in selected_models, "Inspected model is absent from the successful recorded resolution")
    return {"successful_activity_ids": sorted(successes), "matched_node_ids": sorted(set(expected_node_ids)),
            "partial": evidence.partial, "durable_root_commit": evidence.durable_root_commit}


def inspect_result(observation, oracle, path, expected_node_ids, *, model_urn=None):
    """Native export, full oracle check, analysis and recorded evidence for one result."""
    import rasterio
    stages = {}
    started = time.perf_counter()
    require(observation.units == oracle.units, "Computed observation units differ from reference")
    artifact = export_for_analysis(observation, path, max_bytes=64 * 1024 * 1024)
    stages["native_export"] = time.perf_counter() - started
    started = time.perf_counter()
    with rasterio.open(artifact.export.path) as dataset:
        raster = validate_raster(dataset, oracle)
    stages["raster_validation"] = time.perf_counter() - started
    started = time.perf_counter()
    analysis = open_raster(artifact)
    absolute = _decimal(oracle.absolute_tolerance)
    relative = _decimal(oracle.relative_tolerance)
    require(str(analysis.dataset.value.dtype) == oracle.dtype, "Analysis changed precision")
    for index, expected in enumerate(oracle.values):
        row, column = divmod(index, oracle.width)
        require(bool(analysis.dataset.valid.values[row, column]) == (expected is not None),
                "Analysis changed missingness")
        if expected is not None:
            actual = Decimal(str(analysis.dataset.value.values[row, column]))
            tolerance = max(absolute, abs(expected) * relative)
            require(actual.is_finite() and abs(actual - expected) <= tolerance, "Analysis changed values")
        x = oracle.transform[2] + (column + .5) * oracle.transform[0]
        y = oracle.transform[5] + (row + .5) * oracle.transform[4]
        require(float(analysis.dataset.x.values[column]) == x and float(analysis.dataset.y.values[row]) == y,
                "Analysis changed cell centers")
    stages["analysis_validation"] = time.perf_counter() - started
    started = time.perf_counter()
    evidence = validate_recorded_evidence(observation, fetch_execution_evidence(observation), expected_node_ids,
                                          model_urn=model_urn)
    stages["recorded_evidence"] = time.perf_counter() - started
    return {"raster": raster, "evidence": evidence, "artifact_sha256": artifact.sha256,
            "exported_bytes": artifact.export.bytes_written, "stages_seconds": stages}


def run_matrix(connection_factory, cases, directory, *, model_urn, expected_node_ids, workers=1, timeout=120):
    """Discover/inspect once, then run independent owned experiments with native checks.

    Caller explicitly authorizes computation and supplies a fresh output directory.
    Default retained remote contexts support later original-result restoration.
    No model is forced and inspected applicability is never called executed selection.
    """
    cases = validate_matrix(cases)
    if (not isinstance(expected_node_ids, (tuple, list)) or not 1 <= len(expected_node_ids) <= 256
            or any(not isinstance(v, str) or not v or len(v) > 256 for v in expected_node_ids)):
        raise ConfigurationError("Supply independently specified recorded computation node identities")
    expected_node_ids = tuple(expected_node_ids)
    if (not callable(connection_factory) or type(workers) is not int or workers not in (1, 4)
            or not _finite_number(timeout) or timeout <= 0
            or not isinstance(model_urn, str) or not model_urn.strip() or len(model_urn) > 512):
        raise ConfigurationError("Use an owned factory, model URN, workers 1 or 4 and finite positive timeout")
    if any(c.experiment.context_policy.release != "retain" for c in cases):
        raise ConfigurationError("Retain original experiments for the later restoration gate")
    directory = Path(directory)
    directory.mkdir(exist_ok=False)
    started = time.perf_counter()
    with connection_factory() as owner:
        client = owner.client if isinstance(owner, Connection) else owner
        explorer = SemanticExplorer(client)
        semantics = [explorer.resolve_observable(definition) for definition in
                     dict.fromkeys(c.experiment.request.observable for c in cases)]
        model = ResourceInspector(client).retrieve(model_urn)
    setup = time.perf_counter() - started
    by_name = {case.experiment.name: case for case in cases}

    def process(experiment, observation):
        case = by_name[experiment.name]
        return inspect_result(observation, case.oracle, directory / (experiment.name + ".tif"), expected_node_ids,
                              model_urn=model_urn)

    with ExperimentRunner(connection_factory, workers=workers, processor=process) as runner:
        batch = runner.run([case.experiment for case in cases], timeout=timeout)
    with (directory / "batch.json").open("x", encoding="utf-8") as stream:
        stream.write(batch.dumps())
    return {"batch": batch, "model": model, "semantics": tuple(semantics), "setup_seconds": setup,
            "elapsed_seconds": time.perf_counter() - started,
            "scientific_acceptance": False,
            "remaining_gates": ("authority/hierarchy", "fresh-process restoration", "permissions", "executed notebook"),
            "matrix_verified": all(outcome.state == "succeeded" for outcome in batch.outcomes)}


def restore_matrix(connection_factory, cases, encoded_batch, directory, *, expected_node_ids, model_urn=None):
    """Restore original observation handles and revalidate, never resubmit.

    A separate process must invoke this helper to establish the fresh-process gate.
    Authentication is reacquired by the factory, never loaded from the manifest.
    """
    cases = validate_matrix(cases)
    batch = ExperimentBatch.loads(encoded_batch)
    require(tuple(o.name for o in batch.outcomes) == tuple(c.experiment.name for c in cases),
            "Restoration manifest experiment identities/order differ")
    require(all(o.state == "succeeded" and o.remote_release == "retained" for o in batch.outcomes),
            "Restoration requires successful retained original results")
    directory = Path(directory)
    directory.mkdir(exist_ok=False)
    records = []
    for case, outcome in zip(cases, batch.outcomes):
        with connection_factory() as owner:
            client = owner.client if isinstance(owner, Connection) else owner
            observation = client.restore_handle(outcome.result_handle)
            require(observation.to_handle() == outcome.result_handle, "Restoration returned replacement work")
            record = inspect_result(observation, case.oracle, directory / (outcome.name + ".tif"), expected_node_ids,
                                    model_urn=model_urn)
            records.append({"name": outcome.name, "observation_handle": outcome.result_handle.to_dict(), **record})
    return tuple(records)
