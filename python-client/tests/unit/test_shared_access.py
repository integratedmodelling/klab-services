"""Public opt-in/capability gates and fail-closed shared-read receipts, no simulator import."""
import asyncio
from copy import deepcopy
from dataclasses import FrozenInstanceError
import json

import httpx
import pytest

from klab_client import (AccessInspection, AsyncSharedAccess, Endpoint, ReadGrant, SharedAccess)
from klab_client.errors import AuthorizationError, InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError
from test_selections import client, offline, root

PROTOCOL = "klab.experimental.shared-access/1"
IDENTITY = "a" * 32


def grant():
    return {"protocol": PROTOCOL, "contextId": "s.c", "grantId": IDENTITY, "grantee": "bob", "expiresAt": 2000000000, "rights": ["READ"]}


def inspection(owner=True):
    return {"protocol": PROTOCOL, "contextId": "s.c", "principal": "alice" if owner else "bob", "owner": "alice",
        "rights": ["READ", "SUBMIT", "RELEASE", "SHARE"] if owner else ["READ"],
        "grantId": None if owner else IDENTITY, "expiresAt": None if owner else 2000000000}


def handler_for(value, calls=None, capability=None):
    def handle(request):
        if calls is not None: calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json=capability if capability is not None else {"serviceId": "runtime-id", "sharedAccessProtocol": PROTOCOL})
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime-id"
        return httpx.Response(200, content=json.dumps(value).encode(), headers={"Content-Type": "application/json"})
    return handle


@pytest.mark.parametrize("native_async", [False, True])
def test_public_grant_inspect_revoke_immutable_current_credentials_and_no_implicit_disposal(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["Authorization"] == "rotated-secret"
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "runtime-id", "sharedAccessProtocol": PROTOCOL})
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime-id"
        if request.url.path.endswith("/access"):
            assert request.url.params["protocol"] == PROTOCOL
            return httpx.Response(200, json=inspection())
        if request.url.path.endswith("/revoke"):
            assert json.loads(request.content) == {"protocol": PROTOCOL}
            return httpx.Response(200, json={"protocol": PROTOCOL, "contextId": "s.c", "grantId": IDENTITY, "revoked": True})
        assert json.loads(request.content) == {"protocol": PROTOCOL, "grantee": "bob", "ttlSeconds": 30}
        return httpx.Response(200, json=grant())
    async def run():
        async with client(handler, True) as api:
            sharing = AsyncSharedAccess(root(api), allow_experimental=True)
            assert calls == []
            api.transport.endpoints["runtime"] = Endpoint("https://runtime.test", "rotated-secret")
            access = await sharing.inspect(); receipt = await sharing.grant_read("bob", ttl_seconds=30)
            assert await sharing.revoke(receipt.grant_id)
            check(access, receipt)
    def check(access, receipt):
        assert isinstance(access, AccessInspection) and access.principal == access.owner == "alice" and access.grant is None
        assert receipt == ReadGrant("s.c", IDENTITY, "bob", 2000000000.0)
        assert receipt.rights == ("READ",) and "NOT_NATIVE" in receipt.evidence
        with pytest.raises(FrozenInstanceError): receipt.rights = ("SHARE",)
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            sharing = SharedAccess(root(api), allow_experimental=True)
            assert calls == []
            api.transport.endpoints["runtime"] = Endpoint("https://runtime.test", "rotated-secret")
            access = sharing.inspect(); receipt = sharing.grant_read("bob", ttl_seconds=30)
            assert sharing.revoke(receipt.grant_id)
            check(access, receipt)
    assert len(calls) == 6 and not any("release" in request.url.path for request in calls)


@pytest.mark.parametrize("native_async", [False, True])
def test_read_collaborator_inspection_is_server_report_not_local_authority(native_async):
    async def run():
        async with client(handler_for(inspection(False)), True) as api:
            value = await AsyncSharedAccess(root(api), allow_experimental=True).inspect()
            assert value.principal == "bob" and value.rights == ("READ",) and value.grant.grant_id == IDENTITY
    if native_async: asyncio.run(run())
    else:
        with client(handler_for(inspection(False))) as api:
            value = SharedAccess(root(api), allow_experimental=True).inspect()
            assert value.principal == "bob" and value.rights == ("READ",) and value.grant.grant_id == IDENTITY


