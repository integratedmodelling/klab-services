"""Render short, explicitly labeled result walkthroughs from passing functional JUnit evidence.

This is not desktop capture or a performance recording. API sketches accompany
actual verified results; service waiting time is omitted. Requires [analysis,demo].
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import subprocess
import textwrap
import xml.etree.ElementTree as ET

PROPERTIES = {"connection_scientific_workflow", "typed_observe", "saved_handles", "native_geotiff",
              "release_notebook_workflow", "local_scientific_lifecycle", "connection_access"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_evidence(paths):
    """Reject failed/skipped/empty reports; never copy private configs or arbitrary output."""
    records, sources = {}, {}
    for path in paths:
        with Path(path).open("rb") as stream:
            contents = stream.read(10 * 1024 * 1024 + 1)
        require(len(contents) <= 10 * 1024 * 1024 and b"<!DOCTYPE" not in contents.upper(), "Invalid/oversized JUnit report")
        root = ET.fromstring(contents)
        cases = list(root.iter("testcase"))
        require(cases and not any(list(root.iter(tag)) for tag in ("failure", "error", "skipped")),
                "Demos require complete passing functional reports without skips")
        for suite in root.iter("testsuite"):
            require(all(int(suite.get(key, "0")) == 0 for key in ("failures", "errors", "skipped"))
                    and int(suite.get("tests", "0")) == len(suite.findall("testcase")), "Incomplete JUnit suite counts")
        sources[Path(path).name] = hashlib.sha256(contents).hexdigest()
        for case in cases:
            for prop in case.findall("properties/property"):
                name = prop.get("name")
                if name in PROPERTIES:
                    records.setdefault(name, []).append(json.loads(prop.get("value", "")))
    require(PROPERTIES <= records.keys(), "Missing feature evidence; run all documented functional suites first")
    return records, sources


def validate_run(path):
    """Bind clips to the actually tested SDK and successful owned stack cleanup."""
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    require(payload.get("passed") is True and payload.get("cleanup_exit_code") == 0, "Owned acceptance/cleanup did not pass")
    package = Path(__file__).resolve().parents[1]
    actual = {p.relative_to(package).as_posix(): hashlib.sha256(p.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
              for p in (package / "src/klab_client").rglob("*.py")}
    observed = {name.replace("\\", "/"): value for name, value in payload["client_production_sha256_lf"].items()}
    require(actual == observed, "SDK source changed since functional capture; rerun acceptance before making demos")
    return {"client_head": payload["client_commit"], "client_dirty": payload["client_dirty"],
            "server_checkout": payload["server_commit"], "component_sha256": payload["component_sha256"],
            "source_manifest_sha256": hashlib.sha256(json.dumps(actual, sort_keys=True, separators=(",", ":")).encode()).hexdigest()}


@dataclass(frozen=True)
class Clip:
    name: str
    title: str
    code: tuple[str, ...]
    facts: tuple[tuple[str, ...], ...]
    raster: bool = False
    graph: bool = False


def build_clips(records):
    """Only whitelist facts actually verified by the maintained scientific scenarios."""
    connections = records["connection_scientific_workflow"]
    require({item["provider"] for item in connections} == {"certificate", "token"}, "Both credential providers must pass")
    require(all(item["scientific_check"]["verified_cells"] == 40 for item in connections), "Connection scientific checks missing")
    typed = records["typed_observe"][0]
    options = {"geometry", "scenarios", "namespace", "project", "observer", "context grid"}
    require(typed["passed"] is True and set(typed["verified_options"]) == options, "Typed request options were not verified")
    saved = {item["mode"]: item for item in records["saved_handles"][0]}
    require(set(saved) == {"create", "restore", "collaborator", "release"} and all(item["passed"] is True for item in saved.values()),
            "Saved-work journey incomplete")
    require(saved["create"]["observation_id"] == saved["restore"]["observation_id"] == saved["collaborator"]["observation_id"]
            and saved["create"]["job_id"] == saved["restore"]["job_id"]
            and saved["create"]["pending_id"] == saved["restore"]["pending_id"], "Saved identities changed")
    require(saved["collaborator"]["private_denied"] and saved["release"]["explicit_cleanup"], "Saved access/cleanup checks missing")
    geotiff = records["native_geotiff"][0]
    require(geotiff["summary"]["acceptance_passed"] and geotiff["summary"]["verified_samples"] >= 1
            and geotiff["session_cleanup"], "Native raster export was not accepted")
    analysis = records["release_notebook_workflow"][0]
    require(analysis["verified_cells"] == 20 and analysis["valid_sum"] == 409 and analysis["recorded_links"] > 0
            and analysis["partial"] is True and analysis["consistency"].startswith("unknown"), "Analysis/lineage checks missing")
    edges = analysis.get("recorded_relationships", [])
    require(len(edges) == analysis["recorded_links"] and all(type(edge.get(key)) is int and edge[key] > 0
            for edge in edges for key in ("source", "target")), "Recorded graph display evidence incomplete")
    require(analysis["connection_path"] == "certificate to issued-token fresh-process recovery"
            and analysis["valid_nonowner_positive_control"]
            and analysis["unauthorized_export"] == analysis["unauthorized_provenance"] == "AuthorizationError",
            "Native connection/authorization journey missing")
    lifecycle = records["local_scientific_lifecycle"][0]
    require(lifecycle["passed"] and lifecycle["cancellation"]["confirmed"]
            and lifecycle["failed_job"]["scientific_outcome"] == "confirmed failure", "Job lifecycle checks missing")
    access = records["connection_access"][0]
    require(access["valid_collaborator_positive_control"] and access["private_context_denial"] == "AuthorizationError",
            "Private-access positive/negative controls missing")
    return [
        Clip("connect-and-compute", "Connect. Describe. Compute.",
             ("with connect(profile,", "             credentials=provider) as connection:", "    client = connection.client",
              "    job = context.observe(ObservationRequest(...))", "    observation = job.result(timeout=120)"),
             (("ENGINE certificate AND issued-token paths verified", "Controlled TEST identity; not production Hub login"),
              ("Typed geometry, scenarios, namespace, project", "Observer selection and context grid verified"),
              ("40 scientific cell/unit checks per provider", "Explicit readiness; no anonymous fallback"))),
        Clip("save-and-resume", "Close Python. Keep your work.",
             ("save_handle(observation, 'observation.json')", "# In a fresh process, supply credentials separately",
              "observation = client.restore_handle(", "    load_handle('observation.json'))"),
             (("Secret-free versioned references", "Local close does not release remote work"),
              (f"Original observation {int(saved['restore']['observation_id'])} restored unchanged",
               f"Original Job {int(saved['restore']['job_id'])}; timed-out Job {int(saved['restore']['pending_id'])} recovered"),
              ("Allowed collaborator reads original results", "Private work denied; owner cleanup explicit"))),
        Clip("native-exports", "From observation to GIS.",
             ("observation.export_to_file(", "    'field.tif',", "    media_type='image/tiff;application=geotiff')"),
             (("Advertised native formats; unchanged server bytes", "Atomic publication; interrupted output not published"),
              ("Independent 5 x 4 coordinate/value oracle passed", "float64 + source degree_angle + one masked cell"),
              ("Valid zero preserved; orientation checked", "Uses the pinned corrected component, not old archive")), raster=True),
        Clip("analyze-and-trace", "Analysis-ready. Traceable origins.",
             ("analysis = open_raster(export_for_analysis(obs, 'field.tif'))", "ds = analysis.dataset",
              "ds.value.where(ds.valid).sum()", "trace_analysis(analysis, fetch_provenance(obs))"),
             (("Native values + explicit boolean validity", "Actual field and mask shown, not illustrative data"),
              (f"Known masked sum: {int(analysis['valid_sum'])}", f"{int(analysis['recorded_links'])} recorded incident links; explicitly partial"),
              ("Plans are not execution evidence", "Cross-fetch revision consistency remains UNKNOWN")), raster=True, graph=True),
        Clip("jobs-and-access", "Failure is not success.",
             ("try:", "    observation = job.result(timeout=...)", "except WaitTimeout as error:",
              "    save_handle(error.job, 'pending.json')", "# Cancellation acceptance != confirmed interruption"),
             (("Known failures remain failures", "Timeout retains the original Job for later polling"),
              ("Active cancellation was confirmed by the server", "No automatic replay of ambiguous submissions"),
              ("Valid nonowner positive control passed", "Private export AND provenance access rejected"))),
    ], analysis


def render(clip, output, dataset, analysis, *, ffmpeg, font_path=None):
    """Encode an 18-second, 1280x720 H.264 result walkthrough and GIF/poster."""
    from PIL import Image, ImageDraw, ImageFont
    fonts = {}
    for size in (18, 21, 34):
        for candidate in (font_path, "C:/Windows/Fonts/consola.ttf", "DejaVuSansMono.ttf"):
            if candidate is None:
                continue
            try:
                fonts[size] = ImageFont.truetype(str(candidate), size)
                break
            except OSError:
                continue
        require(size in fonts, "Supply a readable monospace --font")
    movie = output / (clip.name + ".mp4")
    frames, fps = 90, 5
    command = [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-f", "rawvideo", "-pixel_format", "rgb24",
        "-video_size", "1280x720", "-framerate", str(fps), "-i", "pipe:0", "-an", "-c:v", "libx264",
        "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(movie)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    posters = []
    try:
        for page in range(3):
            image = Image.new("RGB", (1280, 720), "#101820")
            draw = ImageDraw.Draw(image)
            draw.text((40, 32), "k.LAB / PYTHON CLIENT BRANCH PREVIEW", font=fonts[18], fill="#73daca")
            draw.text((40, 72), clip.title, font=fonts[34], fill="white")
            draw.text((40, 125), "Verified result walkthrough | controlled TEST services | waiting time omitted", font=fonts[18], fill="#b5c4d3")
            draw.rounded_rectangle((35, 174, 803, 618), radius=14, fill="#182532")
            label = "ACTUAL EXPORTED FIELD" if clip.raster and page == 1 else "API SKETCH (not a terminal recording)"
            draw.text((58, 192), label, font=fonts[18], fill="#73daca")
            y = 238
            if clip.raster and page == 1:
                values, valid = dataset.value.values, dataset.valid.values
                maximum = float(values[valid].max())
                for row in range(values.shape[0]):
                    for col in range(values.shape[1]):
                        okay = bool(valid[row, col])
                        value = float(values[row, col]) if okay else None
                        color = (40, 65 + int(150 * value / maximum), 180) if okay else (110, 55, 65)
                        x, top = 100 + col * 110, 250 + row * 70
                        draw.rectangle((x, top, x + 103, top + 63), fill=color)
                        draw.text((x + 14, top + 19), str(int(value)) if okay else "MASK", font=fonts[21], fill="white")
                draw.text((85, 554), "Actual north-up TIFF rows | zero != missing", font=fonts[18], fill="#b5c4d3")
            else:
                for line in clip.code:
                    for part in textwrap.wrap(line, width=54, replace_whitespace=False, drop_whitespace=False) or [""]:
                        draw.text((58, y), part, font=fonts[21], fill="#d9e4ef")
                        y += 33
            draw.text((835, 195), "VERIFIED RUN", font=fonts[18], fill="#73daca")
            y = 237
            for fact in clip.facts[page]:
                for line in textwrap.wrap(fact, width=30):
                    draw.text((835, y), line, font=fonts[21], fill="white")
                    y += 31
                y += 16
            if clip.graph and page == 2:
                draw.text((835, 420), "RECORDED NEIGHBORS", font=fonts[18], fill="#73daca")
                for n, edge in enumerate(analysis["recorded_relationships"][:6]):
                    text = f"{int(edge['source'])} --{edge['relationship']}--> {int(edge['target'])}"
                    draw.text((835, 450 + n * 23), text, font=fonts[18], fill="#e9c46a" if edge["evidence"] == "planned resolution" else "#b5c4d3")
            draw.text((40, 656), f"{page + 1}/3   Native bytes. Explicit ownership. Honest limits.", font=fonts[18], fill="#b5c4d3")
            for tick in range(frames // 3):
                frame = image.copy()
                overlay = ImageDraw.Draw(frame)
                if clip.raster and page == 1:
                    row, col = ((3, 0), (2, 2), (0, 4))[tick // 10]
                    x, top = 100 + col * 110, 250 + row * 70
                    overlay.rectangle((x - 2, top - 2, x + 105, top + 65), outline="white", width=3)
                    valid = bool(dataset.valid.values[row, col])
                    value = str(int(dataset.value.values[row, col])) if valid else "MASK"
                    detail = f"value={value} valid={valid}  x={float(dataset.x.values[col]):.0f} y={float(dataset.y.values[row]):.0f}"
                    overlay.text((58, 588), detail, font=fonts[18], fill="white")
                if clip.graph and page == 2:
                    count = min(len(analysis["recorded_relationships"]), 6)
                    active = min(tick * count // 30, count - 1)
                    overlay.rectangle((830, 447 + active * 23, 1238, 471 + active * 23), outline="#73daca", width=2)
                progress = (page * 30 + tick + 1) / frames
                overlay.rectangle((40, 696, 40 + int(1200 * progress), 700), fill="#73daca")
                if tick == 0:
                    posters.append(frame.copy())
                process.stdin.write(frame.tobytes())
        process.stdin.close()
        process.stdin = None
        _, errors = process.communicate(timeout=90)
        require(process.returncode == 0, "Video encoder failed: " + errors.decode(errors="replace")[:300])
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=10)
    decoded = subprocess.run([ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(movie), "-f", "null", "-"],
                             capture_output=True, timeout=60)
    require(decoded.returncode == 0 and movie.stat().st_size > 0, "Encoded demo did not decode successfully")
    posters[1 if clip.raster else 0].save(output / (clip.name + ".png"))
    posters[0].save(output / (clip.name + ".gif"), save_all=True, append_images=posters[1:], duration=6000, loop=0)
    return {"file": movie.name, "seconds": frames / fps, "width": 1280, "height": 720,
            "bytes": movie.stat().st_size, "sha256": hashlib.sha256(movie.read_bytes()).hexdigest(), "decode_verified": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", type=Path, nargs="+", required=True)
    parser.add_argument("--run-report", type=Path, required=True, help="Successful owned stack report with exact SDK source manifest")
    parser.add_argument("--raster", type=Path, required=True, help="Actual independently verified field.tif from the same owned run")
    parser.add_argument("--output", type=Path, required=True, help="Fresh directory; existing destinations never overwritten")
    parser.add_argument("--font", type=Path)
    args = parser.parse_args()
    identity = validate_run(args.run_report)
    records, sources = load_evidence(args.reports)
    clips, analysis = build_clips(records)
    from klab_client import open_raster
    result = open_raster(args.raster)
    require(result.descriptor.sha256 == analysis["artifact_sha256"], "Raster does not match verified evidence")
    require(result.descriptor.shape == (4, 5) and result.descriptor.units == "degree_angle", "Unsupported demo field")
    import imageio_ffmpeg
    args.output.mkdir(parents=True, exist_ok=False)
    videos = [render(clip, args.output, result.dataset, analysis, ffmpeg=imageio_ffmpeg.get_ffmpeg_exe(), font_path=args.font) for clip in clips]
    report = {"presentation": "API sketches and actual verified results; not screen capture; waiting time omitted",
              "deployment": "controlled TEST services, not actual Hub onboarding", "source_reports_sha256": sources,
              "tested_identity": identity,
              "raster_sha256": result.descriptor.sha256, "videos": videos}
    (args.output / "manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
