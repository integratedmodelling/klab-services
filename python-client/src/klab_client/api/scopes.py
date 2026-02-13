from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

from klab_client.api.primitives import Scope


class UserScope(ABC):
    @abstractmethod
    def get_user_id(self) -> str: ...

    @abstractmethod
    def get_roles(self) -> set[str]: ...


class SessionScope(UserScope, ABC):
    @abstractmethod
    def get_session_id(self) -> str: ...


class ContextScope(SessionScope, ABC):
    @abstractmethod
    def get_context_id(self) -> str: ...

    @abstractmethod
    def get_observer(self) -> str | None: ...


@dataclass(slots=True)
class UserScopeImpl(UserScope):
    user_id: str
    roles: set[str] = field(default_factory=set)
    metadata: dict[str, Any] = field(default_factory=dict)

    def get_user_id(self) -> str:
        return self.user_id

    def get_roles(self) -> set[str]:
        return self.roles


@dataclass(slots=True)
class SessionScopeImpl(UserScopeImpl, SessionScope):
    session_id: str = ""

    def get_session_id(self) -> str:
        return self.session_id


@dataclass(slots=True)
class ContextScopeImpl(SessionScopeImpl, ContextScope):
    context_id: str = ""
    observer: str | None = None
    scope: Scope | None = None

    def get_context_id(self) -> str:
        return self.context_id

    def get_observer(self) -> str | None:
        return self.observer