@pytest.mark.parametrize("allow", [False, None, 1, "true"])
def test_missing_exact_opt_in_is_offline(allow):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): SharedAccess(root(api), allow_experimental=allow)


def test_root_and_client_model_refuse_offline():
    with client(offline) as api:
        for context in (None, root(api, "s.c.1"), root(api).between(1, 2)):
            with pytest.raises(InvalidRequestError): SharedAccess(context, allow_experimental=True)
        with pytest.raises(InvalidRequestError): AsyncSharedAccess(root(api), allow_experimental=True)
    async def run():
        async with client(offline, True) as api:
            with pytest.raises(InvalidRequestError): SharedAccess(root(api), allow_experimental=True)
    asyncio.run(run())


@pytest.mark.parametrize("kwargs", [{"ttl_seconds": True}, {"ttl_seconds": 0}, {"ttl_seconds": 3601}, {"ttl_seconds": 1.5}])
def test_ttl_refusals_are_offline(kwargs):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): SharedAccess(root(api), allow_experimental=True).grant_read("bob", **kwargs)


@pytest.mark.parametrize("grantee", [None, "", ".", "..", "bad name", "x" * 129, "nonasciié"])
def test_principal_refusals_are_offline(grantee):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): SharedAccess(root(api), allow_experimental=True).grant_read(grantee)


@pytest.mark.parametrize("identity", [None, True, "../escape", "A" * 32, "a" * 31])
def test_revocation_identity_refusals_are_offline(identity):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): SharedAccess(root(api), allow_experimental=True).revoke(identity)


@pytest.mark.parametrize("capability,error", [({"serviceId": "other", "sharedAccessProtocol": PROTOCOL}, ProtocolError),
    ({"serviceId": "runtime-id"}, UnsupportedOperationError)])
@pytest.mark.parametrize("native_async", [False, True])
def test_no_unadvertised_or_foreign_runtime_endpoint_call(capability, error, native_async):
    calls = []
    handle = handler_for({}, calls, capability)
    async def run():
        async with client(handle, True) as api:
            with pytest.raises(error): await AsyncSharedAccess(root(api), allow_experimental=True).inspect()
    if native_async: asyncio.run(run())
    else:
        with client(handle) as api:
            with pytest.raises(error): SharedAccess(root(api), allow_experimental=True).inspect()
    assert len(calls) == 1 and calls[0].url.path.endswith("/capabilities")


@pytest.mark.parametrize("change", [{"protocol": "unknown"}, {"contextId": "other.root"}, {"grantee": "alice"},
    {"rights": ["READ", "SHARE"]}, {"grantId": "bad"}, {"grantee": "bad name"}, {"expiresAt": True},
    {"expiresAt": -1}, {"expiresAt": float("nan")}, {"expiresAt": 2**1025}, {"extra": "unexpected"}])
def test_wrong_or_unbounded_grant_receipts_fail_closed(change):
    with client(handler_for({**grant(), **change})) as api:
        with pytest.raises(ProtocolError): SharedAccess(root(api), allow_experimental=True).grant_read("bob")


@pytest.mark.parametrize("value", [None, {}, {**inspection(), "contextId": "other.root"}, {**inspection(), "protocol": "unknown"},
    {**inspection(), "owner": "bad name"}, {**inspection(), "principal": ""}, {**inspection(), "rights": ["READ"]},
    {**inspection(), "grantId": IDENTITY}, {**inspection(), "expiresAt": 5}, {**inspection(False), "grantId": None}])
def test_invalid_access_inspection_refuses(value):
    with client(handler_for(value)) as api:
        with pytest.raises(ProtocolError): SharedAccess(root(api), allow_experimental=True).inspect()


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("value", [None, {"revoked": False},
    {"protocol": PROTOCOL, "contextId": "s.c", "grantId": IDENTITY, "revoked": 1},
    {"protocol": PROTOCOL, "contextId": "other.root", "grantId": IDENTITY, "revoked": True}])
