"""Opt-in SQLite custody of synthetic simulator state; never a Java persistence API.

JSON only, no pickle/commands/credentials. Bounded snapshots and an OS-owned single
writer lock; corrupt/wrong-tuple files fail closed rather than silently resetting.
"""
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import sys

MAX_BYTES = 4 * 1024 * 1024
APPLICATION_ID = 0x4B53494D
SIMPLE = ("sessions", "contexts", "imported_resources", "query_faults")
PAIRS = ("jobs", "observations")
COUNTERS = ("next_context", "next_job", "next_observation")


def snapshot(state):
    return {"version": 1, "scenario": state.scenario, "seed": state.seed,
        "simple": {name: deepcopy(getattr(state, name)) for name in SIMPLE},
        "pairs": {name: [[list(key), deepcopy(value)] for key, value in getattr(state, name).items()] for name in PAIRS},
        "counters": {name: getattr(state, name) for name in COUNTERS}, "worldview_id": state.worldview_id,
        "assets": [[list(key), deepcopy(value)] for key, value in state.authoring.assets.items()],
        "project_locks": deepcopy(state.authoring.project_locks),
        "workers": [[list(key), deepcopy(value)] for key, value in state.workers.workers.items()],
        "worker_tasks": deepcopy(state.workers.tasks), "next_worker_task": state.workers.next_task,
        "notifications": deepcopy(state.notifications.logs), "drafts": deepcopy(state.drafts.rows)}


def restore(state, saved, *, recovery=True):
    # Earlier reference checkpoints contain no retained-event history. Upgrade by
    # preserving all old state but leaving logs inactive until explicit activation.
    # No old Job/history is replayed or fabricated, and coverage is explicitly limited.
    if isinstance(saved, dict) and "notifications" not in saved:
        saved = {**saved, "notifications": {}}
    if isinstance(saved, dict) and "drafts" not in saved:
        saved = {**saved, "drafts": {}}
    expected = {"version", "scenario", "seed", "simple", "pairs", "counters", "worldview_id", "assets", "project_locks", "workers", "worker_tasks", "next_worker_task", "notifications", "drafts"}
    if (not isinstance(saved, dict) or set(saved) != expected or type(saved["version"]) is not int or saved["version"] != 1
            or type(saved["seed"]) is not int
            or saved["scenario"] != state.scenario or saved["seed"] != state.seed
            or set(saved["simple"]) != set(SIMPLE) or set(saved["pairs"]) != set(PAIRS)
            or set(saved["counters"]) != set(COUNTERS)):
        raise ValueError("Simulator state version/tuple mismatch; no reset performed")
    def mapping(value):
        if not isinstance(value, dict) or len(value) > 4096 or any(not isinstance(k, str) or len(k) > 512 for k in value):
            raise ValueError("Invalid simulator state map")
        return deepcopy(value)
    def pairs(value):
        if not isinstance(value, list) or len(value) > 4096:
            raise ValueError("Invalid simulator pair map")
        result = {}
        for entry in value:
            if not isinstance(entry, list) or len(entry) != 2 or not isinstance(entry[0], list) or len(entry[0]) != 2:
                raise ValueError("Invalid simulator pair key")
            key, row = entry
            if any(type(k) not in (str, int) for k in key) or not isinstance(row, dict) or tuple(key) in result:
                raise ValueError("Duplicate/invalid simulator pair entry")
            result[tuple(key)] = deepcopy(row)
        return result
    simple = {name: mapping(saved["simple"][name]) for name in SIMPLE}
    paired = {name: pairs(saved["pairs"][name]) for name in PAIRS}
    counters = {name: saved["counters"][name] for name in COUNTERS}
    if any(type(n) is not int or not 1 <= n < 2**53 for n in [*counters.values(), saved["next_worker_task"]]):
        raise ValueError("Invalid simulator state counter")
    if not isinstance(saved["worldview_id"], str) or len(saved["worldview_id"]) > 256:
        raise ValueError("Invalid simulator worldview identity")
    assets, locks, workers, tasks = pairs(saved["assets"]), mapping(saved["project_locks"]), pairs(saved["workers"]), mapping(saved["worker_tasks"])
    logs = mapping(saved["notifications"])
    from .drafts import retained
    drafts = retained(saved["drafts"], simple["contexts"])
    for root, log in logs.items():
        if (root not in simple["contexts"] or not isinstance(log, dict) or set(log) != {"epoch", "next", "events"}
                or not isinstance(log["epoch"], str) or len(log["epoch"]) != 32
                or type(log["next"]) is not int or not 1 <= log["next"] < 2**53
                or not isinstance(log["events"], list) or len(log["events"]) > 128):
            raise ValueError("Invalid retained notification log")
        expected_sequence = log["next"] - len(log["events"])
        if expected_sequence < 1:
            raise ValueError("Retained notification sequence would be reused")
        for event in log["events"]:
            if (not isinstance(event, dict) or set(event) != {"sequence", "kind", "job_id", "service"}
                    or type(event["sequence"]) is not int or event["sequence"] != expected_sequence
                    or event["kind"] not in ("JOB_WAITING", "JOB_STARTED", "JOB_FINISHED", "JOB_ABORTED", "JOB_INTERRUPTED")
                    or type(event["job_id"]) is not int or not 1 <= event["job_id"] < 2**53
                    or event["service"] not in ("resources", "resolver", "runtime")):
                raise ValueError("Invalid retained notification event")
            expected_sequence += 1
    for name, counter in (("jobs", "next_job"), ("observations", "next_observation")):
        keys = [key[1] for key in paired[name]]
        if any(type(key) is not int or key < 1 for key in keys) or counters[counter] <= max(keys, default=0):
            raise ValueError("Retained simulator identity would be reused")
    task_numbers = []
    for key in tasks:
        if not key.startswith("worker-task-") or not key[len("worker-task-"):].isdigit():
            raise ValueError("Invalid retained task identity")
        task_numbers.append(int(key[len("worker-task-"):]))
    if saved["next_worker_task"] <= max(task_numbers, default=0):
        raise ValueError("Retained worker task identity would be reused")
    if recovery:
        workers.clear()  # Operator must explicitly re-register after process restart.
        for task in tasks.values():
            if not isinstance(task, dict) or task.get("state") not in ("QUEUED", "LEASED", "RECOVERY_REQUIRED", "PUBLISHED", "FAILED", "ABORTED", "CANCELLED"):
                raise ValueError("Unknown retained worker task state")
            if task["state"] == "LEASED":
                task["state"] = "RECOVERY_REQUIRED"
            task["lease"] = None  # Never renew a lost incarnation's publication authority.
    for name, value in {**simple, **paired, **counters}.items():
        setattr(state, name, value)
    state.worldview_id = saved["worldview_id"]
    state.authoring.assets, state.authoring.project_locks = assets, locks
    state.workers.workers, state.workers.tasks = workers, tasks
    state.workers.next_task = saved["next_worker_task"]
    state.notifications.logs = logs
    state.drafts.rows = drafts


