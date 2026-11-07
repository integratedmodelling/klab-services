"""Installed owned restoration/lifecycle matrix, not native incarnation/retention approval."""
from verify_onboarding_matrix import main as verify

GROUPS = {
    'sdk-restoration-binding': [
        'python-client/tests/unit/test_saved_handles.py',
        'python-client/tests/unit/test_async_saved_handles.py',
        'python-client/tests/unit/test_async_restoration_workflow.py',
        'python-client/tests/unit/test_provider_handles.py',
        'python-client/tests/unit/test_job_binding.py',
        'python-client/tests/unit/test_job_result_envelope.py'],
    'public-http-restoration-lifecycle': [
        'developer-tools/klab-service-simulator/tests/test_integrated_restore.py',
        'developer-tools/klab-service-simulator/tests/test_lifecycle.py',
        'developer-tools/klab-service-simulator/tests/test_durable_recovery.py',
        'developer-tools/klab-service-simulator/tests/test_bootstrap_recovery.py'],
    'public-http-provider-selection-grants': [
        'developer-tools/klab-service-simulator/tests/test_provider_avro.py',
        'developer-tools/klab-service-simulator/tests/test_observation_selections.py',
        'developer-tools/klab-service-simulator/tests/test_shared_access.py'],
}


def main(argv=None):
    return verify(argv, groups=GROUPS, requirements=['RC-P1.2', 'RC-P3.3', 'CAP-07'],
                  matrix_name='restoration-lifecycle',
                  scope='Installed sync/async saved context/observation/provider/Job binding and independent '
                        'owned HTTP restart/expiry/cancel/identity-fencing/no-replay. Fresh optimized SDK-only '
                        'async process restores after reference restart without creation/disposal or gaining ownership. '
                        'Provider/shared/selection references preserve explicit authorization. Simulator retention '
                        'and worker recovery are not native incarnation/durable-history/retention approval.',
                  external_gates=['Native context/service incarnation and durable retention',
                                  'Native permission/TTL reconciliation',
                                  'Native worker deployment/recovery authority'])


if __name__ == '__main__':
    main()