def test_wrong_revocation_receipt_refuses(native_async, value):
    async def run():
        async with client(handler_for(value), True) as api:
            with pytest.raises(ProtocolError): await AsyncSharedAccess(root(api), allow_experimental=True).revoke(IDENTITY)
    if native_async: asyncio.run(run())
    else:
        with client(handler_for(value)) as api:
            with pytest.raises(ProtocolError): SharedAccess(root(api), allow_experimental=True).revoke(IDENTITY)


@pytest.mark.parametrize("native_async", [False, True])
def test_server_denial_is_not_reinterpreted_as_permission_or_retried(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "runtime-id", "sharedAccessProtocol": PROTOCOL})
        return httpx.Response(403, json={"message": "denied"})
    async def run():
        async with client(handler, True) as api:
            with pytest.raises(AuthorizationError): await AsyncSharedAccess(root(api), allow_experimental=True).grant_read("bob")
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            with pytest.raises(AuthorizationError): SharedAccess(root(api), allow_experimental=True).grant_read("bob")
    assert len(calls) == 2


def test_changed_binding_and_credential_reflection_refuse_without_exposing_material():
    with client(offline) as api:
        context = root(api); sharing = SharedAccess(context, allow_experimental=True)
        context.context_id = "s.c.1"
        with pytest.raises(InvalidRequestError): sharing.inspect()
    response = deepcopy(inspection()); response["principal"] = "current-secret"
    with client(handler_for(response)) as api:
        with pytest.raises(ProtocolError) as error: SharedAccess(root(api), allow_experimental=True).inspect()
        assert "current-secret" not in str(error.value)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("state", ["ACTIVE", "REVOKED", "EXPIRED", "ABSENT"])
def test_owner_grant_lookup_is_explicit_server_state_not_an_automatic_retry(native_async, state):
    value = {"protocol": PROTOCOL, "contextId": "s.c", "grantee": "bob", "state": state, "grant": None if state == "ABSENT" else grant()}
    calls = []
    async def run():
        async with client(handler_for(value, calls), True) as api:
            result = await AsyncSharedAccess(root(api), allow_experimental=True).inspect_grant("bob")
            assert result.state == state and (result.grant is None) == (state == "ABSENT")
    if native_async: asyncio.run(run())
    else:
        with client(handler_for(value, calls)) as api:
            result = SharedAccess(root(api), allow_experimental=True).inspect_grant("bob")
            assert result.state == state and (result.grant is None) == (state == "ABSENT")
    assert all(call.method == "GET" for call in calls) and calls[-1].url.params["grantee"] == "bob"


@pytest.mark.parametrize("change", [{"protocol": "wrong"}, {"contextId": "other.root"}, {"grantee": "alice"},
    {"state": "VERIFIED_NATIVE"}, {"state": "ABSENT", "grant": grant()}, {"grant": None}, {"extra": True}])
def test_mismatched_grant_inspection_is_not_permission(change):
    value = {"protocol": PROTOCOL, "contextId": "s.c", "grantee": "bob", "state": "ACTIVE", "grant": grant(), **change}
    with client(handler_for(value)) as api:
        with pytest.raises(ProtocolError): SharedAccess(root(api), allow_experimental=True).inspect_grant("bob")


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("mutation", [False, True])
def test_binding_change_during_response_discards_reads_and_marks_mutations_unknown(native_async, mutation):
    selected = None
    def handler(request):
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "runtime-id", "sharedAccessProtocol": PROTOCOL})
        selected.context_id = "s.c.1"
        return httpx.Response(200, json=grant() if mutation else inspection())
    error = SubmissionOutcomeUnknown if mutation else ProtocolError
    async def run():
        nonlocal selected
        async with client(handler, True) as api:
            selected = root(api); sharing = AsyncSharedAccess(selected, allow_experimental=True)
            with pytest.raises(error):
                if mutation: await sharing.grant_read("bob")
                else: await sharing.inspect()
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            selected = root(api); sharing = SharedAccess(selected, allow_experimental=True)
            with pytest.raises(error):
                if mutation: sharing.grant_read("bob")
                else: sharing.inspect()


@pytest.mark.parametrize("replace", ["context", "client"])
def test_invalid_context_or_client_binding_never_reaches_http(replace):
    with client(offline) as api:
        sharing = SharedAccess(root(api), allow_experimental=True)
        if replace == "context": sharing.context = None
        else: sharing.context.client = None
        with pytest.raises(InvalidRequestError): sharing.inspect()
