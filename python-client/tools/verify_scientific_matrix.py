"""Installed multishard/recorded-data acceptance, never native provider science."""
from verify_onboarding_matrix import main as verify

GROUPS = {
    'sdk-snapshot-export-analysis': [
        'python-client/tests/unit/test_read_plans.py',
        'python-client/tests/unit/test_read_plan_analysis.py',
        'python-client/tests/unit/test_scientific_snapshots.py',
        'python-client/tests/unit/test_scientific_analysis.py',
        'python-client/tests/unit/test_scientific_tables.py',
        'python-client/tests/unit/test_nonuniform_export_oracle.py',
        'python-client/tests/unit/test_exports.py',
        'python-client/tests/unit/test_async_exports.py',
        'python-client/tests/unit/test_async_analysis_exports.py',
        'python-client/tests/unit/test_temporal_exports.py',
        'python-client/tests/unit/test_reference_windows.py',
        'python-client/tests/unit/test_reference_raster_labels.py',
        'python-client/tests/unit/test_reference_raster_analysis.py'],
    'isolated-offline-and-table-http': [
        'python-client/tests/integration/test_scientific_snapshot_process.py',
        'python-client/tests/integration/test_scientific_tables_http.py'],
    'public-http-multishard-recorded-analysis': [
        'developer-tools/klab-service-simulator/tests/test_read_plans.py',
        'developer-tools/klab-service-simulator/tests/test_read_plan_analysis.py',
        'developer-tools/klab-service-simulator/tests/test_exports.py',
        'developer-tools/klab-service-simulator/tests/test_scientific_adapters.py',
        'developer-tools/klab-service-simulator/tests/test_offline_snapshots.py',
        'developer-tools/klab-service-simulator/tests/test_reference_windows.py',
        'developer-tools/klab-service-simulator/tests/test_reference_raster_labels.py'],
}


def main(argv=None):
    return verify(argv, groups=GROUPS, requirements=['RC-P2.1'], matrix_name='scientific-snapshot-reference',
                  scope='Public installed sync/async bounded multishard assembly, independent known-answer '
                        'precision/masks/coordinates, frozen authentic recorded GeoTIFF delivery, explicit '
                        'window/state and server-declared raster metadata, atomic failed exports and offline '
                        'inspection. Frozen fixture bytes pinned before/after alongside installed modules. '
                        'Recorded authentic data and synthetic arithmetic are not real native provider science; '
                        'missing embedded georeferencing refuses rather than inventing CRS or resampling.',
                  external_gates=['Approved actual-provider model/data/datum/tolerance oracle',
                                  'Real Java scanner/provider scientific execution',
                                  'Native dictionary/worldview/time authority'])


if __name__ == '__main__':
    main()
