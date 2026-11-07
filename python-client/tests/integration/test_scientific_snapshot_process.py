"""An installed client inspects a local snapshot in an isolated new process."""
from pathlib import Path
import subprocess
import sys

import klab_client


def test_offline_snapshot_demo_in_fresh_process():
    example = Path(__file__).resolve().parents[2] / "examples/offline_scientific_read.py"
    source = Path(klab_client.__file__).resolve()
    bootstrap = (f"import sys,runpy;sys.path.insert(0,{str(source.parents[1])!r});"
                 f"import klab_client;from pathlib import Path;assert Path(klab_client.__file__).resolve()==Path({str(source)!r});"
                 f"runpy.run_path({str(example)!r},run_name='__main__')")
    result = subprocess.run([sys.executable, "-I", "-c", bootstrap], capture_output=True, text=True, timeout=30,
                            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    assert result.returncode == 0, result.stderr
    assert "PASS: offline fresh process" in result.stdout
