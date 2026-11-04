import unittest

from artifact_manifest import selected


class TreeParsingTest(unittest.TestCase):
    def test_omitted_conflicts_are_not_resolved_artifacts(self):
        tree = '''[INFO] --- dependency:3.7.1:tree (default-cli) @ runtime ---
[INFO] +- com.fasterxml.jackson.core:jackson-core:jar:2.19.0:compile
[INFO] |  +- (com.fasterxml.jackson.core:jackson-core:jar:2.18.3:compile - omitted for conflict with 2.19.0)
[INFO] \\- org.geotools.xsd:gt-xsd-core:jar:34.4:runtime
'''
        result = selected(tree)
        self.assertEqual([r['version'] for r in result], ['2.19.0', '34.4'])
        self.assertEqual(result[1]['group'], 'org.geotools.xsd')

    def test_modules_and_managed_versions_remain_distinct(self):
        tree = '''[INFO] --- dependency:3.7.1:tree (default-cli) @ core ---
[INFO] +- com.h2database:h2:jar:1.1.119:compile (version managed from 1.4.197)
[INFO] --- dependency:3.7.1:tree (default-cli) @ server ---
[INFO] +- com.h2database:h2:jar:1.4.197:compile
'''
        result = selected(tree)
        self.assertEqual([(r['module'], r['version']) for r in result], [('core', '1.1.119'), ('server', '1.4.197')])

    def test_no_heading_is_rejected(self):
        with self.assertRaises(ValueError):
            selected('[INFO] +- com.h2database:h2:jar:1.4.197:compile')

    def test_unrelated_dependencies_are_not_selected(self):
        self.assertEqual(selected('[INFO] +- unrelated:example:jar:1.0:compile'), [])


if __name__ == '__main__':
    unittest.main()
