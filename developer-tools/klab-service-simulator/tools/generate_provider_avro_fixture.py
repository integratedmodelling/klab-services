"""Independent offline fastavro oracle; never imported by SDK or simulator service.

Install fastavro1.13.1 in a development environment to regenerate/check. Inputs
are the checked-in generated Java DataRequest schema and finite synthetic values.
No Java service execution or scientific reasoning is performed.
"""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = Path(__file__).resolve().parents[1] / "src/klab_simulator/fixtures/provider-avro.json"


def generate():
    import fastavro
    if fastavro.__version__ != "1.13.1":
        raise ValueError("Pin the independent fixture oracle to fastavro1.13.1")
    source = ROOT / "klab.core.common/src/main/java/org/integratedmodelling/klab/common/data/DataRequest.java"
    line = next(line for line in source.read_text().splitlines() if "new org.apache.avro.Schema.Parser().parse(" in line)
    schema = json.loads(json.loads(line.split(".parse(", 1)[1].rsplit(");", 1)[0]))
    instance = schema["fields"][-1]["type"][1]
    def encode(schema, value):
        stream = BytesIO(); fastavro.schemaless_writer(stream, schema, value)
        return stream.getvalue().hex()
    base = dict(name="synthetic", geometry="1", observable="synthetic:Value", identity="synthetic-provider",
        fillingCurve="DN_LINEAR", empty=False, notifications=[], metadata={"unit": "m", "source": "SIMULATED_PROVISIONAL"},
        instances=None, doubleData=[0.], longData=None, intData=None, floatData=None, dataKey=None)
    cases = []
    for mode in ("number", "input", "event", "empty", "error", "malformed", "media"):
        request = dict(observable="synthetic:Value", geometry="1", startTime=1 if mode == "event" else 0,
            endTime=2 if mode == "event" else 0, resourceUrns=["urn:synthetic:provider:" + mode],
            inputData=base if mode == "input" else None)
        result = {**base, "doubleData": [-2.5] if mode == "input" else [1.25] if mode == "event" else [0.]}
        if mode == "empty": result.update(empty=True, doubleData=None)
        if mode == "error": result["notifications"] = [dict(message="Synthetic provider fixture error",
            activityUrn=None, metadata=None, code=None, level="ERROR")]
        cases.append(dict(case=mode, request=request, expected_instance=result,
            request_hex=encode(schema, request), response_hex="626164" if mode == "malformed" else encode(instance, result),
            response_media="application/json" if mode == "media" else "application/octet-stream"))
    return dict(version=1, source="SIMULATED_PROVISIONAL", oracle="fastavro1.13.1 raw datum, not Java execution",
        schema_canonical_sha256=hashlib.sha256(json.dumps(schema, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
        source_java_ref=source.relative_to(ROOT).as_posix(), cases=cases)


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = json.dumps(generate(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != rendered: raise ValueError("Independent provider fixture drift")
    else: OUTPUT.write_text(rendered, encoding="utf-8")


if __name__ == "__main__": main()
