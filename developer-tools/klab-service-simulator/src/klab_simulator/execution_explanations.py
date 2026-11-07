"""Finite selected input/model/component/effect declarations, not a scientific engine."""
from copy import deepcopy
import hashlib,json,uuid
from urllib.parse import parse_qs,urlsplit

from .authoring import MODEL,RESOURCE
from .input_provenance import evidence
from .state import ContractError

PROTOCOL='klab.experimental.execution-explanations/1'
PREFIX='/experimental/execution-explanations/v1/observations/'
KIND='COMPLETE_FINITE_REFERENCE_NOT_NATIVE_SCIENTIFIC_LINEAGE'


def seal(value):
    body={key:value[key] for key in value if key!='digest'}
    raw=json.dumps(body,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()
    if len(raw)>1024*1024:raise ContractError(429,'Finite explanation byte budget exceeded')
    return {**body,'digest':hashlib.sha256(raw).hexdigest()}


class ExecutionExplanations:
    def __init__(self,state):self.state=state;self.rows={}

    def release(self,root):self.rows={key:row for key,row in self.rows.items() if key[0]!=root}

    def record(self,root,observation):
        plans=[job['result'] for (service,_),job in self.state.jobs.items() if service=='resolver'
            and job['scope']==root and job['status']=='FINISHED']
        if not plans:return  # Observation completion alone is not proof of explicit selected plan.
        plan=plans[-1]
        if plan.get('computation',[{}])[0].get('computation')!=[{'urn':MODEL}]:return
        component=plan.get('referenceComponentSelection')
        if component!={'componentId':'simulator.recorded-crop','componentVersion':'1.0.0',
                'componentProviderId':self.state.service_ids['runtime'],'prototype':'simulator.recorded400'}:return
        if len(self.rows)>=128:raise ContractError(429,'Finite completed explanation capacity exhausted')
        binding,manifest,model_source,_=evidence(self.state,root,self.state.contexts[root]['owner'])
        requirements=plan.get('requirements',{}).get('results',[])
        for urn,source in ((MODEL,model_source),(RESOURCE,manifest)):
            matches=[row for row in requirements if row.get('resourceUrn')==urn]
            if (len(matches)!=1 or matches[0].get('resourceVersion')!='1.0.1'
                    or matches[0].get('sourceSha256')!=hashlib.sha256(source.encode()).hexdigest()
                    or matches[0].get('serviceId')!=self.state.service_ids['resources']):return
        identity=observation['id'];selection='selection:'+str(identity);planned='plan:'+str(identity)
        value={'protocol':PROTOCOL,'kind':KIND,'contextId':root,'runtimeId':self.state.service_ids['runtime'],
            'observationId':identity,'epoch':uuid.uuid4().hex,'revision':0,'complete':True,
            'inputs':[{'urn':RESOURCE,'providerId':self.state.service_ids['resources'],'version':'1.0.1',
                'sourceSha256':hashlib.sha256(manifest.encode()).hexdigest(),'binarySha256':binding['sourceSha256']}],
            'selections':[{'id':selection,'modelUrn':MODEL,'modelVersion':'1.0.1',
                'modelSourceSha256':hashlib.sha256(model_source.encode()).hexdigest(),
                'providerId':self.state.service_ids['resources'],**component,'inputUrns':[RESOURCE]}],
            'planned':[{'id':planned,'selectionId':selection,'targetObservationId':identity,'effect':'CREATE'}],
            'recorded':[{'id':'reference-activity:'+str(identity),'plannedId':planned,'targetObservationId':identity,
                'outcome':'SUCCESS','effect':'CREATE'}],'semanticUpdates':[]}
        self.rows[(root,identity)]=seal(value)

    def request(self,method,path,principal,scope,headers):
        if self.state.store is not None:raise ContractError(422,'Finite explanation history is ephemeral only')
        root,_=self.state._context(scope,principal)
        if scope!=root or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Execution explanation requires exact owned root/Runtime')
        url=urlsplit(path)
        if method!='GET' or not url.path.startswith(PREFIX) or parse_qs(url.query,keep_blank_values=True)!={'protocol':[PROTOCOL]}:
            raise ContractError(422,'Exact advertised finite explanation read selectors required')
        value=url.path[len(PREFIX):]
        if not value.isascii() or not value.isdecimal() or len(value)>19 or not 0<int(value)<=2**63-1:
            raise ContractError(422,'Exact positive observation identity required')
        key=(root,int(value))
        if key not in self.state.observations:raise ContractError(404,'Observation missing or inaccessible')
        if key not in self.rows:raise ContractError(422,'No explicit completed selected plan/source explanation recorded')
        return deepcopy(self.rows[key]),'application/json'

    def semantic_update(self,body):
        if len(body)>4096:raise ContractError(413,'Finite semantic control budget exceeded')
        try:data=json.loads(body)
        except (ValueError,UnicodeError):raise ContractError(422,'Finite semantic control requires JSON') from None
        if (not isinstance(data,dict) or set(data)!={'contextId','observationId','expectedRevision','afterObservable'}
                or not isinstance(data['contextId'],str) or type(data['observationId']) is not int
                or type(data['expectedRevision']) is not int or not isinstance(data['afterObservable'],str)
                or data['afterObservable']!='geography:Elevation in cm'):
            raise ContractError(422,'Only one declared finite semantic-update fixture is supported')
        key=(data['contextId'],data['observationId']);value=self.rows.get(key)
        if value is None:raise ContractError(404,'Finite explanation observation missing')
        if value['revision']!=data['expectedRevision']:raise ContractError(409,'Finite semantic-update reference revision changed')
        if value['revision']>=1:raise ContractError(422,'Finite semantic update already declared; no engine replay')
        updated=deepcopy(value);revision=value['revision']+1
        planned='semantic-plan:'+str(revision);recorded='reference-semantic-activity:'+str(revision)
        updated['planned'].append({'id':planned,'selectionId':value['selections'][0]['id'],
            'targetObservationId':key[1],'effect':'SEMANTIC_UPDATE'})
        updated['recorded'].append({'id':recorded,'plannedId':planned,'targetObservationId':key[1],
            'outcome':'SUCCESS','effect':'SEMANTIC_UPDATE'})
        updated['semanticUpdates'].append({'recordedId':recorded,'previousRevision':value['revision'],'revision':revision,
            'beforeObservable':'geography:Elevation in m','afterObservable':data['afterObservable']})
        updated['revision']=revision;self.rows[key]=seal(updated)
        self.state.observations[key]['metadata']['delegate']['referenceSemanticUpdate']={
            'beforeObservable':'geography:Elevation in m','afterObservable':data['afterObservable'],
            'revision':revision,'origin':'DECLARED_REFERENCE_LABEL_ASSOCIATION_NOT_NUMERIC_MEDIATION'}
        # Declared semantic-label association only: native observable and values unchanged.
        return True,'application/json'
