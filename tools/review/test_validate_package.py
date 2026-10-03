import json
from pathlib import Path
import shutil
import tempfile
import unittest
import yaml
from validate_package import validate_package, digest, ROOT

FIXTURE = ROOT / 'klab.services.resources/src/test/resources/review/hydrology'

class PackageValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        shutil.copytree(FIXTURE, self.root, dirs_exist_ok=True)
    def tearDown(self): self.temp.cleanup()
    def manifest(self): return json.loads((self.root / 'manifest.json').read_text())
    def save(self, m): (self.root / 'manifest.json').write_text(json.dumps(m))
    def statuses(self, result): return {c['kind']: c['status'] for c in result['checks']}
    def rehash(self, role):
        m = self.manifest()
        for a in m['artifacts']:
            if a['role'] == role: a['sha256'] = digest((self.root / a['path']).read_bytes())
        self.save(m)
    def test_real_hydrology_schema_passes_but_acceptance_stays_blocked(self):
        result = validate_package(self.root)
        self.assertEqual('PASS', self.statuses(result)['DOCUMENT_SCHEMA'])
        self.assertEqual('PASS', self.statuses(result)['PILOT_MANIFEST'])
        self.assertEqual('BLOCKED', self.statuses(result)['IMPORT_CONTEXT'])
        self.assertEqual('BLOCKED', result['acceptance'])
    def test_corrupt_saved_bytes_fail_manifest(self):
        (self.root / 'candidate.kwv').write_bytes(b'corrupt')
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_artifact_revision_mismatch(self):
        m=self.manifest(); m['artifacts'][2]['revisionId']='other'; self.save(m)
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_proposal_internal_revision_mismatch_even_after_rehash(self):
        p=self.root/'proposal.yaml'; p.write_text(p.read_text().replace('revision_id: hydrology-surface-catchment-r1', 'revision_id: forged'))
        self.rehash('PROPOSAL')
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_full_schema_detects_missing_required_title(self):
        p=self.root/'proposal.yaml'; doc=yaml.safe_load(p.read_text()); del doc['proposal']['title']; p.write_text(yaml.safe_dump(doc)); self.rehash('PROPOSAL')
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['DOCUMENT_SCHEMA'])
    def test_duplicate_yaml_key_rejected(self):
        p=self.root/'proposal.yaml'; p.write_text(p.read_text()+'\nproposal: {}\n'); self.rehash('PROPOSAL')
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_duplicate_role_and_missing_fourth_artifact_rejected(self):
        m=self.manifest(); m['artifacts'][3]['role']='DOSSIER'; self.save(m)
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_path_escape_rejected_before_read(self):
        m=self.manifest(); m['artifacts'][0]['path']='../outside.yaml'; self.save(m)
        self.assertEqual('FAIL', self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_changed_current_import_rejected(self):
        m=self.manifest()
        for i in m['imports']: i['sha256']='a'*64
        self.save(m)
        current={i['id']:dict(revision=i['revision'],sha256='b'*64) for i in m['imports']}
        self.assertEqual('FAIL', self.statuses(validate_package(self.root,current))['IMPORT_CONTEXT'])
    def test_forged_scientific_and_root_flags_never_approve(self):
        m=self.manifest(); m['scientificReview']='APPROVED'; m['rootContext']='PASS'; self.save(m)
        result=validate_package(self.root)
        self.assertEqual('BLOCKED',self.statuses(result)['SCIENTIFIC_REVIEW'])
        self.assertEqual('BLOCKED',self.statuses(result)['ROOT_CONTEXT'])
        self.assertEqual('BLOCKED',result['acceptance'])
    def test_mapping_cannot_silently_select_a_different_concept(self):
        m=self.manifest(); m['assetMapping'][0]['dossierConceptId']='hydrology-Missing'; self.save(m)
        self.assertEqual('FAIL',self.statuses(validate_package(self.root))['PILOT_MANIFEST'])

    def test_multidocument_proposal_rejected(self):
        p=self.root/'proposal.yaml'; p.write_text(p.read_text()+'---\nproposal: {}\n'); self.rehash('PROPOSAL')
        self.assertEqual('FAIL',self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_duplicate_manifest_key_rejected(self):
        p=self.root/'manifest.json'; p.write_text(p.read_text().rstrip()[:-1]+',"manifestVersion": 1}')
        self.assertEqual('FAIL',self.statuses(validate_package(self.root))['PILOT_MANIFEST'])
    def test_oversized_artifact_rejected_before_schema_parse(self):
        (self.root/'candidate.kwv').write_bytes(b'x'*(2*1024*1024+1)); self.rehash('ONTOLOGY')
        self.assertEqual('FAIL',self.statuses(validate_package(self.root))['PILOT_MANIFEST'])

if __name__ == '__main__': unittest.main()
