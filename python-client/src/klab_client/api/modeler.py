from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from klab_client.api.scopes import ContextScope, SessionScope, UserScope


class Modeler(ABC):
    @abstractmethod
    def open_user(self, user_scope: UserScope) -> None: ...

    @abstractmethod
    def open_session(self, session_scope: SessionScope) -> None: ...

    @abstractmethod
    def open_context(self, context_scope: ContextScope) -> None: ...

    @abstractmethod
    def get_open_context(self) -> ContextScope | None: ...


@dataclass(slots=True)
class ModelerImpl(Modeler):
    user_scope: UserScope | None = None
    session_scope: SessionScope | None = None
    context_scope: ContextScope | None = None
    event_log: list[str] = field(default_factory=list)

    def open_user(self, user_scope: UserScope) -> None:
        self.user_scope = user_scope
        self.event_log.append(f"user:{user_scope.get_user_id()}")

    def open_session(self, session_scope: SessionScope) -> None:
        self.session_scope = session_scope
        self.event_log.append(f"session:{session_scope.get_session_id()}")

    def open_context(self, context_scope: ContextScope) -> None:
        self.context_scope = context_scope
        self.event_log.append(f"context:{context_scope.get_context_id()}")

    def get_open_context(self) -> ContextScope | None:
        return self.context_scope
