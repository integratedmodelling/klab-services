"""Finite native-shaped commit cache, not durable/contiguous/scientific commits."""
from copy import deepcopy
from urllib.parse import parse_qs, urlsplit

from .state import ContractError


class CommitReports:
    def __init__(self, state):
        self.state = state
        self.rows = {}

    def release(self, root):
        self.rows = {key:row for key,row in self.rows.items() if key[0] != root}

    def record(self, root, observation):
        # Finite cache cap mirrors cache missingness, not deployed Java retention.
        identity = 1000 + observation['id']
        self.rows[(root,identity)] = {'id':identity, 'timestamp':100 + observation['id'],
            'owner':self.state.contexts[root]['owner'], 'addedAssets':[observation['id']],
            'addedObservations':[observation['id']], 'addedCohorts':[], 'modifiedAssets':[], 'deletedAssets':[],
            'addedLinks':[{'first':999, 'second':observation['id'], 'third':'CONTAINS'}],
            'deletedLinks':[], 'observationCessations':[], 'cohortMembershipChanges':[]}
        observation['metadata']['delegate']['im:commit-id'] = identity
        while len(self.rows) > 128:
            del self.rows[next(iter(self.rows))]

    def request(self, method, path, principal, scope, headers):
        if self.state.store is not None:
            raise ContractError(422, 'Finite native-shaped commit cache is ephemeral only')
        root,_ = self.state._context(scope,principal)
        if root != scope or headers.get('klab-service') != self.state.service_ids['runtime']:
            raise ContractError(403, 'Commit read requires exact owned root and Runtime origin')
        url = urlsplit(path); query = parse_qs(url.query,keep_blank_values=True)
        if method != 'GET' or url.path != '/api/v1/commit':
            raise ContractError(404, 'Only source-defined native read route supported')
        if set(query) != {'id'} or len(query['id']) != 1:
            raise ContractError(422, 'One explicit commit identifier required')
        value = query['id'][0]
        if not value.isascii() or not value.isdecimal() or len(value)>19 or not 0<int(value)<=2**63-1:
            raise ContractError(422, 'Positive Java-long commit identifier required')
        # Native getIfPresent returns null. Do not invent an empty change report.
        return deepcopy(self.rows.get((root,int(value)))), 'application/json'
