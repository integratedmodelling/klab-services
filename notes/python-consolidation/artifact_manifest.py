"""Hash selected Maven artifacts from captured trees, not the entire cache."""
import argparse
import datetime as dt
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from pathlib import Path


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def selected(tree):
    module = None
    result = []
    pattern = re.compile(r'(org\.hortonmachine|org\.geotools(?:\.[\w.]+)?|com\.fasterxml\.jackson[\w.]*|org\.locationtech\.jts|com\.h2database|org\.h2database|org\.orbisgis):([^: ()]+):jar:([^: ()]+):(compile|runtime|test|provided)')
    for line in tree.splitlines():
        match = re.search(r' @ ([^ ]+) ---', line)
        if match:
            module = match[1]
        if 'omitted for' in line:
            continue
        for group, artifact, version, scope in pattern.findall(line):
            if module is None:
                raise ValueError('Artifact encountered before module heading')
            result.append({'module': module, 'group': group, 'artifact': artifact, 'version': version, 'scope': scope})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tree', type=Path, action='append', required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--effective-pom', type=Path, action='append', default=[])
    parser.add_argument('--archive', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Manifest destination must be fresh')
    artifacts = []
    for tree in args.tree:
        for item in selected(tree.read_text(encoding='utf-8')):
            directory = args.cache / item['group'].replace('.', '/') / item['artifact'] / item['version']
            jar = directory / f'{item["artifact"]}-{item["version"]}.jar'
            item.update(tree=str(tree), tree_sha256=digest(tree), resolver_path=str(jar),
                        exists=jar.is_file(), sha256=digest(jar) if jar.is_file() else None,
                        snapshot=item['version'].endswith('-SNAPSHOT'))
            item['resolver_metadata'] = [{'path': str(path), 'sha256': digest(path)} for path in directory.glob('*')
                                         if path.is_file() and (path.suffix in ['.pom', '.xml'] or path.name == '_remote.repositories')]
            artifacts.append(item)
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    effective = []
    for pom in args.effective_pom:
        tree = ET.parse(pom).getroot()
        projects = [tree] if tree.tag.endswith('}project') else list(tree)
        effective.append({'path': str(pom), 'sha256': digest(pom),
                          'modules': [{'artifact': project.findtext('m:artifactId', namespaces=ns),
                                       'properties': {prop.tag.split('}')[-1]: prop.text for prop in project.findall('m:properties/*', ns)},
                                       'repositories': [entry.findtext('m:url', namespaces=ns) for entry in project.findall('m:repositories/m:repository', ns)]} for project in projects]})
    archives = []
    for archive in args.archive:
        with zipfile.ZipFile(archive) as contents:
            names = contents.namelist()
            prefixes = ('com/fasterxml/jackson/', 'org/geotools/', 'org/hortonmachine/', 'org/h2/', 'org/h2gis/')
            entries = [entry for entry in names if entry.endswith('.jar') or entry.startswith(prefixes)]
            coordinates = []
            for name in names:
                if name.startswith('META-INF/maven/') and name.endswith('/pom.properties'):
                    properties = {}
                    for line in contents.read(name).decode('utf-8', errors='replace').splitlines():
                        if line and not line.startswith('#') and '=' in line:
                            key, value = line.split('=', 1)
                            properties[key.strip()] = value.strip()
                    coordinates.append({'entry': name, 'properties': properties})
            duplicates = [name for name, count in Counter(names).items() if count > 1 and name.endswith('.class')]
        archives.append({'path': str(archive), 'sha256': digest(archive), 'relevant_entries': entries,
                         'packaged_coordinates': coordinates, 'duplicate_class_entries': duplicates,
                         'running_deployment_identity': 'NOT_VERIFIED'})
    record = {'recorded_at_utc': dt.datetime.now(dt.UTC).isoformat(),
              'status': 'IN_PROGRESS', 'loaded_versions': 'NOT_RUN', 'artifacts': artifacts,
              'effective_poms': effective, 'packaged_archives': archives}
    args.output.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'artifacts': len(artifacts), 'missing': sum(not a['exists'] for a in artifacts),
                      'archives': len(archives), 'sha256': digest(args.output)}))


if __name__ == '__main__':
    main()
