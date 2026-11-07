"""Independent finite conditional publication, not native source/compiler authority."""
from copy import deepcopy
import hashlib
import re
import uuid
from urllib.parse import parse_qs,urlsplit

from .state import ContractError

PROTOCOL='klab.experimental.accepted-documents/1'
PREFIX='/experimental/accepted-documents/v1/'
NAMES=tuple('synthetic.document'+str(i) for i in range(8))


class AcceptedSources:
    def __init__(self,state):
        self.state=state;self.rows={};self.publications={}
        self.session=uuid.uuid4().hex

    def knowledge(self):
        return {'serviceId':self.state.service_ids['reasoner'],'worldviewId':self.state.worldview_id,
            'revision':self.state.validation_revision,'sessionId':self.session,
            'contentFingerprint':'synthetic-authoring-content-'+str(self.state.validation_revision)}

    def release(self,root):
        self.rows={key:rows for key,rows in self.rows.items() if key[0]!=root}
        self.publications={key:row for key,row in self.publications.items() if key[0]!=root}

    def _envelope(self,root,name):
        return {'protocol':PROTOCOL,'contextId':root,'resourcesServiceId':self.state.service_ids['resources'],'name':name}

    def _condition(self,data,current):
        if (not isinstance(data,dict) or set(data)!={'protocol','expectedRevision','expectedKnowledgeRevision'}
                or data['protocol']!=PROTOCOL or type(data['expectedRevision']) is not int
                or type(data['expectedKnowledgeRevision']) is not int):
            raise ContractError(422,'Exact source and knowledge conditions required')
        if data['expectedRevision']!=current['revision'] or data['expectedKnowledgeRevision']!=self.state.validation_revision:
            raise ContractError(409,'Source or knowledge changed; explicitly reread/revalidate')

    def request(self,method,original_path,principal,scope,headers,data):
        if self.state.store is not None: raise ContractError(422,'Conditional source reference is ephemeral only')
        root,_=self.state._context(scope,principal)
        if root!=scope or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Conditional documents require exact owned Runtime root')
        url=urlsplit(original_path);route=url.path
        query=parse_qs(url.query,keep_blank_values=True)
        protocol=query.get('protocol') if method=='GET' else [data.get('protocol')] if isinstance(data,dict) else []
        if protocol!=[PROTOCOL] or any(len(values)!=1 for values in query.values()):
            raise ContractError(422,'Exact experimental protocol required')
        if method!='GET' and query: raise ContractError(422,'Mutations do not accept query overrides')
        if method=='GET' and route==PREFIX+'knowledge':
            if set(query)!={'protocol'}: raise ContractError(422,'Unknown knowledge query')
            return self.knowledge(),'application/json'
        match=re.fullmatch(re.escape(PREFIX)+r'(documents|publications)/([A-Za-z0-9_.]+)(?:/(validate|publish))?',route)
        if match is None: raise ContractError(404,'Unknown conditional source route')
        kind,name,action=match.groups()
        if name not in NAMES: raise ContractError(422,'Only eight declared namespace identities admitted')
        key=(root,name);history=self.rows.get(key,[])
        if kind=='publications':
            if method!='GET' or action is not None or set(query)!={'protocol'}: raise ContractError(422,'Read-only publication lookup required')
            published=self.publications.get(key)
            if published is None: raise ContractError(404,'Namespace is not published')
            return deepcopy(published),'application/json'
        if method=='GET' and action is None:
            if not set(query)<={'protocol','revision'}: raise ContractError(422,'Unknown document query')
            if not history: raise ContractError(404,'Document absent')
            revision=query.get('revision',[str(history[-1]['revision'])])[0]
            if not revision.isascii() or not revision.isdecimal() or not 1<=int(revision)<2**53:
                raise ContractError(422,'Explicit bounded retained revision required')
            row=next((row for row in history if row['revision']==int(revision)),None)
            if row is None: raise ContractError(410,'Source revision not retained')
            return deepcopy(row),'application/json'
        if method=='PUT' and action is None:
            if (not isinstance(data,dict) or set(data)!={'protocol','expectedRevision','source','sourceSha256'}
                    or type(data['expectedRevision']) is not int or not 0<=data['expectedRevision']<2**53
                    or not isinstance(data['source'],str)):
                raise ContractError(422,'Exact conditional update required')
            try: raw=data['source'].encode('utf-8')
            except UnicodeError: raise ContractError(422,'Source must be UTF-8') from None
            if (not raw or not data['source'].strip() or len(raw)>16384
                    or hashlib.sha256(raw).hexdigest()!=data['sourceSha256']):
                raise ContractError(422,'Bounded exact source hash required')
            current=history[-1]['revision'] if history else 0
            if data['expectedRevision']!=current: raise ContractError(409,'Source compare-and-swap conflict; do not replay')
            if current>=2**53-1 or not history and len(self.rows)>=256:
                raise ContractError(429,'Conditional source storage budget exhausted')
            row={**self._envelope(root,name),'revision':current+1,'source':data['source'],
                'sourceSha256':data['sourceSha256'],'validation':None}
            self.rows[key]=[*history,row][-8:]
            return deepcopy(row),'application/json'
        if not history: raise ContractError(404,'Document absent')
        current=history[-1]
        if method!='POST' or action not in ('validate','publish'): raise ContractError(404,'Unsupported conditional source action')
        self._condition(data,current)
        if action=='validate':
            valid=current['source']==f'namespace {name};\n'
            current['validation']={'revision':current['revision'],'sourceSha256':current['sourceSha256'],
                'knowledge':self.knowledge(),'status':'VALID' if valid else 'INVALID',
                'diagnostics':[] if valid else [{'level':'Error','message':'Source is outside the declared finite namespace fixture'}]}
            return deepcopy(current),'application/json'
        validation=current['validation']
        if (validation is None or validation['status']!='VALID' or validation['knowledge']!=self.knowledge()
                or validation['revision']!=current['revision'] or validation['sourceSha256']!=current['sourceSha256']):
            raise ContractError(409,'Publication requires exact current server-owned successful validation')
        if key in self.publications and self.publications[key]['documentRevision']==current['revision']:
            raise ContractError(409,'Revision already published; inspect existing snapshot, do not replay')
        result={**self._envelope(root,name),'documentRevision':current['revision'],'source':current['source'],
            'sourceSha256':current['sourceSha256'],'publicationId':uuid.uuid4().hex,'knowledge':self.knowledge()}
        self.publications[key]=deepcopy(result)
        return result,'application/json'
