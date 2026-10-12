"""Run against the explicit isolated fixture: typed setup, options, jobs and known values."""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import sys

from klab_client import (Client, Endpoint, ContextOptions, GridOptions, ObservationOptions,
                         ObservationRequest, RectangularGeometry)
from klab_client.experiment import verify_grid


def fixture_client(config):
    return Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")})


def demonstrate(state):
    config = json.loads((state / "scientist.json").read_text())
    with fixture_client(config) as client:
        client.initialize_user_scope(local_federation=True)
        session = client.create_session(name="typed-observe-example")
        context = session.create_context(options=ContextOptions(name="Typed field", persistence="EXPLICIT_ACTION"))
        print(f"Context {context.id}; retained on failure for explicit cleanup", flush=True)
        region_job = context.observe(ObservationRequest("earth:Region", urn="python.fixture:typed-region",
            geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))
        region = region_job.result(120)
        verify_grid(region)
        focused = context.within(region)
        (state / "controls/reset-field").write_text("reset owned fixture")
        aspect = focused.observe("geography:Aspect in degree_angle").result(120)
        verify_grid(aspect)
        expected = tuple(None if (x, y) == (2, 1) else Decimal(x*10+y) for y in range(4) for x in range(5))
        actual = aspect.fetch_data(range(20), curve="D2_YX").values
        if actual != expected:
            raise AssertionError("Nonuniform field, valid zero or missingness differs from coordinate oracle")
        scenario = focused.observe("geography:Elevation in m",
            options=ObservationOptions(scenarios=["python.scenario"])).result(120)
        if scenario.fetch_data(range(20), curve="D2_YX").values != (Decimal("234.5"),)*20:
            raise AssertionError("Scenario selection was not honored by the Runtime")
        namespace = focused.observe("geography:BathymetricDepth in m",
            options=ObservationOptions(namespace="python.lexical")).result(120)
        if namespace.fetch_data([0], curve="D2_YX").values != (Decimal("66.5"),):
            raise AssertionError("Private lexical namespace selection was not honored")
        project = focused.observe("geography:Slope in degree_angle",
            options=ObservationOptions(project="python.fixture")).result(120)
        if project.fetch_data([0], curve="D2_YX").values != (Decimal("7.5"),):
            raise AssertionError("Project-private model selection was not honored")
        observer = context.configuration.get("observer")
        if not observer or type(observer.get("id")) is not int or observer["id"] <= 0:
            raise AssertionError("Prepared context did not supply a supported observer")
        observer_job = focused.observe("geography:Elevation in m",
            options=ObservationOptions(observer=observer["id"]))
        observer_job.result(120)
        if observer_job.scope.get_context_id() != focused.get_context_id() + f"#{observer['id']}":
            raise AssertionError("Explicit observer/focus selection was not preserved")
        grid_context = session.create_context(options=ContextOptions(name="Typed lattice",
            grid=GridOptions(500000, 0, 100, projection="EPSG:32631")))
        alignment = grid_context.configuration.get("gridAlignment", {})
        if alignment.get("projection") != "EPSG:32631" or alignment.get("stepX") != 100 or alignment.get("stepY") != 100:
            raise AssertionError("Context-level inline lattice was not honored")
        report = {"passed": True, "context_id": context.id, "aspect_id": aspect.id,
            "verified_cells": 20, "verified_options": ["geometry", "scenarios", "namespace", "project", "observer", "context grid"],
            "scope": observer_job.scope.get_context_id()}
        if not grid_context.release() or not context.release() or not session.release():
            raise AssertionError("Test-owned explicit cleanup was not confirmed")
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = demonstrate(args.state_dir)
    if args.report:
        args.report.write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
