"""Source-only regression: never claim native execution or encoder simulation."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]


def test_pinned_client_stub_is_not_absence_of_service_encoding():
    client = (ROOT / 'klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ResolverClient.java').read_text(encoding='utf-8')
    service = (ROOT / 'klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/ResolverService.java').read_text(encoding='utf-8')
    assert re.search(r'public String encodeDataflow\(Dataflow dataflow\)\s*\{\s*//[^\n]*\n\s*return null;\s*\}', client)
    assert 'public String encodeDataflow(Dataflow dataflow)' in service
    assert 'ret.append(kdl)' in service
