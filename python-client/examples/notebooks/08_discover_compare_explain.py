# %% [markdown]
# # Discover, compare, and explain four scientific experiments
# This notebook executes new work against an explicitly authorized deployment.
# It requires an independently verified four-cell fixture, not copied results.
# Missing prerequisites fail rather than generate illustrative scientific output.

# %%
import os
from pathlib import Path
import sys
sys.path.insert(0, os.environ["KLAB_MILESTONE_TOOLS"])
from workflows import milestone
from workflows.scientific_checks import require
require(os.environ.get("KLAB_CONNECTED_ALLOW_OWNED_WORK") == "1", "Authorize this notebook's new computations first")
fixture_path = Path(os.environ["KLAB_MILESTONE_FIXTURE"])
profile_path = Path(os.environ["KLAB_MILESTONE_PROFILE"])
certificate_path = Path(os.environ["KLAB_MILESTONE_CERTIFICATE"])
fixture = milestone.load_fixture(fixture_path)
factory = milestone.factory_from_paths(profile_path, certificate_path, allow_owned_work=True)

# %% [markdown]
# ## Discover an explicitly selected authority identity and inspect the model
# The selected authority need not influence the numeric field. Model advertisement
# is not proof of selection; recorded activities are checked separately below.

# %%
from IPython.display import display
discovery = milestone.discover(factory, fixture)
display(discovery)

# %% [markdown]
# ## Compute and independently validate four fields, sequentially and in parallel
# Every exported cell, mask, unit, precision and coordinate is checked by the shared
# workflow. Timing here is a single demonstration, not the full throughput protocol.

# %%
sequential = milestone.run_case_batch(factory, fixture, Path("sequential"), workers=1)
parallel = milestone.run_case_batch(factory, fixture, Path("parallel"), workers=4)
require(sequential["matrix_verified"] and parallel["matrix_verified"], "All real scientific matrix cells must validate")
display([{ "experiment": name, **record["raster"], "recorded_evidence": record["evidence"] }
         for name, record in sequential["processed"].items()])
display({"sequential_seconds": sequential["complete_seconds"], "parallel_seconds": parallel["complete_seconds"],
         "note": "single notebook batches, not throughput capacity"})

# %% [markdown]
# ## Plot only this notebook's actual native exports

# %%
import matplotlib.pyplot as plt
from klab_client import open_raster
figure, axes = plt.subplots(2, 2, figsize=(10, 8))
for axis, case in zip(axes.flat, fixture.cases):
    analysis = open_raster(Path("sequential") / (case.experiment.name + ".tif"))
    field = analysis.dataset.value.where(analysis.dataset.valid)
    image = axis.imshow(field.values, origin="upper")
    axis.set_title(case.experiment.name + " (" + case.oracle.units + ")")
    figure.colorbar(image, ax=axis)
plt.tight_layout()
display(figure)
plt.close(figure)

# %% [markdown]
# ## Restore the original observations in a fresh Python process
# Authentication is reacquired separately. No observations are resubmitted.
# Remote contexts remain retained for explicit owner-managed follow-up.

# %%
restored = milestone.launch_restore(fixture_path, Path("sequential/batch.json"), Path("restored"), profile_path, certificate_path)
require([r["observation_handle"] for r in restored["results"]] ==
        [r["result_handles"][-1] for r in sequential["batch"]["outcomes"]], "Restored identities changed")
display(restored["results"])
milestone.write_json("notebook-run.json", {"version": 1, "run_id": sequential["run_id"],
    "identity": sequential["identity"], "fixture_sha256": fixture.sha256,
    "parallel_run_id": parallel["run_id"], "restoration_run_id": restored["run_id"],
    "matrix_verified": True, "scientific_acceptance": False,
    "limits": "permission acceptance and the full throughput protocol are separate gates"})
