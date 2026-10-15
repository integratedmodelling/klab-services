# Show the work, not the test report

The first slide-based clips were rejected. Do not use them as the release demos.
The replacement should show someone working in Python, with readable code and
outputs that explain why they would use the client.

## Full series now in execution

The user requested coverage of every major PR capability, across multiple videos.
Seven [executable walkthroughs](../examples/notebooks/README.md) now cover connection,
typed requests/scientific reads, Job control, fresh-kernel saved work, native
exports, analysis/recorded origins and two-identity sharing. Their recorder runs
all assertions before recording real Jupyter execution against the owned stack.
Publication waits for actual execution, decoding, credential checks and cleanup;
source files alone are not presented as completed videos.

## Start with one recording

**Explore an exported result in a notebook.** Open the file with the SDK, see
the xarray Dataset, plot it, select part of the grid, and inspect the selected
values. Record actual cell execution in Jupyter, not a re-created terminal.

The runnable source is
[`examples/explore_exported_raster.py`](../examples/explore_exported_raster.py).
It uses normal xarray and Matplotlib. No SDK visualization feature is needed.
The first local prototype uses the actual, verified synthetic acceptance TIFF;
its 5 × 4 grid is a workflow demonstration, not a landscape or scientific case study.
Check the input bytes, execute every cell, require three actual plot outputs and
check the numerical selection before recording. Do not calculate a linear mean
of the aspect angles.

Review this prototype before spending time producing a whole set in the same
style. It demonstrates local analysis only, not service connection or computation.

## The stronger story

Aim for two or three recordings, long enough to see the work:

1. **Ask for a result.** Connect to a deployment, define a real area, request an
   available observation, run the Job and plot the returned field. Show the area,
   request and actual output. No ellipses in the code being executed.
2. **Return to yesterday's work.** Save an observation reference, end the first
   Python process, restore it in a fresh one and continue the analysis. Show the
   same observation identity and no second scientific submission.
3. **Answer a small question.** Select a region or value range, show the selected
   cells, calculate a meaningful statistic, and export a result for another tool.
   The question must suit the measured quantity; do not invent scientific meaning
   just because a plot looks attractive.

Use brief plain-English narration/captions to explain choices. Keep test counts,
coverage, artifact digests and implementation caveats in the linked review notes,
not as the subject of the footage. Name the dataset and any controlled/synthetic
setup once, visibly. Crop to the notebook, never private configuration windows.

## What would make it more compelling?

**Better input and an actual question, first.** Current numerical acceptance models
are either constant fields or a 20-cell coordinate-coded field. Enlarging that
fixture alone would not make it a real-world example. A connected deployment with
an accessible, spatially varied model/resource is needed for a genuine landscape
story. Confirm its location, units, missingness, export support and provenance
before recording. Do not substitute an unrelated downloaded map and imply the
client computed it.

Additional functional tests should verify the same demo code: region/result
identity, spatial selection, unit-aware statistics, exported values, fresh-process
restoration without resubmission and owned cleanup. These should be reusable
examples, not a second execution framework or a renderer-specific success script.

Potential later conveniences are a clearer observation summary and easier
discovery of observations available on a deployment. Recommend these separately
if the real workflow exposes a gap. An SDK map widget, dashboard framework or
plotting abstraction is not required for this notebook demonstration.
