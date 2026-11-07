"""Fresh exact-SDK process with optional imports and network explicitly prohibited."""
import json
from pathlib import Path
import subprocess
import sys

import klab_client


def test_public_core_exact_scalar_snapshot_without_optional_dependencies_or_network():
    source = Path(klab_client.__file__).resolve()
    code = '''
import builtins,json,socket,sys
from decimal import Decimal
from pathlib import Path
blocked={'numpy','pandas','xarray','rasterio','geopandas','shapely','nbformat','nbclient','ipykernel','matplotlib','pika'}
original=builtins.__import__
def no_optional(name,*args,**kwargs):
    if name.split('.')[0] in blocked:
        raise ModuleNotFoundError('Optional dependency intentionally absent: '+name)
    return original(name,*args,**kwargs)
builtins.__import__=no_optional
def no_network(*args,**kwargs):
    raise AssertionError('Offline core probe attempted network access')
socket.socket.connect=no_network
socket.create_connection=no_network
import klab_client
from klab_client import ScientificData,ScientificDataOrigin
from klab_client.errors import UnsupportedOperationError,InvalidRequestError
assert Path(klab_client.__file__).resolve()==Path(EXPECTED_SDK)
data=ScientificData(42,'example:Sample in m','m',None,(4,0,4,9),'D2_YX',{},
    (9007199254740993,Decimal('1.2300'),0,None),
    ('9007199254740993','1.2300','0','null'),
    origin=ScientificDataOrigin('https://runtime.invalid/runtime','s.c','s.c','home'),
    artifact_type='NUMBER',contextualization='MEASURE')
restored=ScientificData.loads(data.dumps())
assert restored.offsets==(4,0,4,9) and restored.values[0]==2**53+1
assert restored.values[1].as_tuple()==Decimal('1.2300').as_tuple()
assert restored.values[2:]==(0,None) and restored.origin.runtime_id=='home'
try:
    restored.to_dataframe()
except UnsupportedOperationError as error:
    assert '[tabular]' in str(error)
else:
    raise AssertionError('Optional dataframe silently succeeded or fell back')
try:
    ScientificData.loads('{"version":999}')
except InvalidRequestError:
    pass
else:
    raise AssertionError('Future schema silently accepted')
assert not blocked.intersection(sys.modules)
assert 'klab_simulator' not in sys.modules
print(json.dumps({'exact_sdk':True,'optional_imports':False,'network':False,'native':'NOT_RUN'}))
'''
    bootstrap = f"import sys;sys.path.insert(0,{str(source.parents[1])!r});EXPECTED_SDK={str(source)!r}\n"
    result = subprocess.run([sys.executable, '-I', '-c', bootstrap + code], capture_output=True,
                            text=True, timeout=30, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    assert result.returncode == 0, result.stdout + result.stderr
    assert json.loads(result.stdout) == {'exact_sdk': True, 'optional_imports': False,
                                         'network': False, 'native': 'NOT_RUN'}
