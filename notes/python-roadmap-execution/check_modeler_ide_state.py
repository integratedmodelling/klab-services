"""Bounded read-only Modeler snapshot, not a science test or service manager.

Only loopback GETs, local process/file inspection and a read-only Bolt helper are
allowed. Writes are limited to the requested receipt and optional state document.
No retries, polling, dependency installs, context/session/Job operations or repairs.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
from itertools import islice
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import urllib.error
import urllib.request

VERSION = "2"
MAX_FILE_BYTES = 128 * 1024 * 1024
SERVICES = {"reasoner": 8091, "resources": 8092, "resolver": 8093, "runtime": 8094}
CATALOGS = ("PROJECT", "NAMESPACE", "MODEL", "RESOURCE", "RESOURCE_ADAPTER")
MODEL = "scientific.examples.usgs_virginia.elevation-quantifier"
RESOURCE = "resources.eric.lumsden:staging:usgs:virginia20211229"
BEGIN, END = "<!-- BEGIN IDE CHECK -->", "<!-- END IDE CHECK -->"
ROOT = Path(__file__).resolve().parent
HISTORICAL = "2026-11-06T03:11:16Z"
ORACLE_SHA = "22ed905702f9e8bed9dc6a98f07ff7762877459c55cb50e581ec1a6fa7519b76"
CROP_SHA = "2c9fcd0748b9ef3d8c036a59af5c1419a826ba14262a3a80d089b1d59023fb26"
SOURCE_SHA = "026ec5429ddb1197b46247dd97832e2abe246be112e7133f4fce5029fea50de5"


def mapping(value):
    return value if isinstance(value, dict) else {}


def sequence(value):
    return value if isinstance(value, list) else []


def utc():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_time(value):
    if not isinstance(value, str):
        raise ValueError("timestamp required")
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.tzinfo is None:
        raise ValueError("UTC offset required")
    return result


def freshness(receipt, now=None, max_age_hours=24):
    try:
        stamp = parse_time(receipt.get("collected_utc", receipt.get("utc")))
        age = ((now or datetime.now(timezone.utc)) - stamp).total_seconds()
        return "PASS" if 0 <= age <= max_age_hours * 3600 else "UNKNOWN"
    except (TypeError, ValueError, AttributeError):
        return "UNKNOWN"


def sanitize(value, secrets=(), home=None):
    home = str(home or Path.home())
    if isinstance(value, dict):
        return {k: ("[REDACTED]" if re.search(r"secret|password|authorization|access.token|server.key", k, re.I)
                    else sanitize(v, secrets, home)) for k, v in value.items()}
    if isinstance(value, list):
        return [sanitize(x, secrets, home) for x in value]
    if isinstance(value, str):
        for secret in secrets:
            if secret:
                value = value.replace(secret, "[REDACTED]")
        value = value.replace(home, "$HOME").replace(home.replace("\\", "/"), "$HOME")
        value = re.sub(r"(?i)Bearer\s+[^\s\"<>]+", "Bearer [REDACTED]", value)
        return re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED]", value)
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def get_json(url, headers=None, timeout=3):
    # Never forward owner credentials to redirects, a proxy, or non-loopback endpoints.
    if not re.match(r"^http://127\.0\.0\.1:(809[1-4]|8382)/", url):
        raise ValueError("checker only admits the named local Modeler services")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(urllib.request.Request(url, headers=headers or {}, method="GET"), timeout=timeout) as response:
            data = response.read(2 * 1024 * 1024 + 1)
            if len(data) > 2 * 1024 * 1024:
                return {"check": "UNKNOWN", "error_type": "ResponseBudgetExceeded"}
            return {"check": "PASS", "http": response.status, "data": json.loads(data)}
    except urllib.error.HTTPError as failure:
        return {"check": "FAIL", "http": failure.code, "error_type": "HTTPError"}
    except (OSError, ValueError) as failure:
        return {"check": "UNKNOWN", "error_type": type(failure).__name__}


def processes():
    """psutil is an existing optional local extra; absence never triggers installation."""
    try:
        import psutil
    except ImportError:
        return {}
    found = {}
    suffixes = {name: f"klab.server.{name}" for name in SERVICES}
    suffixes["graphdb"] = "klab.support.graphdb"
    for process in psutil.process_iter(["name"]):
        try:
            if (process.info["name"] or "").lower() not in {"java.exe", "javaw.exe"}:
                continue
            cwd = process.cwd()
            for name, suffix in suffixes.items():
                if Path(cwd).name != suffix:
                    continue
                parent = process.parent()
                cmd = " ".join(parent.cmdline()) if parent else ""
                found[name] = {"pid": process.pid, "parent_pid": process.ppid(), "cwd": cwd,
                               "java": process.exe(), "build": Path(cwd).parent.name,
                               "modeler_parent": bool(re.search("modeler", cmd, re.I))}
        except (psutil.Error, OSError):
            continue
    return found


def warning_messages(value):
    result = []
    if isinstance(value, dict):
        if str(value.get("level", "")).lower() == "warning":
            result.append(str(value.get("message", "Warning without message")))
        for child in value.values():
            result.extend(warning_messages(child))
    elif isinstance(value, list):
        for child in value:
            result.extend(warning_messages(child))
    return list(dict.fromkeys(result))[:50]


def digest(path, max_bytes=MAX_FILE_BYTES):
    """A bounded best-effort local digest; unreadable/oversize files stay unknown."""
    sha = hashlib.sha256()
    try:
        if path.stat().st_size > max_bytes:
            return None
        with path.open("rb") as stream:
            remaining = max_bytes
            while chunk := stream.read(min(1024 * 1024, remaining + 1)):
                if len(chunk) > remaining:
                    return None
                remaining -= len(chunk)
                sha.update(chunk)
    except OSError:
        return None
    return sha.hexdigest()


def local_json(path):
    with path.open("rb") as stream:
        data = stream.read(2 * 1024 * 1024 + 1)
    if len(data) > 2 * 1024 * 1024:
        raise ValueError("Local JSON exceeds receipt budget")
    return json.loads(data.decode("utf-8-sig"))


def archives(directory):
    # Finite even if a plugin directory unexpectedly contains thousands of files.
    paths = list(islice(directory.glob("*.jar"), 9))
    return {"truncated": len(paths) > 8, "files": [
        {"name": path.name, "sha256": digest(path)} for path in paths[:8]]}


def spatial_check(process):
    if not process:
        return {"check": "UNKNOWN", "reason": "No verified local runtime process/classpath"}
    try:
        command = [process["java"], "--class-path", str(Path(process["cwd"]) / "*"),
                   str(ROOT / "ModelerSpatialReadCheck.java")]
        result = subprocess.run(command, capture_output=True, text=True, timeout=20,
                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        # JDK/driver may emit warnings. Only accept an attributable JSON result line.
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.startswith("{")]
        data = rows[-1] if rows else {}
        return {"check": "PASS" if result.returncode == 0 and data.get("required") is True
                and data.get("layers_read") is True else "UNKNOWN", "result": data,
                "exit": result.returncode, "method": "SHOW PROCEDURES + spatial.layers; READ session"}
    except (OSError, ValueError, subprocess.SubprocessError) as failure:
        return {"check": "UNKNOWN", "error_type": type(failure).__name__}


def identity_drift(previous, current):
    changes = []
    rows = previous.get("services", [])
    old = {row.get("name"): row for row in rows if isinstance(row, dict)} if isinstance(rows, list) else {}
    for row in current.get("services", []):
        prior = old.get(row["name"], {})
        for field in ("serviceId", "build", "worldview"):
            def identity(data):
                if field == "build":
                    return data.get(field) or (Path(data["cwd"]).parent.name if data.get("cwd") else None)
                if field == "worldview":
                    return mapping(data.get("semantics")).get(field) or mapping(data.get(field)).get("worldviewUrn")
                return data.get(field)
            before, after = identity(prior), identity(row)
            if before is not None and after is not None and before != after:
                changes.append({"service": row["name"], "field": field, "before": before, "after": after})
    return changes


def collect(*, offline=False, deep=False, home=None, request=get_json, process_reader=processes,
            spatial_reader=spatial_check, baseline=None):
    home = Path(home or Path.home())
    receipt = {"schema_version": 1, "checker_version": VERSION, "collected_utc": utc(),
               "mode": "offline" if offline else "deep-read-only" if deep else "read-only",
               "environment": "local-modeler-8091-8094", "machine_id": hashlib.sha256(
                   platform.node().encode()).hexdigest()[:12], "services": [], "catalogs": {},
               "historical_evidence": {"checked_utc": HISTORICAL,
                   "report": "MODELER_REAL_DEM_REMEDIATION_REPORT.md#12-remaining-failures--smallest-next-action",
                   "gates": {"G3": "PASS_HISTORICAL", "G4": "PASS_HISTORICAL", "G5": "FAILED_HISTORICAL_NULL_SCANNER",
                             "G6": "NOT_RUN", "G7": "NOT_RUN", "G8": "SEPARATE_ENVIRONMENT_REQUIRED"}},
               "blocker": {"id": "JAVA-REMOTE-SCANNER", "state": "UNVERIFIED_CURRENT",
                           "last_reproduced_utc": HISTORICAL}, "drift": []}
    secrets, auth = [], {}
    if not offline:
        for name in SERVICES:
            try:
                with (home / ".klab/services" / name / "secret.key").open() as stream:
                    secret = stream.read(16385).strip()
                if len(secret) > 16384:
                    continue
                if secret:
                    secrets.append(secret)
                    auth[name] = {"server-key": secret, "Authorization": "Bearer 018fc355-c123-7608-be4a-89ea1059c33e"}
            except OSError:
                pass
        proc = process_reader()
        for name, port in SERVICES.items():
            base = f"http://127.0.0.1:{port}/{name}"
            row = {"name": name, "endpoint": base, "checked_utc": receipt["collected_utc"]}
            row.update(proc.get(name, {}))
            status = request(base + "/public/status", auth.get(name))
            caps = request(base + "/public/capabilities", auth.get(name))
            health = request(base + "/public/health", auth.get(name))
            s, c, h = status.get("data", {}), caps.get("data", {}), health.get("data", {})
            if not all(isinstance(x, dict) for x in (s, c, h)):
                row.update(check="UNKNOWN", reason="Malformed service payload")
                receipt["services"].append(row)
                continue
            row.update({k: s.get(k) for k in ("serviceId", "operational", "available", "connected")})
            row["health"] = h.get("status")
            row["transport"] = {"status": {k: v for k, v in status.items() if k != "data"},
                                "health": {k: v for k, v in health.items() if k != "data"}}
            meta = mapping(mapping(s.get("metadata")).get("delegate"))
            row["semantics"] = {"worldview": c.get("worldviewUrn", c.get("adoptedWorldview")),
                                "consistent": c.get("consistent"), "validation": meta.get("semantic.validation"),
                                "candidate": {k: v for k, v in mapping(meta.get("semantic.candidate")).items()
                                              if k in {"acceptedGeneration", "knowledgeRevision", "logicalStatus", "beanStatus"}},
                                "warnings": warning_messages(meta)}
            components = c.get("components")
            row["components"] = [{"id": x.get("id"), "version": x.get("version"),
                                   "adapters": [a.get("name") for a in sequence(x.get("adapters")) if isinstance(a, dict)]}
                                  for x in components if isinstance(x, dict)] if isinstance(components, list) else []
            row["check"] = ("PASS" if s.get("operational") is True and s.get("connected") is True
                            and h.get("status") == "UP" else "FAIL" if status.get("check") == "PASS"
                            and s.get("operational") is False else "UNKNOWN")
            if row["check"] == "PASS" and row.get("modeler_parent") is not True:
                row["check"] = "UNKNOWN"
                row["ownership"] = "Modeler parent not verified"
            if row["check"] == "PASS" and (not isinstance(s.get("serviceId"), str)
                    or not s["serviceId"] or s["serviceId"] != c.get("serviceId")):
                row["check"] = "UNKNOWN"
                row["identity"] = "Status/capability identity missing or inconsistent"
            if deep:
                owned = home / ".klab/services" / name / "components/plugins"
                row["archive_inventory"] = archives(owned)
            receipt["services"].append(row)
        for cls in CATALOGS:
            if "resources" not in auth:
                receipt["catalogs"][cls] = {"check": "UNKNOWN", "reason": "Owner credential unavailable"}
                continue
            result = request(f"http://127.0.0.1:8092/resources/api/v1/list/{cls}", auth["resources"])
            data = result.get("data")
            receipt["catalogs"][cls] = ({"check": "PASS", "count": len(data),
                "identities": [{k: x.get(k) for k in ("urn", "projectName", "name", "adapterType") if x.get(k) is not None}
                               for x in data[:100] if isinstance(x, dict)]}
                if isinstance(data, list) else {k: v for k, v in result.items() if k != "data"})
        graph = request("http://127.0.0.1:8382/health")
        receipt["graphdb"] = {"check": "PASS" if mapping(graph.get("data")).get("status") == "UP"
                              and proc.get("graphdb", {}).get("modeler_parent") is True else "UNKNOWN",
                              "health": mapping(graph.get("data")).get("status"), **proc.get("graphdb", {})}
        receipt["spatial"] = spatial_reader(proc.get("runtime")) if receipt["graphdb"]["check"] == "PASS" else {"check": "UNKNOWN"}
        if deep:
            receipt["retrievals"] = {}
            if "resources" in auth:
                for cls, urn in (("RESOURCE", RESOURCE), ("MODEL", MODEL)):
                    result = request(f"http://127.0.0.1:8092/resources/api/v1/retrieve/{cls}/{urn}", auth["resources"])
                    data = result.get("data", {})
                    receipt["retrievals"][cls] = {"check": "PASS" if isinstance(data, dict) and data.get("urn") == urn else "UNKNOWN",
                        "urn": data.get("urn") if isinstance(data, dict) else None,
                        "serviceId": data.get("serviceId") if isinstance(data, dict) else None}
                    if cls == "RESOURCE" and isinstance(data, dict):
                        metadata = mapping(mapping(data.get("metadata")).get("delegate"))
                        receipt["retrievals"][cls]["source_sha256"] = mapping(metadata.get("source")).get("sha256", metadata.get("source.sha256"))
            plugin_dir = home / ".klab/services/graphdb/plugins"
            receipt["graphdb"]["plugin_inventory"] = archives(plugin_dir)
            custody = home / ".klab/scientific-examples/usgs-3dep-virginia"
            receipt["archived_evidence"] = {}
            for relative in ("reference/oracle.json", "evidence/spatial-corrected-elevation-job-result.json",
                             "evidence/spatial-final-preservation.json"):
                path = custody / relative
                try:
                    data = local_json(path)
                    if not isinstance(data, dict):
                        raise ValueError("Archived evidence object required")
                    receipt["archived_evidence"][relative] = {"check": "PASS", "sha256": digest(path),
                        "interpretation": "archived, not a fresh scientific execution",
                        **({"empty": data.get("empty"), "notification_levels": [x.get("level") for x in sequence(data.get("notifications")) if isinstance(x, dict)]}
                           if relative.endswith("job-result.json") else {})}
                except (OSError, ValueError):
                    receipt["archived_evidence"][relative] = {"check": "UNKNOWN"}
            crop = custody / "reference/virginia-native-20x20.tif"
            oracle = receipt["archived_evidence"].get("reference/oracle.json", {})
            receipt["native_fixture"] = {"check": "PASS" if digest(crop) == CROP_SHA
                                         and oracle.get("sha256") == ORACLE_SHA else "UNKNOWN"}
    receipt["gates"] = gates(receipt)
    if baseline is not None:
        receipt["baseline_freshness"] = freshness(baseline)
        receipt["drift"] = identity_drift(baseline, receipt)
    receipt["source_sha256"] = digest(Path(__file__))
    try:
        receipt["repository_commit"] = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT,
            capture_output=True, text=True, timeout=5).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        receipt["repository_commit"] = "UNKNOWN"
    return sanitize(receipt, secrets, home)


def gates(receipt):
    rows = receipt.get("services", [])
    semantic = next((x.get("semantics", {}) for x in rows if x["name"] == "reasoner"), {})
    health = [x.get("check") for x in rows] + [receipt.get("graphdb", {}).get("check")]
    g0 = "PASS" if len(rows) == 4 and all(x == "PASS" for x in health) else "FAIL" if "FAIL" in health else "UNKNOWN"
    g1 = "PASS" if (semantic.get("worldview") == "imod" and semantic.get("consistent") is True
        and re.fullmatch(r"accepted:[1-9][0-9]*:ready", str(semantic.get("validation", "")))) else "UNKNOWN"
    catalogs = receipt.get("catalogs", {})
    def has(kind, value):
        return any(value in (x.get("urn"), x.get("name")) for x in catalogs.get(kind, {}).get("identities", []))
    fixture = receipt.get("native_fixture", {}).get("check") == "PASS"
    source = receipt.get("retrievals", {}).get("RESOURCE", {}).get("source_sha256") == SOURCE_SHA
    return {"G0": g0, "G1": g1,
            "G2": "PASS" if fixture and source and has("MODEL", MODEL) and has("RESOURCE", RESOURCE) and has("RESOURCE_ADAPTER", "raster") else "UNKNOWN",
            "G3": "UNKNOWN", "G4": "UNKNOWN", "G5": "UNKNOWN", "G6": "UNKNOWN", "G7": "UNKNOWN", "G8": "NOT_APPLICABLE"}


def render(receipt):
    lines = [BEGIN, f"Last checked UTC: **{receipt['collected_utc']}**; checker v{VERSION}; mode `{receipt['mode']}`.",
             f"Source SHA256 `{receipt['source_sha256']}`; checkout `{receipt['repository_commit']}`.",
             "", "| Service | Current classification | Operational | Connected | Health | ID / observed build |",
             "|---|---|---|---|---|---|"]
    for row in receipt["services"]:
        lines.append(f"| {row['name']} | {row['check']} | {row.get('operational', 'UNKNOWN')} | {row.get('connected', 'UNKNOWN')} | {row.get('health', 'UNKNOWN')} | {row.get('serviceId', 'UNKNOWN')} / {row.get('build', 'UNKNOWN')} |")
    lines += ["", f"Graphdb: `{receipt.get('graphdb', {}).get('check', 'UNKNOWN')}`; spatial procedure read: `{receipt.get('spatial', {}).get('check', 'UNKNOWN')}`.",
              f"Current read-only gates: `{json.dumps(receipt['gates'], sort_keys=True)}`.",
              f"Identity drift: `{json.dumps(receipt['drift'], sort_keys=True)}`.",
              "G3/G4 are not freshly passed by procedure discovery or catalog reads; no contexts/Jobs submitted.",
              "G5 remains a historical reproduced Java failure, not a new execution or a verified fix.", END]
    return "\n".join(lines)


def update_document(path, receipt):
    text = path.read_text(encoding="utf-8")
    if text.count(BEGIN) != 1 or text.count(END) != 1:
        raise ValueError("State document must contain one generated check block")
    start, end = text.index(BEGIN), text.index(END) + len(END)
    if end <= start:
        raise ValueError("Generated check markers must be ordered")
    text = text[:start] + render(receipt) + text[end:]
    text += f"\n- {receipt['collected_utc']}: checker v{VERSION}, `{receipt['mode']}`, G0={receipt['gates']['G0']}; G3–G7 not executed; drift={len(receipt['drift'])}.\n"
    path.write_text(text, encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--deep", action="store_true", help="Nonmutating retrieval/hash/archive checks only")
    parser.add_argument("--baseline", type=Path, default=Path.home() /
                        ".klab/scientific-examples/usgs-3dep-virginia/evidence/final-authorized-install-state.json")
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--update-document", action="store_true")
    args = parser.parse_args(argv)
    baseline = None
    if args.baseline:
        try:
            baseline = local_json(args.baseline)
            if not isinstance(baseline, dict):
                baseline = {}
        except (OSError, ValueError):
            baseline = {}
    receipt = collect(offline=args.offline, deep=args.deep, baseline=baseline)
    output = args.receipt or (Path.home() / ".klab/scientific-examples/usgs-3dep-virginia/evidence" /
                             ("ide-state-" + receipt["collected_utc"].replace(":", "-") + ".json"))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    if args.update_document:
        update_document(ROOT / "MODELER_IDE_STATE.md", receipt)
    print(render(receipt))
    print("Receipt:", sanitize(str(output)))
    return 2 if "FAIL" in receipt["gates"].values() else 0 if all(receipt["gates"][x] == "PASS" for x in ("G0", "G1", "G2")) else 3


if __name__ == "__main__":
    raise SystemExit(main())
