"""Installed-core, fresh-process local snapshot demonstration; no live Java claim.

Run: python -I examples/offline_scientific_read.py
"""
from decimal import Decimal
from pathlib import Path
import subprocess
import sys

import klab_client
from klab_client import ScientificData, ScientificDataOrigin


data = ScientificData(42, "example:Sample in m", "m", None, (4, 0, 4, 9), "D2_YX", {},
    (9007199254740993, Decimal("1.2300"), 0, None),
    ("9007199254740993", "1.2300", "0", "null"),
    origin=ScientificDataOrigin("https://runtime.invalid/runtime", "s.c", "s.c", "home"),
    artifact_type="NUMBER", contextualization="MEASURE")
# This input is an independent local scalar fixture, not a newly executed model.
code = """
import socket, sys
from decimal import Decimal
from klab_client import ScientificData
def forbidden(*args, **kwargs):
    raise AssertionError('Offline snapshot attempted a network connection')
socket.socket.connect = forbidden
restored = ScientificData.loads(sys.stdin.read())
assert restored.offsets == (4, 0, 4, 9)
assert restored.values[0] == 2**53 + 1
assert restored.values[1].as_tuple() == Decimal('1.2300').as_tuple()
assert restored.values[2:] == (0, None)
assert restored.origin.runtime_id == 'home' and restored.units == 'm'
assert all(name not in sys.modules for name in ('pandas', 'numpy', 'xarray', 'rasterio'))
try:
    ScientificData.loads('{"version": 999}')
except Exception as error:
    assert type(error).__name__ == 'InvalidRequestError'
else:
    raise AssertionError('Future schema must fail')
print('PASS: offline fresh process; exact integer/Decimal/zero/null, order, origin and units; invalid schema rejected')
"""
# -I ignores PYTHONPATH. Preserve the actual selected installed SDK explicitly so
# a pip-target acceptance run cannot silently measure another global installation.
source = Path(klab_client.__file__).resolve()
bootstrap = (f"import sys;sys.path.insert(0,{str(source.parents[1])!r});"
             f"import klab_client;from pathlib import Path;assert Path(klab_client.__file__).resolve()==Path({str(source)!r});\n")
subprocess.run([sys.executable, "-I", "-c", bootstrap + code], input=data.dumps(), text=True, check=True,
               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
