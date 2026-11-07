# Installed onboarding and routing verification

The development-only `tools/verify_onboarding_matrix.py` reruns three independent
groups: public connection/provider policy, isolated local HTTP certificate exchange
and forbidden-origin boundaries, and owned simulator HTTP expiry/mixed-provider
outage/recovery/identity/no-replay workflows. Each group must execute tests with zero
failures, errors or skips; counts come from JUnit, never expected constants.

```text
python tools/verify_onboarding_matrix.py --artifact-receipt RECEIPT.json --output-directory NEW_DIRECTORY
```

Run from an environment importing the installed SDK, worker and independent simulator.
The explicit artifact receipt must contain their exact `source_raw_sha256` module
hashes. Source-tree imports, changed/extra/missing modules, changed test bytes and
existing output directories refuse. Failed logs/JUnit are retained without a success
receipt. `--capture-current-source` instead compares installed bytes with the current
checkout; this mode explicitly does **not** establish wheel/sdist or fresh dependency
isolation. CI uses this mode for its fresh current installation rather than treating
a Windows artifact receipt as cross-platform wheel-byte authority.

The runner removes inherited `KLAB_` variables from its children and starts no native
Modeler, Hub or IDE. Its suites use owned loopback services, synthetic credentials
and an isolated process for origin/redirect tests. They verify Python expiry,
readiness, exact provider selection, identity fencing, no implicit failover/replay,
secret-free saved metadata and owned lifecycle cleanup. They do not repair services
or acquire production credentials.

Certificate/token substitution and the local certificate exchange are reference
contracts, **not genuine issuer or licence approval**. Real issuer, permission,
deployment and mixed native-version support evidence remain external; passing this
matrix neither authorizes release nor grants final original-row/scientific acceptance.
Reviewed onboarding mappings in `operation-workflow-mappings.json` retain these
authority distinctions and require matching executed test bytes.
