# Saved executable PaL checks

The work is preserved on `feature/pal-assumption-explicit-demo`. This includes
the inherited archive inventories, parser probe, Groovy equation checks, adaptation
generator, numerical cases, failures, audit and scientific/demo specifications.
See [README](README.md), [adaptation evidence](RADIATION_ADAPTATION.md) and
[the scenario/demo plan](ASSUMED_EXPERIMENT_AND_DEMOS.md).

## Real-input functional prerequisite test

`tests/functional/test_pal_real_input_prerequisites.py` reopens the actual pinned
raster inputs and repeats the two-patch native-center input QA. It checks hashes,
coordinates, CRS, units absence, nodata, valid zero, mask counts and exact category
pairs. No network, services, scientific jobs or raw-data redistribution is involved.
This is an executable **input prerequisite**, not a finished Python-client-to-Java
PaL experiment. Passing it must not count as scientific reproduction acceptance.

Install the existing `dev,analysis` extras. Prepare a private fixture directory
containing these four files, obtained using the source/query recipes in
[assumed-input-receipts.json](assumed-input-receipts.json):

- `com_50m_wgs84.tif`
- `com2_50m_wgs84.tif`
- `radiation-equal_patch.tif`
- `radiation-mixed_patch.tif`

The test verifies their exact recorded hashes before opening them. A changed WCS
response fails; do not update the pin just to make it pass. No automatic download
or substitution of synthetic data occurs. Missing fixtures/dependencies fail the
explicitly selected suite; the default offline test run excludes it via `live`.
The marker is the repository's existing opt-in convention, not a claim this test
contacts a running deployment.

From `python-client`, PowerShell:

```powershell
$env:PYTHONPATH = "$PWD/src;$PWD/tools"
$env:KLAB_PAL_INPUT_DIR = 'C:/path/to/private/pinned-inputs'
python -m pytest -o addopts= -o junit_family=legacy -m live tests/functional/test_pal_real_input_prerequisites.py -q --junitxml=C:/path/to/evidence/pal-input-qa.xml
```

Expected: **two passing prerequisite cases**, equal and mixed patches. JUnit
properties explicitly identify the restricted claim and radiation input identity.
The fixture/output directories are user-selected; never publish private paths or
payloads without a rights/privacy check.

## What is not yet saved as a passing scientific functional test

No Java-backed PaL scientific run has occurred. It would be misleading to add a
passing/skip-only test that purports to execute one. The next real acceptance test
needs admitted project-local operational definitions and scenario bindings, frozen
normalization/grid/masks/tolerances, authorized resources and independent expected
fields. It should then use existing Client/Job/evidence APIs to execute and compare
actual results. The current input test does not lift those gates or authorize
Java/shared-worldview changes. The original parser and arithmetic probes remain
separate research checks; this test does not rerun them or imply deployment fidelity.