class Store:
    def __init__(self, path, state):
        path = Path(path)
        if path.is_symlink() or ".klab" in {p.lower() for p in path.resolve().parts} or not path.name.endswith(".klab-sim.sqlite"):
            raise ValueError("Choose an explicitly owned .klab-sim.sqlite file outside live .klab custody")
        if not path.parent.is_dir():
            raise ValueError("Simulator state parent directory must already exist")
        self.connection = None
        lock_path = Path(str(path) + ".lock")
        if lock_path.is_symlink():
            raise ValueError("Simulator state lock cannot be a symbolic link")
        self.lock = lock_path.open("a+b")
        try:
            if not lock_path.stat().st_size:
                self.lock.write(b"0"); self.lock.flush()
            self.lock.seek(0)
            if sys.platform == "win32":
                import msvcrt
                msvcrt.locking(self.lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            existed = path.exists() and path.stat().st_size > 0
            self.connection = sqlite3.connect(path, timeout=0, check_same_thread=False)
            app_id = self.connection.execute("PRAGMA application_id").fetchone()[0]
            if existed and app_id != APPLICATION_ID:
                raise ValueError("Existing file is not owned simulator state; left unchanged")
            if not existed:
                self.connection.execute(f"PRAGMA application_id={APPLICATION_ID}")
                self.connection.execute("CREATE TABLE checkpoint (id INTEGER PRIMARY KEY CHECK(id=1), payload BLOB NOT NULL, digest TEXT NOT NULL)")
                self.connection.commit()
                if os.name != "nt":
                    path.chmod(0o600); lock_path.chmod(0o600)
            self.connection.execute("PRAGMA synchronous=FULL")
            row = self.connection.execute("SELECT length(payload), digest FROM checkpoint WHERE id=1").fetchone()
            if row:
                if row[0] > MAX_BYTES:
                    raise ValueError("Simulator checkpoint exceeded byte budget")
                data = self.connection.execute("SELECT payload FROM checkpoint WHERE id=1").fetchone()[0]
                if hashlib.sha256(data).hexdigest() != row[1]:
                    raise ValueError("Simulator checkpoint checksum differs; no reset performed")
                def unique(pairs):
                    result = {}
                    for key, value in pairs:
                        if key in result:
                            raise ValueError("Duplicate state key")
                        result[key] = value
                    return result
                restore(state, json.loads(data, object_pairs_hook=unique))
            self.save(state)  # Persist startup lease fencing before serving any request.
        except BaseException:
            self.close()
            raise

    def save(self, state):
        if self.connection is None:
            raise ValueError("Owned simulator store is closed")
        saved = snapshot(state)
        # Active publication capabilities are deliberately not durable bearer tokens.
        for task in saved["worker_tasks"].values():
            task["lease"] = None
        payload = json.dumps(saved, allow_nan=False, separators=(",", ":")).encode()
        if len(payload) > MAX_BYTES:
            raise ValueError("Simulator checkpoint exceeded byte budget")
        with self.connection:
            self.connection.execute("INSERT OR REPLACE INTO checkpoint(id,payload,digest) VALUES(1,?,?)", (payload, hashlib.sha256(payload).hexdigest()))

    def close(self):
        if self.connection is not None:
            self.connection.close()
            self.connection = None
        self.lock.close()  # OS lock is released on close or process exit, not a stale PID file.
