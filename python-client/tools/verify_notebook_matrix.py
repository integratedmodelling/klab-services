"""Installed notebook matrix; kernel smoke and reference HTTP workflows stay separate."""
from verify_onboarding_matrix import main as verify

GROUPS = {
    'sdk-notebook-guards': [
        'python-client/tests/unit/test_async_notebook_guards.py',
        'python-client/tests/unit/test_notebook_demos.py'],
    'local-kernel-await-html-smoke': [
        'python-client/tests/integration/test_async_notebook_kernel.py',
        'python-client/tests/integration/test_milestone_notebook_kernel.py'],
    'owned-public-sdk-notebook-workflows': [
        'developer-tools/klab-service-simulator/tests/test_notebook_client.py',
        'developer-tools/klab-service-simulator/tests/test_notebook_principal_races.py',
        'developer-tools/klab-service-simulator/tests/test_reference_raster_notebook.py'],
}


def main(argv=None):
    return verify(argv, groups=GROUPS, requirements=['RC-P3.5'], matrix_name='notebook-integrated',
                  scope='Local exact-interpreter await/HTML smoke is separate from actual SDK-only owned HTTP '
                        'notebook workflows. Concurrent Jobs, own-wait cancellation, surviving same-Job and '
                        'independent-Job results, reader-close preservation, actual Bob denial, partial graph '
                        'invalidation/refresh, separate-client private-draft CAS/principal races and declared '
                        'raster metadata/codes/bands; output/secret guards and '
                        'owned kernel/session cleanup. Kernels remain explicitly unmeasured by owning-thread '
                        'operation tracing; no native scheduler/semantic/time/provider science authority.',
                  external_gates=['Native scientific/provider scheduler and permission acceptance',
                                  'Production notebook/deployment support tuple approval'])


if __name__ == '__main__':
    main()
