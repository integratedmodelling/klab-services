"""Small maintained elevation experiment; requires real geography assets."""
from decimal import Decimal

from .api.runtime import GeometryImpl, ObservationImpl
from .dto import storage_semantics
from .errors import JobFailedError


def rectangle_geometry():
    # GeometryBuilder.SpaceBuilder.shape/gridResolution/size equivalent.
    return GeometryImpl.from_wire({
        "@CLASS": "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl",
        "granularity": "SINGLE", "scalar": False, "empty": False, "universal": False,
        "dimensions": [{
            "@CLASS": "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl$DimensionImpl",
            "type": "SPACE", "regular": True, "dimensionality": 2, "shape": [5, 4],
            "generic": False, "coverage": 1.0,
            "parameters": {
                "@CLASS": "org.integratedmodelling.klab.api.collections.impl.ParametersImpl",
                "delegate": {
                    "shape": "EPSG:3857 POLYGON ((200000 6000000,200000 6000400,200500 6000400,200500 6000000,200000 6000000))",
                    "sgrid": "100 m", "proj": "EPSG:3857"},
                "unnamedKeys": []}}]})


def run_elevation(client, *, context=None, timeout=120, report=print, region_definition="earth:Region"):
    """Create disposable test scopes or attach to an already-owned context.

    A wait timeout/interruption leaves remote scopes intact and prints job IDs.
    Explicit cleanup occurs only after verified success, for scopes created here.
    An attached context is never released by this function.
    """
    session = None
    if context is None:
        session = client.create_session(name="python-elevation-acceptance")
        report(f"Created session {session.id}")
        context = session.create_context(configuration={"name": "Python elevation grid", "persistence": "ONE_OFF"})
    report(f"Context {context.id}; retain this ID if interrupted")
    region_observable = client.reasoner.resolve_observable(region_definition)
    region_job = context.submit(ObservationImpl(
        urn="staging.storage:rectangle", name="rectangle", observable=region_observable,
        geometry=rectangle_geometry()))
    report(f"Region job {region_job.id}; scope {region_job.scope.get_context_id()}")
    region = region_job.result(timeout)
    if region.raw.get("resolvedCoverage", 0) < 1:
        raise JobFailedError(region_job.id, "Region coverage is incomplete")
    elevation_observable = client.reasoner.resolve_observable("geography:Elevation in m")
    focused = context.within(region)
    job = focused.submit(ObservationImpl(urn="", observable=elevation_observable))
    report(f"Elevation job {job.id}; resume in scope {focused.get_context_id()}")
    elevation = job.result(timeout)
    if elevation.raw.get("resolvedCoverage", 0) < 1 or elevation.units != "m":
        raise JobFailedError(job.id, "Elevation coverage/units do not meet the experiment contract")
    metres = elevation.fetch_data(range(20), curve="D2_YX")
    millimetre_observable = client.reasoner.resolve_observable("geography:Elevation in mm")
    millimetres = elevation.fetch_data(range(20), curve="D2_YX", semantics=storage_semantics(millimetre_observable))
    verify_elevation(metres.values, millimetres.values)
    report(f"Observation {elevation.id}: {elevation.observable.raw['urn']}; units {elevation.units}")
    report(f"Metres: {metres.values}; millimetres: {millimetres.values}")
    report("Verified finite elevation, missingness and mm = m * 1000")
    if session is not None:
        if not context.release():
            raise JobFailedError(job.id, "Disposable context release was not confirmed")
        if not session.release():
            raise JobFailedError(job.id, "Disposable session release was not confirmed")
    return elevation, metres, millimetres


def verify_elevation(metres, millimetres):
    if len(metres) != 20 or len(millimetres) != 20:
        raise AssertionError("Expected 20 cells on the 5 × 4 experiment grid")
    valid = 0
    for m, mm in zip(metres, millimetres):
        if m is None or mm is None:
            if m is not None or mm is not None:
                raise AssertionError("Unit conversion changed missingness")
            continue
        m, mm = Decimal(m), Decimal(mm)
        if not m.is_finite() or not mm.is_finite() or not -500 <= m <= 9000:
            raise AssertionError("Nonfinite or implausible terrain elevation")
        expected = m * 1000
        if abs(mm - expected) > max(Decimal("0.001"), abs(expected) * Decimal("0.000001")):
            raise AssertionError("Failed independent metres-to-millimetres invariant")
        valid += 1
    if valid == 0:
        raise AssertionError("No real scientific values retrieved")
