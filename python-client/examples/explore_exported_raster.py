# %% [markdown]
# # Explore a k.LAB result in Python
# This example starts with a GeoTIFF already exported by k.LAB. It does not
# connect to a server or recompute the observation. Install `.[analysis]`,
# Matplotlib and JupyterLab, then set KLAB_DEMO_RASTER to the exported input file.
# The local preview uses the actual 5 x 4 synthetic acceptance export, not terrain.

# %%
import os
from pathlib import Path

import matplotlib.pyplot as plt
from IPython.display import display

from klab_client import open_raster

path = Path(os.environ.get("KLAB_DEMO_RASTER", "field.tif"))
result = open_raster(path)
ds = result.dataset
display(ds)

# %% [markdown]
# ## See the whole result
# Mask missing cells before plotting. Coordinates come from the file; the
# measurement unit is separate from the coordinate reference system.

# %%
values = ds.value.where(ds.valid)
unit = result.descriptor.units or "unit not supplied"
fig, ax = plt.subplots(figsize=(9, 5), constrained_layout=True)
values.plot(ax=ax, cmap="viridis", cbar_kwargs={"label": unit})
ax.set_title("The exported observation")
ax.set_aspect("equal")
plt.show()

# %% [markdown]
# ## Select part of the grid
# Keep the right-hand portion of this result. This is an ordinary xarray
# selection, working locally on the exported data.

# %%
selection = values.isel(x=slice(values.sizes["x"] // 2, None))
display(selection)
fig, ax = plt.subplots(figsize=(8, 4), constrained_layout=True)
selection.plot(ax=ax, cmap="viridis", vmin=float(values.min()),
               vmax=float(values.max()), cbar_kwargs={"label": unit})
ax.set_title("Selected columns, with the original coordinates")
ax.set_aspect("equal")
plt.show()

# %% [markdown]
# ## Inspect the values
# Count the available cells and inspect the range. We do not average the angles
# in the synthetic aspect example: a linear mean is not a circular statistic.

# %%
display({
    "valid cells": int(ds.valid.sum()),
    "missing cells": int((~ds.valid).sum()),
    "minimum": float(values.min()),
    "maximum": float(values.max()),
    "measurement unit": unit,
    "selected valid cells": int(selection.count()),
})
fig, ax = plt.subplots(figsize=(9, 3.5), constrained_layout=True)
values.plot.hist(ax=ax, bins=8, color="#367d94", edgecolor="white")
ax.set_title("Distribution of the exported values")
ax.set_xlabel(unit)
ax.set_ylabel("Cells")
plt.show()

# %% [markdown]
# The plot is a view of the exported result, not a new scientific model. To use
# a different result, change the input file and rerun the notebook. Reconnecting
# to a saved observation and creating this export are covered in the companion
# analysis and saved-work guides.
