"""Independent finite graph snapshots, not native parser/compiler/closure authority."""
from copy import deepcopy
import hashlib
import re
import uuid
from urllib.parse import parse_qs,urlsplit

from .state import ContractError

PROTOCOL='klab.experimental.dependency-graphs/1'
PREFIX='/experimental/dependencies/v1/graphs/'


class DependencyGraphs:
    def __init__(self,state):
        self.state=state;self.snapshots={};self.revision=1;self.mode='complete'

    def release(self,root):
        self.snapshots={key:value for key,value in self.snapshots.items() if key[0]!=root}

    def _snapshot(self,root,principal,root_class,root_urn):
        def node(identity,urn,kind,provider,source):
            return {'nodeId':identity,'urn':urn,'knowledgeClass':kind,'providerId':provider,
                'version':'1.0.'+str(self.revision),'sourceRevision':self.revision,'source':source,
                'sourceSha256':hashlib.sha256(source.encode()).hexdigest(),
                'access':{'subject':principal,'contextId':root,'right':'READ','revision':self.revision}}
        a,b,c,d,e=('a'*32,'b'*32,'c'*32,'d'*32,'e'*32)
        nodes=[node(a,'synthetic.model','MODEL',self.state.service_ids['resources'],'literal model uses synthetic.namespace and synthetic:input\n'),
            node(b,'synthetic.namespace','NAMESPACE',self.state.service_ids['resources'],'namespace synthetic.namespace;\n'),
            node(c,'synthetic:input','RESOURCE','external-provider','{"fixture":"synthetic:input","requires":"synthetic:base-input","version":'+str(self.revision)+'}\n'),
            node(d,'synthetic.base','NAMESPACE',self.state.service_ids['resources'],'namespace synthetic.base;\n'),
            node(e,'synthetic:base-input','RESOURCE','external-provider','{"fixture":"synthetic:base-input","version":'+str(self.revision)+'}\n')]
        pairs: tuple[tuple[str,str],...]=((a,b),(a,c),(b,d),(c,e))
        selected_root=a
        if root_class=='RESOURCE':
            nodes=[nodes[2],nodes[4]];pairs=((c,e),);selected_root=c
        if self.state.scenario in ('annotated-input','execution-explanation'):
            from .input_provenance import graph_nodes
            nodes, pairs, selected_root = graph_nodes(self.state, root, principal, node)
        edges=[{'sourceId':source,'targetId':target,'kind':'DEPENDS_ON'} for source,target in pairs]
        if self.mode=='cycle':edges.append({'sourceId':nodes[-1]['nodeId'],'targetId':selected_root,'kind':'DEPENDS_ON'})
        if self.mode=='missing-endpoint':edges[-1]['targetId']='f'*32
        return {'protocol':PROTOCOL,'contextId':root,'resourcesServiceId':self.state.service_ids['resources'],
            'requestedUrn':root_urn,'requestedClass':root_class,'snapshotId':uuid.uuid4().hex,
            'revision':self.revision,'rootNodeId':selected_root,'nodeCount':len(nodes),'edgeCount':len(edges),
            'nodes':nodes,'edges':edges,'providers':{self.state.service_ids['resources']:'https://fixture.invalid/resources',
                'external-provider':'https://untrusted-graph-provider.invalid/resources'},'complete':True}

    def request(self,method,original_path,principal,scope,headers):
        if self.state.store is not None:raise ContractError(422,'Finite dependency graph snapshots are ephemeral only')
        root,_=self.state._context(scope,principal)
        if root!=scope or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Graph inspection requires exact owned Runtime root and origin')
        if principal!='alice':raise ContractError(403,'Finite graph fixture restricted to Alice')
        url=urlsplit(original_path);query=parse_qs(url.query,keep_blank_values=True)
        roots={PREFIX+'MODEL/synthetic.model':('MODEL','synthetic.model'),PREFIX+'RESOURCE/synthetic:input':('RESOURCE','synthetic:input')}
        if self.state.scenario in ('annotated-input','execution-explanation'):
            from .authoring import MODEL
            roots = {PREFIX+'MODEL/'+MODEL:('MODEL', MODEL)}
        if method!='GET' or url.path not in roots:
            raise ContractError(404,'Only declared read-only dependency graph root admitted')
        root_class,root_urn=roots[url.path]
        if (set(query) not in ({'protocol','pageSize'},{'protocol','pageSize','snapshot','cursor'})
                or any(len(v)!=1 for v in query.values()) or query.get('protocol')!=[PROTOCOL]):
            raise ContractError(422,'Exact dependency graph protocol/page selectors required')
        size=query['pageSize'][0]
        if not size.isascii() or not size.isdecimal() or not 1<=int(size)<=32:
            raise ContractError(422,'Graph page size1..32required')
        size=int(size)
        if 'snapshot' in query:
            identity=query['snapshot'][0];cursor=query['cursor'][0]
            if re.fullmatch('[0-9a-f]{32}',identity) is None or re.fullmatch('[0-9a-f]{32}',cursor) is None:
                raise ContractError(422,'Opaque graph snapshot/cursor required')
            stored=self.snapshots.get((root,identity))
            if stored is None:raise ContractError(410,'Dependency graph snapshot not retained')
            snapshot,cursors=stored
            if snapshot['requestedUrn']!=root_urn or snapshot['requestedClass']!=root_class:
                raise ContractError(422,'Cursor does not belong to requested root asset/class')
            if snapshot['revision']!=self.revision:
                raise ContractError(409,'Source graph changed; explicitly inspect afresh, no mixed pages')
            if cursor not in cursors or cursors[cursor][1]!=size:
                raise ContractError(422,'Cursor does not belong to exact snapshot/page size')
            offset=cursors[cursor][0]
        else:
            if sum(key[0]==root for key in self.snapshots)>=8 or len(self.snapshots)>=128:
                raise ContractError(429,'Owned finite graph snapshot capacity exhausted')
            snapshot=self._snapshot(root,principal,root_class,root_urn);cursors={};offset=0
            self.snapshots[(root,snapshot['snapshotId'])]=(snapshot,cursors)
        next_offset=offset+size;cursor=None
        if next_offset<snapshot['nodeCount']:
            if len(cursors)>=64:raise ContractError(429,'Finite graph cursor capacity exhausted')
            cursor=uuid.uuid4().hex;cursors[cursor]=(next_offset,size)
        return {**deepcopy(snapshot),'nodes':deepcopy(snapshot['nodes'][offset:next_offset]),
            'edges':deepcopy(snapshot['edges']) if offset==0 else [],'nextCursor':cursor},'application/json'
