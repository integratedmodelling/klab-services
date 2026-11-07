"""Development-only bounded public call/resume presence, not functional acceptance.

No arguments, return values or parameterized test identifiers are retained. Only the
owning thread is measured; other threads/processes remain explicitly unmeasured.
The production SDK does not import or ship this tool.
"""
import hashlib
import os
from pathlib import Path
import sys
import threading

from operation_inventory import inventory


class OperationTrace:
    def __init__(self, source, *, limit=100000, test_limit=10000):
        if (type(limit) is not int or not 1 <= limit <= 1000000
                or type(test_limit) is not int or not 1 <= test_limit <= 100000):
            raise ValueError("Explicit finite event/test bounds required")
        self.source = Path(source).resolve()
        self.operations = inventory(self.source)["operations"]
        self.lookup = {(row["path"], (row["owner"] + "." if row["owner"] else "")
                        + row["operation"]) for row in self.operations}
        self.hashes = {path.relative_to(self.source).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in sorted(self.source.rglob("*.py"))}
        self.files = {os.path.normcase(str(self.source / path)): path for path in self.hashes}
        self.limit, self.test_limit = limit, test_limit
        self.events, self.tests = set(), []
        self.current = None
        self.active = self.exhausted = self.ownership_lost = False
        self.started = False
        self.hook = self._profile
        self.thread = None

    def _verify(self):
        actual = {path.relative_to(self.source).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                  for path in sorted(self.source.rglob("*.py"))}
        if actual != self.hashes:
            raise ValueError("Source bytes changed; no attribution fallback")

    def _profile(self, frame, event, arg):
        if event != "call" or self.current is None:
            return
        code = frame.f_code
        path = self.files.get(os.path.normcase(code.co_filename))
        if path is None:
            return
        if (path, code.co_qualname) not in self.lookup:
            return
        row = (self.current, path, code.co_qualname)
        if row not in self.events and len(self.events) >= self.limit:
            self.exhausted = True
        else:
            self.events.add(row)

    def start(self):
        if self.started or sys.getprofile() is not None or threading.getprofile() is not None:
            raise ValueError("Never replace an existing profiler or active trace")
        self._verify()
        self.thread = threading.get_ident()
        self.started = True
        self.active = True
        sys.setprofile(self.hook)

    def begin(self, nodeid):
        if (not self.active or threading.get_ident() != self.thread or self.current is not None
                or not isinstance(nodeid, str) or not nodeid or len(nodeid) > 4096):
            raise ValueError("Explicit nonoverlapping owning-thread test scope required")
        if len(self.tests) >= self.test_limit:
            self.exhausted = True
            raise ValueError("Test attribution budget exceeded")
        # Never store parameter values or their hashes, even for failed tests.
        reference = nodeid.split("[", 1)[0]
        if not reference or any(ord(char) < 32 for char in reference):
            raise ValueError("Safe executable test reference required")
        self.current = len(self.tests)
        self.tests.append({"test_ref": reference, "outcome": "UNKNOWN"})

    def finish(self, outcome):
        if (not self.active or threading.get_ident() != self.thread or self.current is None
                or outcome not in ("PASSED", "FAILED", "SKIPPED", "ERROR")):
            raise ValueError("Explicit whole-test outcome required")
        self.tests[self.current]["outcome"] = outcome
        self.current = None

    def stop(self):
        if not self.active or threading.get_ident() != self.thread:
            raise ValueError("Only the owning thread can stop an active trace")
        self.ownership_lost = sys.getprofile() is not self.hook
        if not self.ownership_lost:
            sys.setprofile(None)
        self.active = False
        if self.ownership_lost:
            raise ValueError("Profiler replaced; unrelated replacement preserved")

    def report(self):
        if self.active:
            raise ValueError("Stop owned profiler before reporting")
        self._verify()
        return {"version": 1,
                "evidence": "TEST_SCOPE_CALL_OR_RESUME_PRESENCE_NOT_FUNCTIONAL_OR_NATIVE_ACCEPTANCE",
                "thread_scope": "OWNING_THREAD_ONLY_OTHER_THREADS_AND_PROCESSES_UNMEASURED",
                "exhausted": self.exhausted, "profiler_ownership_lost": self.ownership_lost,
                "source_sha256": dict(self.hashes), "operations": [dict(row) for row in self.operations],
                "tests": [dict(row) for row in self.tests],
                "bindings": [{"test": index, "path": path, "qualname": name}
                             for index, path, name in sorted(self.events)]}
