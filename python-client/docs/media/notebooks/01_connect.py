# %% [markdown]
# # Connect to k.LAB
# Load a deployment profile, connect, and see which services are ready.
# This notebook uses a local TEST authority and real local services, not a
# production Hub. `notebook_setup` loads credentials privately; it does not fake
# any service responses. No credential value is displayed or saved in this file.

# %%
import pandas as pd
from IPython.display import display
from klab_client import connect, DeploymentProfile
from notebook_setup import profile, certificate, issued_token

deployment = profile()
display(pd.DataFrame([{"service": s.service_type, "URL": s.url}
                      for s in deployment.services]))

# %% [markdown]
# ## Connect with an ENGINE certificate
# The provider exchanges the certificate for service credentials. The connection
# checks the configured services and initializes the client for this caller.

# %%
connection = connect(deployment, credentials=certificate())
connection.wait_ready(timeout=30)
display(pd.DataFrame(connection.catalog.summary())[
    ["type", "trusted", "reachable", "available", "operational"]])
print("Signed in as:", connection.identity.principal)

# %% [markdown]
# ## See what is available
# The catalog and readiness explanation come from the connected deployment.

# %%
display(connection.explain_readiness())
models = connection.client.resources.list("MODEL")
display(models[:8])
assert models

# %% [markdown]
# ## Keep your profile, not your credentials
# Save the deployment choices so another Python process can load them.

# %%
deployment.save("deployment.json")
reloaded = DeploymentProfile.load("deployment.json")
assert reloaded.to_dict() == deployment.to_dict()
print("Saved profile:", reloaded.name)
print("Required services:", ", ".join(reloaded.required_services))
connection.refresh()
connection.renew()
assert all(row["operational"] for row in connection.catalog.summary())
connection.close()

# %% [markdown]
# ## Already have issued credentials?
# Use the token provider instead. Secrets still come from private configuration,
# not the notebook. This is a new connection to the same deployment.

# %%
with connect(reloaded, credentials=issued_token()) as token_connection:
    token_connection.wait_ready(timeout=30)
    display(pd.DataFrame(token_connection.catalog.summary())[
        ["type", "available", "operational"]])
    assert token_connection.identity.principal == "python-scientist"
print("Both connections closed locally; no remote work was released.")
