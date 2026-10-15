"""Private configuration and TEST controls, not a substitute for public SDK calls.

The recorder copies this module beside the notebooks. No credentials are written
into notebook source, output, or video. Scientific computation stays in the cells.
"""
import json
import os
from pathlib import Path
import time

from klab_client import (ConnectionIdentity, DeploymentProfile, HubCertificateProvider,
                         IssuedTokenProvider)


def state():
    return Path(os.environ["KLAB_NOTEBOOK_STATE"])


def profile():
    return DeploymentProfile.load(state() / "connection-profile.json")


def certificate():
    return HubCertificateProvider(state() / "engine.cert")


def issued_token(*, collaborator=False):
    config = json.loads((state() / "scientist.json").read_text())
    principal = "other-scientist" if collaborator else config["username"]
    token = config["other_token"] if collaborator else config["token"]
    return IssuedTokenProvider(ConnectionIdentity(principal, "python-local-test-hub"),
                               {url: token for url in config["urls"].values()})


def track_session(session):
    """Allow the owned recorder to clean up even after a cell fails."""
    path = Path("owned-sessions.json")
    ids = json.loads(path.read_text()) if path.exists() else []
    item = {"id": session.id, "principal": session.client.agent_name}
    if item not in ids:
        path.write_text(json.dumps([*ids, item]))


def reset_field():
    (state() / "controls/reset-field").write_text("reset the serial 20-cell TEST field")


def hold_work():
    for name in ("entered", "release"):
        (state() / "controls" / name).unlink(missing_ok=True)


def release_work():
    (state() / "controls/release").write_text("release the owned TEST computation")


def wait_for_work(job):
    until = time.monotonic() + 15
    while not (state() / "controls/entered").exists():
        if time.monotonic() >= until or job.status().status in {"FINISHED", "ABORTED", "INTERRUPTED", "EMPTY"}:
            raise AssertionError("Controlled computation did not enter its wait gate")
        time.sleep(.02)


def plot_grid(observation, *, title="Values returned by k.LAB", curve="D2_YX"):
    """Show native cell reads. This fixture's XY origin is south-west, not TIFF row order."""
    import matplotlib.pyplot as plt
    import numpy as np
    values = observation.fetch_data(range(20), curve=curve).values
    array = np.array([float(v) if v is not None else np.nan for v in values]).reshape(4, 5)
    fig, ax = plt.subplots(figsize=(8, 4), constrained_layout=True)
    image = ax.imshow(array, origin="lower", cmap="viridis")
    for y in range(4):
        for x in range(5):
            label = "missing" if np.isnan(array[y, x]) else f"{array[y, x]:g}"
            ax.text(x, y, label, ha="center", va="center", color="white", fontsize=12,
                    bbox={"facecolor": "black", "alpha": .35, "edgecolor": "none"})
    ax.set(xlabel="Grid column", ylabel="Grid row (south to north)", title=title)
    fig.colorbar(image, ax=ax, label=observation.units)
    plt.show()
    return values


def plot_recorded_links(view):
    """Draw only actual returned edges. Group identical parallel edges with a count."""
    from collections import Counter
    import math
    import matplotlib.pyplot as plt
    nodes = {node.id: node for edge in view.links for node in (edge.source, edge.target)}
    positions = {id: (math.cos(i * 2 * math.pi / len(nodes)), math.sin(i * 2 * math.pi / len(nodes)))
                 for i, id in enumerate(sorted(nodes))}
    fig, ax = plt.subplots(figsize=(10, 5), constrained_layout=True)
    grouped = Counter((edge.source.id, edge.target.id, edge.relationship) for edge in view.links)
    for index, ((source, target, relation), count) in enumerate(grouped.items()):
        x, y = positions[source]
        u, v = positions[target]
        bend = .13 * (index % 3 - 1)
        ax.annotate("", xy=(u * .83, v * .83), xytext=(x * .83, y * .83),
                    arrowprops={"arrowstyle": "->", "color": "#477f91", "lw": 2,
                                "connectionstyle": f"arc3,rad={bend}"})
        label = relation + (f" ×{count}" if count > 1 else "")
        ax.text((x+u)/2, (y+v)/2 + bend, label, fontsize=9, ha="center",
                bbox={"facecolor": "white", "alpha": .9, "edgecolor": "none"})
    for id, node in nodes.items():
        x, y = positions[id]
        label = f"{node.kind} {id}"
        if node.activity_type:
            label += f"\n{node.activity_type}: {node.outcome}"
        ax.text(x, y, label, ha="center", va="center", fontsize=10,
                bbox={"boxstyle": "round,pad=.6", "facecolor": "#dcefeb", "edgecolor": "#4d8a7d"})
    ax.set(xlim=(-1.7, 1.7), ylim=(-1.5, 1.5), title="Recorded neighbors of this observation — partial, one hop")
    ax.axis("off")
    plt.show()
