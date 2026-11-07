"""Two literal namespace snapshots, not a semantic compiler or native admission.

Independent source-hash/revision/diagnostic protocol oracle; never stores source,
publishes assets or computes observations. Knowledge advance is test-control only.
"""
import hashlib

from .state import ContractError

URN = 'synthetic.validation'
SOURCE = 'namespace synthetic.validation;\n'
BAD_SOURCE = 'namespace synthetic.validation;\nmodel synthetic:Unknown;\n'
CLASS = 'org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl'


def document(state, source):
    if source not in (SOURCE, BAD_SOURCE):
        raise ContractError(422, 'Only two declared namespace fixtures; no local parser')
    return {'@CLASS':CLASS, 'urn':URN, 'sourceCode':source,'serviceId':state.service_ids['resources'],
        'statements':[], 'notifications':[]}


def capabilities(state):
    return {'knowledgeRevision':state.validation_revision,'semanticSessionId':'synthetic-validation-session',
        'semanticContentFingerprint':'synthetic-content-'+str(state.validation_revision)}


def respond(state, service, method, route, principal, data, body):
    if service == 'resources' and method == 'POST' and route == '/parseAsset/NAMESPACE':
        try: source = body.decode('utf-8')
        except UnicodeError: raise ContractError(422, 'Fixture source requires UTF-8') from None
        return document(state,source),'application/json'
    if service != 'reasoner' or method != 'POST' or route != '/api/v1/validate/document': return None
    if principal != 'alice': raise ContractError(403, 'Finite validation fixture restricted to Alice')
    if not isinstance(data,dict) or set(data) != {'namespace','documentVersion','knowledgeRevision',
            'executableModels','classifierPreparableDependencies','executionContext','preparableMemberQualityDependencies'}:
        raise ContractError(422,'Exact source-only namespace validation request required')
    parsed = data['namespace']
    source = parsed.get('sourceCode') if isinstance(parsed,dict) else None
    if (not isinstance(source,str) or parsed != document(state, source) or data['executableModels'] is not False
            or data['classifierPreparableDependencies'] is not False or data['executionContext'] is not None
            or data['preparableMemberQualityDependencies'] != [] or type(data['knowledgeRevision']) is not int
            or not isinstance(data['documentVersion'],str) or not data['documentVersion'].strip()):
        raise ContractError(422,'No executable admission, forged document or unversioned validation')
    stale = data['knowledgeRevision'] != state.validation_revision
    return {'status':'STALE_KNOWLEDGE' if stale else 'COMPLETE','documentUrn':URN,
        'documentVersion':data['documentVersion'],'sourceHash':hashlib.sha256(source.encode()).hexdigest(),
        **capabilities(state),'executableModels':False,'classifierPreparableDependencies':False,
        'executionContext':None,'preparableMemberQualityDependencies':[],
        'notifications':[] if stale or source == SOURCE else [{'level':'Error','message':'Declared synthetic unknown semantic',
            'lexicalContext':{'documentUrn':URN,'offsetInDocument':38,'length':17}}],
        'reason':'Independent finite reference, not native semantic admission'},'application/json'
