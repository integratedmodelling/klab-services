"""Extract actual JVM class origins from owned -Xlog:class+load logs, not POM guesses."""
import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote, urlsplit

PREFIXES = ('org.geotools.', 'org.hortonmachine.', 'org.locationtech.jts.', 'org.h2.',
            'org.h2gis.', 'com.fasterxml.jackson.', 'org.integratedmodelling.', 'org.semanticweb.owlapi.')
PATTERN = re.compile(r'\[class,load\] ([^ ]+) source: (.+)$')


def origins(text):
    result = defaultdict(set)
    for line in text.splitlines():
        match = PATTERN.search(line)
        if match and match[1].startswith(PREFIXES):
            result[match[2]].add(match[1])
    return result


def file_origin(uri):
    parsed = urlsplit(uri)
    if parsed.scheme != 'file' or parsed.netloc not in ('', 'localhost'):
        return None
    name = unquote(parsed.path)
    if re.match(r'^/[A-Za-z]:/', name):
        name = name[1:]
    return Path(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('state', type=Path)
    args = parser.parse_args()
    rows = []
    for log in args.state.glob('*.log'):
        for uri, classes in origins(log.read_text(encoding='utf-8', errors='replace')).items():
            file = file_origin(uri)
            row = {'service_log': log.name, 'loaded_source': uri, 'classes': sorted(classes),
                   'identity': 'UNKNOWN: non-file or dynamic origin'}
            if file is not None and file.is_file():
                with file.open('rb') as stream:
                    row.update(identity='LOADED_FILE_HASHED', sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
            elif file is not None and file.is_dir():
                row.update(identity='COMPILED_DIRECTORY; individual bytes in compiled-classes.json')
            rows.append(row)
    output = args.state / 'actual-loaded-identity.json'
    if output.exists():
        raise FileExistsError('Preserve prior manifests rather than overwrite')
    output.write_text(json.dumps({'status': 'OBSERVED', 'rows': rows,
        'not_observed': 'An absent class is NOT proof of artifact absence or compatibility'}, indent=2))
    print(json.dumps({'loaded_origins': len(rows), 'hashed_files': sum('sha256' in row for row in rows),
                      'manifest_sha256': hashlib.sha256(output.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    main()
