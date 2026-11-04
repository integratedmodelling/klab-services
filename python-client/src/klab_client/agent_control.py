"""Process-local agent control policy, with no built-in transport or remote authority."""
from dataclasses import dataclass, field
from threading import RLock

from .agent_status import AgentStatusSnapshot, _text, decode_agent_status
from .client import scope_token
from .errors import InvalidRequestError, ProtocolError, TransportError
from .handles import _service_identity, _url


@dataclass(frozen=True)
class AgentControlBinding:
    """Caller assertions, not discovered/authenticated credentials or a remote lease.

    An adapter must independently establish every field, including incarnation;
    never synthesize incarnation from an agent URN or a numeric observation ID.
    """
    agent_urn: str
    runtime_url: str
    runtime_id: str
    scope: str
    principal_key: str = field(repr=False)
    runtime_incarnation: str = field(repr=False)

    def __post_init__(self):
        for value in (self.agent_urn, self.scope, self.principal_key, self.runtime_incarnation):
            if not _text(value, 4096):
                raise InvalidRequestError("Control binding requires explicit bounded identity fields")
        _service_identity(self.runtime_id)
        scope_token(self.scope)
        object.__setattr__(self, "runtime_url", _url(self.runtime_url))


@dataclass(frozen=True)
class AgentControlView:
    """Local facts only. Neither publication nor a sender state proves cleanup."""
    attachment: str
    start_outcome: str
    stop_outcome: str
    last_report: AgentStatusSnapshot | None
    terminal_reported: bool
    conflicting_reports: bool
    admitted_reports: int
    admitted_bytes: int
    remote_cleanup: str = "UNKNOWN"


class AgentControlJournal:
    """One admission per start/stop, per object, consumed BEFORE calling a sender.

    The explicit sender(binding, action) must implement one bounded transmission
    and return its publication boolean. This class never selects a broker, sends
    an HTTP request, creates an agent/session, retries, stops on detach, or cleans
    up remote resources. It cannot prevent retries *inside* a caller's sender or
    replay through another object/process. No persistence/recovery contract exists.
    ACTIVE is local admission permission, NOT an authenticated transport state.
    """
    def __init__(self, binding, *, max_reports=128, max_total_bytes=1024 * 1024):
        if not isinstance(binding, AgentControlBinding):
            raise InvalidRequestError("An explicit AgentControlBinding is required")
        if type(max_reports) is not int or not 1 <= max_reports <= 10000:
            raise InvalidRequestError("Report budget must be an integer in [1,10000]")
        if type(max_total_bytes) is not int or not 1 <= max_total_bytes <= 16 * 1024 * 1024:
            raise InvalidRequestError("Total report bytes must be an integer in [1,16777216]")
        self._binding = binding
        self._max_reports, self._max_total_bytes = max_reports, max_total_bytes
        self._lock = RLock()
        self._attachment = "ACTIVE"
        self._outcomes = {"start": "NOT_ATTEMPTED", "stop": "NOT_ATTEMPTED"}
        self._last = None
        self._terminal = self._conflicting = False
        self._reports = self._bytes = 0

    @property
    def view(self):
        with self._lock:
            return AgentControlView(self._attachment, self._outcomes["start"],
                self._outcomes["stop"], self._last, self._terminal, self._conflicting,
                self._reports, self._bytes)

    def _admit(self, binding):
        if not isinstance(binding, AgentControlBinding) or binding != self._binding:
            raise ProtocolError("Agent control binding changed; operation not admitted")
        if self._attachment != "ACTIVE":
            raise InvalidRequestError("Detached or lost journal cannot reopen or admit operations")

    def attempt(self, action, *, binding, sender):
        """Call the explicit sender at most once for this action; never confirm stop.

        Exceptions, cancellation and nonboolean replies leave UNKNOWN permanently.
        False means UNCONFIRMED, not proof that no mutation happened. True means
        PUBLISHED, not execution/termination. An already admitted callback may
        finish after concurrent detach/loss; its result never reattaches the journal.
        """
        if not isinstance(action, str) or action not in ("start", "stop") or not callable(sender):
            raise InvalidRequestError("Use explicit start/stop and a callable one-shot sender")
        with self._lock:
            self._admit(binding)
            if self._outcomes[action] != "NOT_ATTEMPTED" or self._terminal or self._conflicting:
                raise InvalidRequestError("Command already attempted or terminal/conflicting status reported")
            if action == "start" and (self._outcomes["stop"] != "NOT_ATTEMPTED"
                                      or self._last is not None and
                                      (self._last.state != "READY" or not self._last.viable)):
                raise InvalidRequestError("Start is incompatible with prior stop or sender status")
            self._outcomes[action] = "UNKNOWN"
        try:
            published = sender(binding, action)
        except Exception:
            raise TransportError("Agent command outcome unknown; do not replay") from None
        if type(published) is not bool:
            raise ProtocolError("Agent command publication result invalid; outcome unknown, do not replay")
        with self._lock:
            self._outcomes[action] = "PUBLISHED" if published else "UNCONFIRMED"
        return self.view

    def observe(self, body, *, binding):
        """Record bounded payload evidence, NOT authenticated/ordered current state.

        Caller must validate the transport envelope and identity first. Budgets
        include admitted malformed payloads. Sender clocks never establish order;
        terminal reports latch, and subsequent nonterminal reports cannot reopen.
        """
        with self._lock:
            self._admit(binding)
            if not isinstance(body, bytes) or len(body) > 65536:
                raise ProtocolError("Status payload must be bounded bytes")
            if self._reports >= self._max_reports or self._bytes + len(body) > self._max_total_bytes:
                self._attachment = "LOST"
                raise ProtocolError("Agent report admission budget exhausted; continuity unknown")
            self._reports += 1
            self._bytes += len(body)
            report = decode_agent_status(body, expected_agent_urn=self._binding.agent_urn)
            terminal = report.state in ("STOPPED", "FAILED")
            if (self._terminal and not terminal or self._last is not None
                    and self._last.state == "RUNNING" and report.state == "READY"):
                self._conflicting = True
            self._terminal = self._terminal or terminal
            self._last = report
            return self.view

    def transport_lost(self):
        """Disable future admissions without replay, remote stop or resource deletion."""
        with self._lock:
            if self._attachment == "ACTIVE":
                self._attachment = "LOST"
            return self.view

    def detach(self):
        """Local-only, idempotent detachment. Never call a sender or stop remotely."""
        with self._lock:
            self._attachment = "DETACHED"
            return self.view
