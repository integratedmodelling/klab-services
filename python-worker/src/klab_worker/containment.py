"""Owned process-tree cleanup, not a hostile-code sandbox.

Windows Job Objects forbid breakaway and kill members on handle close. POSIX
sessions kill the owned process group; a trusted script must not deliberately
escape via setsid. No global process scan or unrelated-process termination.
"""
import os
import signal
import time

from .protocol import WorkerError


class ProcessTree:
    def __init__(self, process, memory_bytes=None):
        self.process = process
        self.handle = None
        if os.name != "nt":
            return
        import ctypes
        from ctypes import wintypes

        class Basic(ctypes.Structure):
            _fields_ = [("PerProcessUserTimeLimit", ctypes.c_int64), ("PerJobUserTimeLimit", ctypes.c_int64),
                ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD), ("SchedulingClass", wintypes.DWORD)]

        class IO(ctypes.Structure):
            _fields_ = [(name, ctypes.c_uint64) for name in ("ReadOperationCount", "WriteOperationCount",
                "OtherOperationCount", "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]

        class Extended(ctypes.Structure):
            _fields_ = [("BasicLimitInformation", Basic), ("IoInfo", IO), ("ProcessMemoryLimit", ctypes.c_size_t),
                ("JobMemoryLimit", ctypes.c_size_t), ("PeakProcessMemoryUsed", ctypes.c_size_t), ("PeakJobMemoryUsed", ctypes.c_size_t)]

        kernel = getattr(ctypes, "WinDLL")("kernel32", use_last_error=True)
        kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        kernel.CreateJobObjectW.restype = wintypes.HANDLE
        kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
        kernel.SetInformationJobObject.restype = wintypes.BOOL
        kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
        kernel.AssignProcessToJobObject.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        kernel.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
        kernel.TerminateJobObject.restype = wintypes.BOOL
        kernel.QueryInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p]
        kernel.QueryInformationJobObject.restype = wintypes.BOOL
        self.kernel = kernel
        handle = kernel.CreateJobObjectW(None, None)
        if not handle:
            raise WorkerError("Cannot establish owned worker process containment")
        limits = Extended()
        limits.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if memory_bytes is not None:
            limits.BasicLimitInformation.LimitFlags |= 0x200  # JOB_OBJECT_LIMIT_JOB_MEMORY
            limits.JobMemoryLimit = memory_bytes
        if (not kernel.SetInformationJobObject(handle, 9, ctypes.byref(limits), ctypes.sizeof(limits))
                or not kernel.AssignProcessToJobObject(handle, wintypes.HANDLE(int(process._handle)))):
            kernel.CloseHandle(handle)
            raise WorkerError("Cannot establish owned worker process containment")
        self.handle = handle

    def close(self):
        if os.name == "nt":
            if self.handle is not None:
                import ctypes
                from ctypes import wintypes
                class Accounting(ctypes.Structure):
                    _fields_ = [(name, ctypes.c_int64) for name in ("TotalUserTime", "TotalKernelTime",
                        "ThisPeriodTotalUserTime", "ThisPeriodTotalKernelTime")] + [(name, wintypes.DWORD) for name in (
                        "TotalPageFaultCount", "TotalProcesses", "ActiveProcesses", "TotalTerminatedProcesses")]
                try:
                    if not self.kernel.TerminateJobObject(self.handle, 1):
                        raise WorkerError("Owned worker job termination failed")
                    deadline = time.monotonic() + 5
                    while True:
                        counts = Accounting()
                        if not self.kernel.QueryInformationJobObject(self.handle, 1, ctypes.byref(counts), ctypes.sizeof(counts), None):
                            raise WorkerError("Owned worker job cleanup cannot be confirmed")
                        if not counts.ActiveProcesses:
                            break
                        if time.monotonic() >= deadline:
                            raise WorkerError("Owned worker job cleanup deadline exceeded")
                        time.sleep(.01)
                finally:
                    self.kernel.CloseHandle(self.handle)
                    self.handle = None
        else:
            try:
                getattr(os, "killpg")(self.process.pid, getattr(signal, "SIGKILL"))
            except ProcessLookupError:
                pass
