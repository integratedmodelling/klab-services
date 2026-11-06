"""Finite public-SDK read-contract campaign against one explicitly chosen backend.

Real mode requires a fresh matching read-only gate receipt. Never submit, import,
create scopes, repair Java, proxy a request or fall back on another backend.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path

from .state import SERVICES


def readiness_matches(receipt, endpoints):
    try:
        stamp = datetime.fromisoformat(receipt["collected_utc"].replace("Z", "+00:00"))
        age = (datetime.now(timezone.utc) - stamp).total_seconds()
        rows = {row["name"]: row for row in receipt["services"]}
        return (0 <= age <= 86400 and all(receipt["gates"][key] == "PASS" for key in ("G0", "G1", "G2"))
                and all(rows[name]["endpoint"] == endpoints[name] and rows[name]["check"] == "PASS" for name in SERVICES))
    except (KeyError, ValueError, TypeError, AttributeError):
        return False


def run(mode, endpoints, token, model_urn, readiness=None):
    if mode not in ("simulated", "real", "replay") or set(endpoints) != set(SERVICES):
        raise ValueError("Choose one complete backend and exactly four named endpoints")
    backend = {"simulated": "SIMULATED", "real": "REAL", "replay": "REPLAY"}[mode]
    if mode == "real" and not readiness_matches(readiness or {}, endpoints):
        return {"backend": backend, "status": "NOT_RUN", "reason": "Fresh matching read-only G0-G2 receipt required", "cases": []}
    from klab_client import Client, ConnectionIdentity, DeploymentProfile, Endpoint, IssuedTokenProvider, ServiceReference, connect
    import httpx
    results, trace = [], []

    class TraceTransport(httpx.BaseTransport):
        def __init__(self):
            self.inner = httpx.HTTPTransport(retries=0, trust_env=False)

        def handle_request(self, request):
            record = {"backend": backend, "workflow": "read-contracts", "method": request.method,
                      "route": request.url.path.replace(token, "[REDACTED]")}
            try:
                response = self.inner.handle_request(request)
                record["status"] = response.status_code
                return response
            except httpx.TransportError as error:
                record["error_type"] = type(error).__name__
                raise
            finally:
                trace.append(record)

        def close(self):
            pass  # Shared lease: short bootstrap clients must not close the outer pool.

    transport = TraceTransport()

    def check(identity, operation):
        try:
            operation()
            results.append({"id": identity, "backend": backend, "status": "PASS"})
        except Exception as error:
            # Never copy arbitrary private response bodies/credentials into artifacts.
            results.append({"id": identity, "backend": backend, "status": "FAIL", "error_type": type(error).__name__,
                            "http_status": getattr(error, "status_code", None)})

    def reads(client):
        def semantics():
            observable = client.reasoner.resolve_observable("geography:Elevation in m")
            if observable.semantics.urn != "geography:Elevation":
                raise ValueError("Semantic contract mismatch")
        def model():
            if client.resources.retrieve(model_urn, "MODEL", max_response_bytes=1024 * 1024).urn != model_urn:
                raise ValueError("Model identity mismatch")
        check("READ-SEMANTIC", semantics)
        check("READ-MODEL", model)

    try:
        if mode == "replay":
            with Client(endpoints["runtime"], token, http_transport=transport,
                        reasoner=Endpoint(endpoints["reasoner"], token),
                        resources=Endpoint(endpoints["resources"], token), timeout=3) as client:
                reads(client)
        else:
            references = tuple(ServiceReference(name, endpoints[name]) for name in SERVICES)
            profile = DeploymentProfile("explicit-contract-comparison", references, timeout=12)
            provider = IssuedTokenProvider(ConnectionIdentity("contract-comparison"), {url: token for url in endpoints.values()})
            try:
                with connect(profile, credentials=provider, initialize=False, http_transport=transport) as connection:
                    if mode == "real":
                        expected = {row["name"]: row["serviceId"] for row in readiness["services"]}
                        if any(connection.selected[name].service_id != expected[name] for name in SERVICES):
                            raise ValueError("Real service identity drifted since gate receipt")
                    results.append({"id": "READ-DISCOVERY", "backend": backend, "status": "PASS"})
                    reads(connection.client)
            except Exception as error:
                results.append({"id": "READ-DISCOVERY", "backend": backend, "status": "FAIL", "error_type": type(error).__name__})
    finally:
        transport.inner.close()
    return {"backend": backend, "status": "PASS" if all(row["status"] == "PASS" for row in results) else "FAIL",
            "cases": results, "trace": trace, "scientific_execution": "NOT_RUN", "fallbacks": 0,
            "java_gaps": [{"case": row["id"], "classification": "NOT_YET_TESTED",
                           "observation": "Real contract check failed; root cause remains untriaged", "error_type": row["error_type"]}
                          for row in results if mode == "real" and row["status"] == "FAIL"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("simulated", "real", "replay"), default="simulated")
    parser.add_argument("--endpoints", required=True, type=Path)
    parser.add_argument("--model-urn", required=True)
    parser.add_argument("--token-env", required=True, help="Explicit credential environment variable; value is never logged")
    parser.add_argument("--readiness-receipt", type=Path)
    args = parser.parse_args()
    if args.endpoints.stat().st_size > 16384:
        parser.error("Endpoint configuration exceeds byte budget")
    endpoints = json.loads(args.endpoints.read_text())
    token = os.environ.get(args.token_env)
    if not token:
        parser.error("Explicit credential environment variable is unavailable")
    readiness = None
    if args.readiness_receipt:
        if args.readiness_receipt.stat().st_size > 2 * 1024 * 1024:
            parser.error("Readiness receipt exceeds byte budget")
        readiness = json.loads(args.readiness_receipt.read_text())
    result = run(args.mode, endpoints, token, args.model_urn, readiness)
    print(json.dumps(result))
    raise SystemExit(0 if result["status"] == "PASS" else 3 if result["status"] == "NOT_RUN" else 1)


if __name__ == "__main__":
    main()
