# Installed restoration and lifecycle verification

`tools/verify_restoration_matrix.py` reuses the explicit installed-byte/test/support
custody runner and executes separate groups for sync/async context/observation/provider
and Job saved references, owned HTTP restart/expiry/cancel/identity fencing, and public
provider/selection/shared-grant workflows. Nonempty groups and zero failures/errors/
skips are mandatory. Counts come from JUnit; prior output is never overwritten.

```text
python tools/verify_restoration_matrix.py --artifact-receipt RECEIPT.json --output-directory NEW_DIRECTORY
```

As with onboarding, `--capture-current-source` verifies current installed module bytes
against the checkout, not wheel/sdist or fresh dependency isolation. Both modes refuse
source imports and changed installed, executable-test, support/helper or runner bytes.
The shared runner preserves timeout diagnostics and scrubs inherited `KLAB_` settings;
it does not change product/API timeouts or repair native services.

An additional actual HTTP workflow creates public references, stops an owned durable
reference process, restarts it with the same explicit identity, and restores Context,
Job and Observation through an **optimized, fresh async SDK-only process**. It checks
exact interpreter/SDK imports, no simulator module in that process, exact finite cell
values, no gained ownership and no scientific submission, creation or remote release
during restoration/local close. The parent performs explicit current-owner cleanup.
Checks in the optimized helper use explicit exceptions, not removable Python asserts.

These controls distinguish local wait interruption from remote cancellation, expired
or released Jobs from confirmed cancellation, identity replacement from permitted
same-identity restart, read grants from ownership, and explicit worker recomputation
from replay. The independent fixture's SQLite checkpoint and ephemeral expiry controls
are **not native service incarnation, retention, durable history, permissions/TTL or
production worker recovery evidence**. Real compatibility and scientific approval
remain separate external gates; no release or final original-row acceptance follows.
