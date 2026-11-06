"""Deterministic checker tests: no live services, credentials or subprocesses required."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location("state_checker", Path(__file__).with_name("check_modeler_ide_state.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def response(data):
    return {"check": "PASS", "http": 200, "data": data}


def process_fixture():
    return {name: {"pid": i, "parent_pid": 100, "modeler_parent": True, "build": "new-build"}
            for i, name in enumerate((*checker.SERVICES, "graphdb"), 1)}


def endpoint_fixture(url, headers=None):
    if "/list/" in url:
        kind = url.rsplit("/", 1)[1]
        rows = {"MODEL": [{"urn": checker.MODEL}], "RESOURCE": [{"urn": checker.RESOURCE}],
                "RESOURCE_ADAPTER": [{"urn": "raster"}], "PROJECT": [], "NAMESPACE": []}
        return response(rows[kind])
    if url.endswith("/health"):
        return response({"status": "UP"})
    if url.endswith("/status"):
        return response({"serviceId": "new-id", "operational": True, "connected": True,
                         "metadata": {"delegate": {"semantic.validation": "accepted:7:ready"}}})
    return response({"serviceId": "new-id", "worldviewUrn": "imod", "consistent": True, "components": []})


class CheckerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        for service in checker.SERVICES:
            path = self.home / ".klab/services" / service / "secret.key"
            path.parent.mkdir(parents=True)
            path.write_text("test-secret-never-persist")

    def collect(self, request=endpoint_fixture, **kw):
        with patch.object(checker.subprocess, "run") as run:
            run.return_value.stdout = "test-commit"
            return checker.collect(home=self.home, request=request, process_reader=process_fixture,
                                   spatial_reader=lambda process: {"check": "PASS"}, **kw)

    def test_healthy_services_never_promote_science(self):
        result = self.collect()
        self.assertEqual(result["gates"]["G0"], "PASS")
        self.assertEqual(result["gates"]["G1"], "PASS")
        for gate in ("G2", "G3", "G4", "G5", "G6", "G7"):
            self.assertEqual(result["gates"][gate], "UNKNOWN")
        self.assertEqual(result["historical_evidence"]["gates"]["G5"], "FAILED_HISTORICAL_NULL_SCANNER")
        self.assertNotIn("test-secret-never-persist", json.dumps(result))

    def test_unreachable_is_unknown_not_historical_failure_rewrite(self):
        def unavailable(url, headers=None):
            return {"check": "UNKNOWN", "error_type": "URLError"}
        result = self.collect(unavailable)
        self.assertEqual(result["gates"]["G0"], "UNKNOWN")
        self.assertEqual(result["historical_evidence"]["gates"]["G4"], "PASS_HISTORICAL")

    def test_available_does_not_mean_operational(self):
        def inoperative(url, headers=None):
            result = endpoint_fixture(url, headers)
            if url.endswith("/status"):
                result["data"].update(available=True, operational=False)
            return result
        self.assertEqual(self.collect(inoperative)["gates"]["G0"], "FAIL")

    def test_malformed_service_payload(self):
        self.assertEqual(self.collect(lambda url, headers=None: response([]))["gates"]["G0"], "UNKNOWN")

    def test_different_identity_is_drift_not_failure(self):
        prior = {"utc": checker.HISTORICAL, "services": [{"name": "runtime", "serviceId": "old-id", "build": "old-build"}]}
        result = self.collect(baseline=prior)
        self.assertEqual(len(result["drift"]), 2)
        self.assertEqual(result["gates"]["G0"], "PASS")

    def test_missing_optional_catalog_is_not_health_failure(self):
        result = self.collect(lambda url, headers=None: response([]) if "/list/" in url else endpoint_fixture(url, headers))
        self.assertEqual(result["gates"]["G0"], "PASS")
        self.assertEqual(result["gates"]["G2"], "UNKNOWN")

    def test_offline_never_connects_or_inspects_processes(self):
        with patch.object(checker, "processes", side_effect=AssertionError("process inspection")):
            result = self.collect(lambda *a: self.fail("network"), offline=True)
        self.assertEqual(result["mode"], "offline")
        self.assertEqual(result["services"], [])
        self.assertEqual(result["gates"]["G0"], "UNKNOWN")

    def test_time_validation_and_staleness(self):
        now = datetime(2026, 11, 6, 14, tzinfo=timezone.utc)
        for receipt in ({}, [], {"utc": "bad"}, {"utc": "2026-11-07T00:00:00Z"},
                        {"utc": "2026-10-01T00:00:00Z"}, {"utc": "2026-11-06T14:00:00"}):
            self.assertEqual(checker.freshness(receipt, now), "UNKNOWN")
        self.assertEqual(checker.freshness({"utc": "2026-11-06T13:59:00Z"}, now), "PASS")

    def test_redaction_known_secrets_tokens_and_home(self):
        result = checker.sanitize({"server-key": "secret", "detail": f"secret Bearer abc {self.home}"},
                                  ["secret"], self.home)
        text = json.dumps(result)
        self.assertNotIn("secret", text)
        self.assertNotIn("Bearer abc", text)
        self.assertNotIn(str(self.home), text)

    def test_markdown_keeps_history_and_valid_json(self):
        result = self.collect(offline=True)
        path = self.home / "state.md"
        path.write_text("Title\n" + checker.BEGIN + "old" + checker.END + "\nHistory: FAILED old event\n")
        checker.update_document(path, result)
        checker.update_document(path, result)
        text = path.read_text()
        self.assertIn("History: FAILED old event", text)
        self.assertEqual(text.count(checker.BEGIN), 1)
        self.assertEqual(text.count(f"checker v{checker.VERSION}, `offline`"), 2)
        self.assertEqual(json.loads(json.dumps(result)), result)

    def test_http_refuses_remote_redirect_target(self):
        with self.assertRaises(ValueError):
            checker.get_json("https://example.com/resources", {"server-key": "private"})
        self.assertIsNone(checker.NoRedirect().redirect_request(None, None, 302, "", {}, "http://evil/"))

    def test_read_only_method_and_route_surface(self):
        calls = []
        def request(url, headers=None):
            calls.append(url)
            return endpoint_fixture(url, headers)
        self.collect(request)
        self.assertTrue(calls)
        self.assertFalse(any(any(x in u.lower() for x in ("createsession", "createcontext", "/submit", "/shutdown", "/import")) for u in calls))
        java = Path(__file__).with_name("ModelerSpatialReadCheck.java").read_text()
        self.assertIn("AccessMode.READ", java)
        self.assertNotIn('session.run("CALL spatial.add', java)

    def test_generation_zero_is_not_semantic_acceptance(self):
        def unaccepted(url, headers=None):
            result = endpoint_fixture(url, headers)
            if url.endswith("/status"):
                result["data"]["metadata"]["delegate"]["semantic.validation"] = "accepted:0:ready"
            return result
        self.assertEqual(self.collect(unaccepted)["gates"]["G1"], "UNKNOWN")

    def test_historical_failure_receipt_is_archived_not_reexecuted(self):
        custody = self.home / ".klab/scientific-examples/usgs-3dep-virginia"
        path = custody / "evidence/spatial-corrected-elevation-job-result.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({"empty": True, "notifications": [{"level": "Error", "message": "null scanner"}]}))
        result = self.collect(deep=True)
        evidence = result["archived_evidence"]["evidence/spatial-corrected-elevation-job-result.json"]
        self.assertTrue(evidence["empty"])
        self.assertEqual(result["gates"]["G5"], "UNKNOWN")
        path.write_text("[]")
        self.assertEqual(self.collect(deep=True)["archived_evidence"]["evidence/spatial-corrected-elevation-job-result.json"]["check"], "UNKNOWN")

    def test_hash_and_archive_budgets_and_unreadable_file(self):
        path = self.home / "a.jar"
        path.write_bytes(b"12345")
        self.assertIsNone(checker.digest(path, max_bytes=4))
        self.assertEqual(checker.digest(path, max_bytes=5), checker.hashlib.sha256(b"12345").hexdigest())
        self.assertIsNone(checker.digest(self.home / "missing"))
        for i in range(12):
            (self.home / f"archive{i}.jar").touch()
        inventory = checker.archives(self.home)
        self.assertTrue(inventory["truncated"])
        self.assertEqual(len(inventory["files"]), 8)

    def test_local_json_budget_and_null_metadata(self):
        path = self.home / "large.json"
        path.write_bytes(b" " * (2 * 1024 * 1024 + 1))
        with self.assertRaises(ValueError):
            checker.local_json(path)
        def malformed(url, headers=None):
            result = endpoint_fixture(url, headers)
            if url.endswith("/status"):
                result["data"]["metadata"] = None
            if url.endswith("/capabilities"):
                result["data"]["components"] = [{"adapters": None}]
            return result
        self.assertEqual(self.collect(malformed)["gates"]["G1"], "UNKNOWN")

    def test_mixed_identity_cannot_pass_health_gate(self):
        def mixed(url, headers=None):
            result = endpoint_fixture(url, headers)
            if url.endswith("/capabilities"):
                result["data"]["serviceId"] = "other-service"
            return result
        self.assertEqual(self.collect(mixed)["gates"]["G0"], "UNKNOWN")

    def test_generated_markers_cannot_be_reversed(self):
        path = self.home / "bad.md"
        text = checker.END + "preserve" + checker.BEGIN
        path.write_text(text)
        with self.assertRaises(ValueError):
            checker.update_document(path, self.collect(offline=True))
        self.assertEqual(path.read_text(), text)


if __name__ == "__main__":
    unittest.main()
