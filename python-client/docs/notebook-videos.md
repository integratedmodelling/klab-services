# Use the Python client: notebook walkthroughs

Seven notebooks take you from connecting to a deployment through computation,
saved work, exports and analysis. The videos show the cells actually running in
Jupyter. You can also read the executed notebooks and download their source.

**Environment:** real services on an owned local TEST deployment, with synthetic
known-answer models. The spatial field is a small 5 × 4 grid, not real terrain.
Credentials are loaded privately and are not included in the downloads. Notebook
loading is trimmed; execution and reading pauses remain. These are not benchmarks.

## 1. Connect and see your services

Use a certificate or issued credentials, see which services are ready, inspect
available models, and save your deployment profile for the next session. **#93.**

<video controls playsinline preload="metadata" poster="media/notebooks/01_connect.png" src="media/notebooks/01_connect.mp4"></video>

[Read the executed notebook](media/notebooks/01_connect.html) ·
[Download notebook](media/notebooks/01_connect.ipynb) ·
[Python cells](../examples/notebooks/01_connect.py)

## 2. Choose an area and request an observation

Draw the requested grid, compute a field, read its values, and request a unit
conversion. Then choose a scenario, namespace, project, observer and context grid.
The scenario comparison shows the selected model changing the result. **#84, #85.**

<video controls playsinline preload="metadata" poster="media/notebooks/02_observe.png" src="media/notebooks/02_observe.mp4"></video>

[Read the executed notebook](media/notebooks/02_observe.html) ·
[Download notebook](media/notebooks/02_observe.ipynb) ·
[Python cells](../examples/notebooks/02_observe.py)

## 3. Keep control of a running Job

Stop waiting without losing the Job, restore its reference in a second client,
and cancel it. Then retrieve a completed result and handle a request that has no
executable model. The TEST model uses an explicit wait gate for the cancellation
demonstration. **#84, #86.**

<video controls playsinline preload="metadata" poster="media/notebooks/03_jobs.png" src="media/notebooks/03_jobs.mp4"></video>

[Read the executed notebook](media/notebooks/03_jobs.html) ·
[Download notebook](media/notebooks/03_jobs.ipynb) ·
[Python cells](../examples/notebooks/03_jobs.py)

## 4. Close Python and reopen the same work

Save a context, observation, Runtime Job and Resolver Job. Restart the actual
Python kernel, acquire credentials again, and reopen the original work. Compare
process IDs, observation IDs and the values before and after restarting. **#86.**

<video controls playsinline preload="metadata" poster="media/notebooks/04_reopen.png" src="media/notebooks/04_reopen.mp4"></video>

[Read the executed notebook](media/notebooks/04_reopen.html) ·
[Download notebook](media/notebooks/04_reopen.ipynb) ·
[Python cells](../examples/notebooks/04_reopen.py)

## 5. Take results into other tools

Discover export formats, download GeoJSON and draw its boundary. Download a
numerical GeoTIFF and plot the data read from that file. Check its units,
coordinates and missing cells, and keep an existing file from being overwritten.
**#92 and the branch-carried GeoTIFF correction.**

<video controls playsinline preload="metadata" poster="media/notebooks/05_exports.png" src="media/notebooks/05_exports.mp4"></video>

[Read the executed notebook](media/notebooks/05_exports.html) ·
[Download notebook](media/notebooks/05_exports.ipynb) ·
[Python cells](../examples/notebooks/05_exports.py)

## 6. Explore the result and its recorded origins

Select part of a field with xarray, compare the selection to the whole grid, and
inspect the distribution. Draw the relationships returned by the server and look
for the recorded numerical activity. The graph is a partial neighborhood, not
complete lineage or a reproducibility recipe. **Analysis/provenance work in #94.**

<video controls playsinline preload="metadata" poster="media/notebooks/06_analyze.png" src="media/notebooks/06_analyze.mp4"></video>

[Read the executed notebook](media/notebooks/06_analyze.html) ·
[Download notebook](media/notebooks/06_analyze.ipynb) ·
[Python cells](../examples/notebooks/06_analyze.py)

## 7. Share selected work and keep private results private

Create shared and private contexts. A second scientist supplies their own
credentials and reads the shared observation. The private reference does not
grant access: restoration, export and provenance requests are denied, while the
owner can still retrieve the result. **Cross-cutting client/server behavior.**

<video controls playsinline preload="metadata" poster="media/notebooks/07_share.png" src="media/notebooks/07_share.mp4"></video>

[Read the executed notebook](media/notebooks/07_share.html) ·
[Download notebook](media/notebooks/07_share.ipynb) ·
[Python cells](../examples/notebooks/07_share.py)

## Run the examples yourself

Start with the [notebook setup and reproduction guide](../examples/notebooks/README.md).
The [manifest](media/notebooks/manifest.json) records execution and video hashes.
The downloadable setup helper reads credentials from a private owned TEST state;
it does not include certificates, tokens or enrollment material.

The contributions target `feature/python-client`. Analysis/provenance and the
complete polish pass are included in PR #94. The component
patch are labeled separately; these recordings do not turn them into published
releases. Production Hub onboarding, independent separate-deployment acceptance,
server-crash recovery and complete provenance are not demonstrated.

[Back to release notes](release-preview.md) · [Review and test evidence](polish-review.md)
