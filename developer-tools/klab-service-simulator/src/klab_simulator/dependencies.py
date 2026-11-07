"""Finite native-shaped dependency groups; no edge or complete-closure claims."""
from .state import ContractError


def respond(state, method, route, principal, scope):
    if method != 'GET' or not route.startswith('/api/v1/resolve/NAMESPACE/'):
        return None
    if state.store is not None:
        raise ContractError(422, 'Finite dependency version fixture is ephemeral only')
    if route != '/api/v1/resolve/NAMESPACE/synthetic.root':
        raise ContractError(404, 'Only declared dependency root admitted')
    state._context(scope, principal)
    if principal != 'alice': raise ContractError(403, 'Finite dependency catalog restricted to Alice')
    def descriptor(urn, kind='NAMESPACE', provider=None):
        return {'resourceUrn':urn,'knowledgeClass':kind,'serviceId':provider or state.service_ids['resources'],
            'resourceVersion':{'major':1,'minor':0,'build':state.dependency_revision},
            'timestamp':100+state.dependency_revision,'optional':False,'projectUrn':'synthetic.project',
            'metadata':{'reference':'declared finite dependency group'},'accessKey':'not-public-authority'}
    return {'results':[descriptor('synthetic.root')],
        'namespaces':[descriptor('synthetic.base'),descriptor('synthetic.root')],
        'ontologies':[], 'behaviors':[], 'observationStrategies':[],
        'projects':[descriptor('synthetic.project','PROJECT')],
        'resources':[descriptor('synthetic:resource','RESOURCE','external-provider')],
        'services':{'external-provider':'https://untrusted-dependency.invalid/resources'},
        'notifications':[], 'empty':False},'application/json'
