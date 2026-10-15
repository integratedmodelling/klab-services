# %% [markdown]
# # Share selected work. Keep private work private.
# Two separately authenticated scientists use the same deployment. The owner
# creates shared and private contexts; the collaborator reopens only the permitted
# observation. This is controlled local access testing, not separate-deployment certification.

# %%
import pandas as pd
from IPython.display import display
from klab_client import connect, ObservationRequest, RectangularGeometry, save_handle, load_handle
from klab_client.errors import AuthorizationError, MissingAssetError
from notebook_setup import profile, issued_token, track_session

owner_connection = connect(profile(), credentials=issued_token())
owner = owner_connection.client
session = owner.create_session(name="Notebook: shared and private results")
track_session(session)
print("Owner:", owner_connection.identity.principal)

# %% [markdown]
# ## Create a context with an explicit access list
# Access rules are enforced by the server. A saved reference does not grant access.

# %%
shared = session.create_context(configuration={
    "persistence": "EXPLICIT_ACTION", "name": "Shared study area",
    "accessRights": {
        "@CLASS": "org.integratedmodelling.klab.api.authentication.ResourcePrivileges",
        "isPublic": False, "allowedUsers": ["python-scientist", "other-scientist"],
        "allowedGroups": [], "excludedGroups": [], "excludedUsers": [], "allowedServices": [],
    },
})
private = session.create_context(configuration={"persistence": "EXPLICIT_ACTION", "name": "Private study area"})
area = RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)
shared_region = shared.observe(ObservationRequest("earth:Region", geometry=area)).result(120)
private_region = private.observe(ObservationRequest("earth:Region", geometry=area)).result(120)
shared_result = shared.within(shared_region).observe("geography:Elevation in m").result(120)
private_result = private.within(private_region).observe("geography:Elevation in m").result(120)
save_handle(shared_result, "shared.json")
save_handle(private_result, "private.json")

# %% [markdown]
# ## A second scientist reopens the shared observation
# The collaborator supplies their own credentials. The observation identity and
# values match the owner's result, without creating a new computation.

# %%
guest_connection = connect(profile(), credentials=issued_token(collaborator=True))
guest = guest_connection.client
positive = guest.create_session(name="Notebook: authenticated collaborator")
track_session(positive)
reopened = guest.restore_handle(load_handle("shared.json"))
assert reopened.id == shared_result.id
assert reopened.fetch_data([0]).values[0] == 123.25
display(pd.DataFrame([
    {"scientist": owner_connection.identity.principal, "observation": shared_result.id,
     "value (m)": shared_result.fetch_data([0]).values[0]},
    {"scientist": guest_connection.identity.principal, "observation": reopened.id,
     "value (m)": reopened.fetch_data([0]).values[0]},
]))

# %% [markdown]
# ## The same credentials cannot reopen private work
# Successful access above is the positive control: this is not a broken login.

# %%
try:
    guest.restore_handle(load_handle("private.json"))
except (AuthorizationError, MissingAssetError) as denied:
    print("Private observation not accessible to this scientist:", type(denied).__name__)
else:
    raise AssertionError("Private work was exposed to the collaborator")
assert owner.restore_handle(load_handle("private.json")).id == private_result.id
print("The owner can still reopen the private observation.")

# %% [markdown]
# ## Export and provenance endpoints enforce access too
# These direct checks use the known private observation identity, so they test the
# endpoints themselves rather than stopping at the failed handle restoration.

# %%
from klab_client import Context, export_for_analysis, fetch_provenance
from klab_client.dto import observation_from_wire

foreign_scope = Context(guest, private_result._context.configuration,
                        token=private_result._context.get_context_id())
foreign = observation_from_wire(private_result.raw, foreign_scope)
denials = []
for name, operation in [
    ("GeoTIFF export", lambda: export_for_analysis(foreign, "denied.tif")),
    ("Recorded origins", lambda: fetch_provenance(foreign)),
]:
    try:
        operation()
    except (AuthorizationError, MissingAssetError) as denied:
        denials.append({"request": name, "result": type(denied).__name__})
    else:
        raise AssertionError("Private endpoint returned data")
display(pd.DataFrame(denials))
assert len(denials) == 2
assert positive.release()
guest_connection.close()
assert session.release()
owner_connection.close()
