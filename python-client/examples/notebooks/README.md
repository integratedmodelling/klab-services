# Python-client notebook walkthroughs

These are executable percent-format Python notebooks, not scripts that print
prewritten success messages. Open them as cells in a compatible editor, or use
the recording tool to generate ordinary `.ipynb` files and run them in Jupyter.

The series uses **actual services on an owned local TEST deployment**. Scientific
models are synthetic known-answer examples, not real terrain datasets. Credentials
are loaded privately by `tools/notebook_demos/notebook_setup.py`; that helper also
resets the serial TEST model and tracks only the sessions created by these examples.
All scientific requests, restoration, export and analysis are visible in the cells.

| Notebook | What you do | Contribution |
| --- | --- | --- |
| [01 — Connect](01_connect.py) | Connect with certificate and issued-token providers; inspect service catalog/readiness, models, saved profile and renewal | #93 |
| [02 — Request an observation](02_observe.py) | Draw the requested area/grid; compute and read a field; convert units; choose scenario/namespace/project/observer; configure a grid | #84, #85 |
| [03 — Running Jobs](03_jobs.py) | Keep a timed-out Job, restore it in another client, confirm cancellation; retrieve a completed result; handle a missing-model failure | #84, #86 |
| [04 — Reopen saved work](04_reopen.py) | Save context/observation/Runtime Job/Resolver Job; restart the actual Python kernel; recover original identities and values without scientific resubmission | #86 |
| [05 — Native files](05_exports.py) | Discover formats; export/draw GeoJSON; export/open GeoTIFF; inspect actual numeric data, metadata and overwrite protection | #92, carried component patch |
| [06 — Analyze and trace](06_analyze.py) | Select and plot data with xarray; inspect distribution; draw actual recorded neighbors and activities; refresh and associate the result | Current local analysis/provenance work |
| [07 — Share work](07_share.py) | Use two authenticated identities; read explicitly shared results; deny private restoration/export/provenance while preserving owner access | Cross-cutting #84/#86/#92/#93 and local provenance |

## Execute and record

Optional tooling, separate from SDK runtime dependencies:

```powershell
python -m pip install '.[dev,local,analysis,demo]' jupyterlab nbclient nbconvert matplotlib playwright
python -m playwright install chromium
```

Use `tools/run_export_acceptance.py` with the existing explicit Java/worldview/
component/server pins and **fresh** `--state-dir` and `--notebook-output` directories.
It prepares/starts the owned stack, runs its GeoJSON regression, then invokes:

```powershell
python tools/record_notebook_demos.py --state-dir '<running-owned-state>' --output '<fresh-output>'
```

`--notebook-check-only` on the stack runner, or `--check-only` on the recorder,
executes the notebooks and their assertions without browser recording.

The recorder first executes every notebook in fresh kernels. Restart markers
split the saved-work example into separate actual Python processes. It then
opens clean notebooks in Jupyter, executes their cells through the browser,
records those browser frames and exports the successfully executed notebooks.
During the saved-work recording it uses Jupyter's actual restart action, and the
notebook asserts that the Python PID changed. Assertions compare known values,
units, selection counts, identities, zero/missingness and permission outcomes.

Notebook loading is trimmed. Service execution and deliberate reading pauses
remain; duration is not a performance measurement. The output manifest records
source hashes, executed cell counts, kernel restarts and decoded video hashes.
Actual plots/data tables are included in the downloadable executed notebooks.

## Safety and limits

- The Jupyter server binds only to `127.0.0.1`, uses a random authentication token,
  and serves only notebook workspaces—not the private stack root.
- Credentials and certificate/enrollment files remain outside public output.
  Known private values are checked against selected output before publication.
- Test-owned sessions are tracked for cleanup, including notebook failures.
  Stack shutdown stays in the outer runner's `finally` block.
- The slow model's TEST gate expires after 30 seconds. Run the timeout/restore/
  cancellation cell as a whole; it is not a long-running production model.
- This does not certify production Hub onboarding, independent installations,
  restart durability, complete provenance or an upstream component release.
- Backend corrections are shown through their user-visible effects. These
  recordings complement, not replace, the SDK/Java/scientific regression tests.

The next improvement is a real-world model/dataset on a suitable deployment,
not a new SDK plotting framework. The series deliberately keeps standard xarray,
Matplotlib and pandas tools visible.
