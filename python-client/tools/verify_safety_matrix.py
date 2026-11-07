"""Installed safety regression; no implicit replay, repair or native approval."""
from verify_onboarding_matrix import main as verify

GROUPS = {
    'sdk-safety-precision-refresh-transport': [
        'python-client/tests/unit/test_client.py',
        'python-client/tests/unit/test_async_client.py',
        'python-client/tests/unit/test_async_transport.py',
        'python-client/tests/unit/test_graph.py',
        'python-client/tests/unit/test_async_graph.py',
        'python-client/tests/unit/test_graph_projection.py',
        'python-client/tests/unit/test_scientific_snapshots.py',
        'python-client/tests/unit/test_scientific_analysis.py',
        'python-client/tests/unit/test_read_semantics_isolation.py',
        'python-client/tests/unit/test_indexed_offset_long_bounds.py',
        'python-client/tests/unit/test_indexed_source_id_long_bounds.py',
        'python-client/tests/unit/test_scan_slices.py',
        'python-client/tests/unit/test_exports.py',
        'python-client/tests/unit/test_async_exports.py',
        'python-client/tests/unit/test_connected_workflow_safety.py',
        'python-client/tests/unit/test_source_credential_preflight.py'],
    'isolated-public-core-nooptional-offline': [
        'python-client/tests/integration/test_core_optional_isolation.py',
        'python-client/tests/integration/test_scientific_snapshot_process.py'],
    'public-http-partial-offline-no-replay': [
        'developer-tools/klab-service-simulator/tests/test_partial_refresh.py',
        'developer-tools/klab-service-simulator/tests/test_graph_convergence.py',
        'developer-tools/klab-service-simulator/tests/test_revision_convergence.py',
        'developer-tools/klab-service-simulator/tests/test_offline_snapshots.py',
        'developer-tools/klab-service-simulator/tests/test_exports.py',
        'developer-tools/klab-service-simulator/tests/test_replay.py',
        'developer-tools/klab-service-simulator/tests/test_read_plan_analysis.py'],
}


def main(argv=None):
    return verify(argv, groups=GROUPS, requirements=['RC-P3.2', 'CAP-08', 'CAP-12', 'CAP-18'],
                  matrix_name='safety-regression',
                  scope='Installed public stale/partial graph refresh, scalar precision/missingness, exact '
                        'SDK-only socket-forbidden offline restoration and optional-import-blocked core APIs. '
                        'Explicit dataframe extra error, no implicit replay/fallback, interrupted/denied '
                        'exports preserve user files. Reference HTTP contracts are not native compatibility, '
                        'science, complete graph/history or hostile-code isolation authority.',
                  external_gates=['Native complete graph/history and deployment tuple acceptance',
                                  'Approved OS/container hostile-code and hard-disk enforcement',
                                  'Representative native provider scale/soak workload'])


if __name__ == '__main__':
    main()
