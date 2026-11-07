"""Independent finite complete graph checkpoints, never native scientific commits."""
from copy import deepcopy
import hashlib,json,uuid
from urllib.parse import parse_qs,urlsplit

from .state import ContractError

PROTOCOL='klab.experimental.graph-commits/1'
PREFIX='/experimental/graph-commits/v1/'
KIND='REFERENCE_COMPLETE_FINITE_GRAPH_NOT_NATIVE_SCIENTIFIC_COMMIT'


def graph_hash(nodes,edges):
    document={'nodes':sorted(nodes,key=lambda row:row['id']),
        'edges':sorted(edges,key=lambda row:(row['sourceId'],row['targetId'],row['relationship']))}
    raw=json.dumps(document,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode('utf-8')
    if len(raw)>2*1024*1024:raise ContractError(429,'Finite reference aggregate graph JSON budget exceeded')
    return hashlib.sha256(raw).hexdigest()


class GraphCommits:
    def __init__(self,state):
        self.state=state;self.rows={}

    def release(self,root):
        self.rows.pop(root,None)

    def _graph(self,root,*,initial=False):
        nodes=[{'id':'context','kind':'CONTEXT','properties':{'contextId':root}}];edges=[]
        if not initial:
            observations=sorted((identity,row) for (context,identity),row in self.state.observations.items() if context==root)
            if len(observations)>127:raise ContractError(429,'Complete finite graph supports at most127completed observations')
            for identity,row in observations:
                observation='observation:'+str(identity);activity='reference-activity:'+str(identity)
                nodes.extend([{'id':observation,'kind':'OBSERVATION','properties':{'observationId':identity,
                    'urn':row['urn'],'name':row.get('name'),'observableUrn':row['observable']['urn'],'origin':'FINITE_REFERENCE_NOT_NATIVE_COMMIT'}},
                    {'id':activity,'kind':'RECORDED_ACTIVITY','properties':{'observationId':identity,
                        'outcome':'SUCCESS','origin':'DECLARED_SYNTHETIC_ACTIVITY_NOT_NATIVE_ACTIVITY_ID'}}])
                edges.extend([{'sourceId':'context','targetId':observation,'relationship':'CONTAINS'},
                    {'sourceId':'context','targetId':activity,'relationship':'CONTAINS'},
                    {'sourceId':activity,'targetId':observation,'relationship':'PRODUCED'}])
        return nodes,edges,graph_hash(nodes,edges)

    def _ensure(self,root):
        if root not in self.rows:
            if len(self.rows)>=128:raise ContractError(429,'Finite reference root capacity exhausted')
            nodes,edges,digest=self._graph(root,initial=True)
            initial={'protocol':PROTOCOL,'kind':KIND,'contextId':root,'runtimeId':self.state.service_ids['runtime'],
                'epoch':uuid.uuid4().hex,'revision':0,'digest':digest,'nodes':nodes,'edges':edges,'complete':True}
            self.rows[root]=[initial]
        return self.rows[root]

    def _delta(self,before,after):
        old_nodes={n['id']:n for n in before['nodes']};new_nodes={n['id']:n for n in after['nodes']}
        def edges(snapshot):return {(e['sourceId'],e['targetId'],e['relationship']):e for e in snapshot['edges']}
        old_edges,new_edges=edges(before),edges(after)
        return {'previousRevision':before['revision'],'revision':after['revision'],
            'previousDigest':before['digest'],'digest':after['digest'],
            'removeNodes':sorted(set(old_nodes)-set(new_nodes)),
            'removeEdges':[old_edges[key] for key in sorted(set(old_edges)-set(new_edges))],
            'upsertNodes':[new_nodes[key] for key in sorted(new_nodes) if old_nodes.get(key)!=new_nodes[key]],
            'upsertEdges':[new_edges[key] for key in sorted(set(new_edges)-set(old_edges))]}

    def request(self,method,path,principal,scope,headers,data):
        if self.state.store is not None:raise ContractError(422,'Finite reference graph history is ephemeral only')
        root,_=self.state._context(scope,principal)
        if root!=scope or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Reference graph requires exact owned Runtime root/origin')
        url=urlsplit(path);query=parse_qs(url.query,keep_blank_values=True)
        history=self._ensure(root);head=history[-1]
        if method=='POST' and url.path==PREFIX+'checkpoint' and not query:
            if (not isinstance(data,dict) or set(data)!={'protocol','expectedRevision'} or data['protocol']!=PROTOCOL
                    or type(data['expectedRevision']) is not int or not 0<=data['expectedRevision']<2**53):
                raise ContractError(422,'Exact finite reference protocol/CAS expected revision required')
            if data['expectedRevision']!=head['revision']:raise ContractError(409,'Reference head changed; inspect/reconcile explicitly')
            if head['revision']>=2**53-1:raise ContractError(429,'Finite reference revision budget exhausted')
            nodes,edges,digest=self._graph(root)
            snapshot={**head,'revision':head['revision']+1,'digest':digest,'nodes':nodes,'edges':edges}
            self.rows[root]=[*history,snapshot][-8:]
            return deepcopy(snapshot),'application/json'
        if method!='GET' or query.get('protocol')!=[PROTOCOL] or any(len(v)!=1 for v in query.values()):
            raise ContractError(422,'Exact finite reference read selectors required')
        if url.path==PREFIX+'snapshot':
            if not set(query)<={'protocol','revision'}:raise ContractError(422,'Unknown reference snapshot selector')
            selected=head['revision']
            if 'revision' in query:selected=self._revision(query['revision'][0])
            retained=next((s for s in history if s['revision']==selected),None)
            if retained is None:raise ContractError(410,'Reference snapshot not retained; explicit full resync required')
            return deepcopy(retained),'application/json'
        if url.path==PREFIX+'changes':
            if set(query)!={'protocol','epoch','since','baseDigest'}:raise ContractError(422,'Pinned reference delta selectors required')
            since=self._revision(query['since'][0])
            if query['epoch'][0]!=head['epoch']:raise ContractError(409,'Reference epoch changed; explicit full resync required')
            base=next((s for s in history if s['revision']==since),None)
            if base is None:raise ContractError(410,'Reference delta history gap; explicit full resync required')
            if query['baseDigest'][0]!=base['digest']:raise ContractError(409,'Reference base digest changed; explicit full resync required')
            chain=[self._delta(before,after) for before,after in zip(history,history[1:]) if after['revision']>since]
            result={'protocol':PROTOCOL,'contextId':root,'runtimeId':self.state.service_ids['runtime'],'epoch':head['epoch'],
                'baseRevision':since,'baseDigest':base['digest'],'headRevision':head['revision'],'headDigest':head['digest'],
                'deltas':chain,'complete':True}
            if len(json.dumps(result,separators=(',',':')).encode())>2*1024*1024:
                raise ContractError(429,'Reference delta response budget exhausted; choose explicit full resync')
            return deepcopy(result),'application/json'
        raise ContractError(404,'Unknown finite reference graph operation')

    def _revision(self,value):
        if not value.isascii() or not value.isdecimal() or len(value)>16 or not 0<=int(value)<2**53:
            raise ContractError(422,'Finite exact reference revision required')
        return int(value)

    def admin(self,action,body):
        if len(body)>4096:raise ContractError(413,'Finite graph control budget exceeded')
        try:data=json.loads(body)
        except (ValueError,UnicodeError):raise ContractError(422,'Graph fixture control requires bounded JSON') from None
        if (not isinstance(data,dict) or not isinstance(data.get('contextId'),str)
                or data['contextId'] not in self.state.contexts):
            raise ContractError(422,'Graph fixture control requires one current root')
        root=data['contextId']
        if action=='epoch':
            if set(data)!={'contextId'}:raise ContractError(422,'Exact reference epoch control required')
            self.rows.pop(root,None)
            return True,'application/json'
        expected={'contextId','observationId','name'} if action=='rename' else {'contextId','observationId'}
        if set(data)!=expected or type(data['observationId']) is not int or data['observationId']<=0:
            raise ContractError(422,'Exact graph fixture observation selector required')
        key=(root,data['observationId'])
        if key not in self.state.observations:raise ContractError(404,'Graph fixture observation missing')
        if action=='rename':
            name=data['name']
            if not isinstance(name,str) or not name.strip() or len(name)>128 or any(ord(c)<32 for c in name):
                raise ContractError(422,'Graph fixture display name must be bounded text')
            self.state.observations[key]['name']=name
        else:
            del self.state.observations[key]
        return True,'application/json'
