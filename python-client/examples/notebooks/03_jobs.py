# %% [markdown]
# # Keep control of a running Job
# Stop waiting, keep the reference, return to the same Job, or cancel it. This
# notebook uses a TEST model with an explicit wait gate so the work stays active
# long enough to demonstrate these actions. The services and Job outcomes are real.

# %%
import pandas as pd
from IPython.display import display
from klab_client import connect, ObservationRequest, RectangularGeometry, save_handle, load_handle
from klab_client.errors import WaitTimeout, JobCancelledError, JobFailedError
from notebook_setup import (profile, certificate, track_session, hold_work,
                            wait_for_work, release_work)

connection = connect(profile(), credentials=certificate())
client = connection.client
session = client.create_session(name="Notebook: running Jobs")
track_session(session)
context = session.create_context()
region = context.observe(ObservationRequest("earth:Region",
    geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
focused = context.within(region)

# %% [markdown]
# ## A wait timeout does not discard the Job
# Save the reference, restore it through a second client, then cancel the same
# running Job. These cells run consecutively: the TEST gate has a 30-second limit.

# %%
hold_work()
pending = focused.observe("geography:BathymetricDepth in m")
wait_for_work(pending)
try:
    pending.result(timeout=.1)
except WaitTimeout as stopped:
    assert stopped.job is pending
    print("Stopped waiting. Job", stopped.job.id, "is still", pending.status().status)
save_handle(pending, "pending.json")
with connect(profile(), credentials=certificate()) as second:
    resumed = second.client.restore_handle(load_handle("pending.json"))
    assert resumed.id == pending.id
    print("Second client restored Job", resumed.id)
    assert resumed.cancel()
    try:
        resumed.result(timeout=5)
    except JobCancelledError:
        print("Server confirmed:", resumed.status().status)
    else:
        raise AssertionError("Cancellation was not confirmed")
release_work()
assert pending.status().status == "INTERRUPTED"

# %% [markdown]
# ## You can also keep waiting and receive the original result
# A short wait budget is separate from the Job's lifetime.

# %%
job = focused.observe("geography:Elevation in m")
try:
    job.result(timeout=0)
except WaitTimeout as stopped:
    print("Keep this Job reference:", stopped.job.id)
result = job.result(timeout=120)
display(pd.DataFrame([{"Job": job.id, "status": job.status().status,
                      "observation": result.id, "first value (m)": result.fetch_data([0]).values[0]}]))
assert result.fetch_data([0]).values[0] == 123.25
assert job.cancel() is False  # The completed result is not a running Job.
assert job.result(120).id == result.id

# %% [markdown]
# ## A request with no executable model gives you an error to handle
# This deployment has no public slope model. The project-private version is
# available only when explicitly selected, as shown in the request notebook.
# Processing can finish without producing a usable observation; `result()` checks
# the returned scientific result as well as the Job's processing status.

# %%
failed = focused.observe("geography:Slope in degree_angle")
try:
    failed.result(timeout=120)
except JobFailedError as error:
    print("No result was produced for this request.")
    print(type(error).__name__ + ":", str(error))
    print("Job processing status:", failed.status().status)
else:
    raise AssertionError("The missing-model Job unexpectedly succeeded")
assert session.release()
connection.close()
