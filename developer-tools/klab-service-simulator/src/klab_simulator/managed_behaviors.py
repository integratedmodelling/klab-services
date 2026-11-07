"""Literal finite recipe admission and synthetic private sessions; no actor interpreter."""
from copy import deepcopy
import hashlib,re,secrets
from urllib.parse import parse_qs,urlsplit

from .state import ContractError

PROTOCOL='klab.experimental.managed-behaviors/1'
PREFIX='/experimental/managed-behaviors/v1/'
KIND='DECLARED_FINITE_SOURCE_STATE_MACHINE_NOT_KACTORS_EXECUTION'
SOURCES={recipe:f'''script simulator.managed.{recipe}
    "Finite declared {recipe} fixture, not native execution."
    version 1.0

action main:
    {instruction}
''' for recipe,instruction in (('finish','return 0'),('fail','fail "declared failure"'))}
TERMINAL=('FINISHED','FAILED','STOPPED')


class ManagedBehaviors:
    def __init__(self,state):
        self.state=state;self.epoch=secrets.token_hex(16);self.rows:dict[str,dict]={}

    def release(self,root):
        for key,row in list(self.rows.items()):
            if row['reference']['contextId']==root:
                self._dispose(row);del self.rows[key]

    def _dispose(self,row):
        identity=row['privateSessionId'];session=self.state.sessions.get(identity)
        if session is not None:
            if session.get('managedBehavior')!=row['reference']['agentId']:
                raise ContractError(409,'Private session ownership marker changed; never release unrelated sessions')
            if any(root.split('.')[0]==identity for root in self.state.contexts):
                raise ContractError(409,'Private session gained independent contexts; no disposal or orphaning inferred')
            del self.state.sessions[identity]
        row['privateSessionDisposed']=True

    def _admit(self,data):
        try:raw=data['source'].encode('utf-8') if isinstance(data.get('source'),str) else None
        except UnicodeError:raise ContractError(422,'Finite source must be valid UTF8') from None
        if raw is None or len(raw)>4096:
            raise ContractError(422,'Bounded exact literal source required; arbitrary source is not executable')
        recipe=next((key for key,value in SOURCES.items() if data['source']==value),None)
        digest=hashlib.sha256(raw).hexdigest()
        if recipe is None or data.get('sourceSha256')!=digest:
            raise ContractError(422,'Unknown source recipe/hash; not a k.Actors parser or compiler')
        return {'protocol':PROTOCOL,'kind':KIND,'sourceSha256':digest,'sourceUrn':'simulator.managed.'+recipe,'recipe':recipe}

    def request(self,method,path,principal,scope,headers,data):
        if self.state.store is not None:raise ContractError(422,'Managed behavior private-session proposal is ephemeral only')
        root,_=self.state._context(scope,principal)
        if root!=scope or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Exact originating root/Runtime required')
        url=urlsplit(path);pieces=url.path[len(PREFIX):].split('/')
        if method=='POST':
            if not isinstance(data,dict) or data.get('protocol')!=PROTOCOL or url.query:
                raise ContractError(422,'Exact finite managed behavior protocol required')
            if pieces in (['validate'],['create']):
                fields={'protocol','source','sourceSha256'}|({'recipe'} if pieces==['create'] else set())
                if set(data)!=fields:raise ContractError(422,'Exact finite source admission fields required')
                admission=self._admit(data)
                if pieces==['validate']:return admission,'application/json'
                if data['recipe']!=admission['recipe']:raise ContractError(409,'Source recipe changed; revalidate explicitly')
                if len(self.rows)>=16 or len(self.state.sessions)>=16:
                    raise ContractError(429,'Finite agent/private-session capacity exceeded')
                identity=secrets.token_hex(16);private=secrets.token_hex(16)
                if identity in self.rows or private in self.state.sessions:raise ContractError(409,'Agent/private session identity collision; no overwrite')
                reference={**admission,'agentId':identity,'epoch':self.epoch,'runtimeId':self.state.service_ids['runtime'],
                    'contextId':root,'privateSessionId':private}
                created={'reference':reference,'revision':1,'state':'READY','privateSessionId':private,
                    'privateSessionDisposed':False,'outcome':None}
                self.state.sessions[private]={'owner':principal,'managedBehavior':identity}
                self.rows[identity]=created
                return deepcopy(created),'application/json'
        if not pieces or re.fullmatch('[0-9a-f]{32}',pieces[0]) is None:
            raise ContractError(422,'Exact managed behavior identity required')
        if method=='GET':
            params=parse_qs(url.query,keep_blank_values=True)
            if set(params)!={'protocol','epoch'} or params['protocol']!=[PROTOCOL] or len(params['epoch'])!=1:
                raise ContractError(422,'Exact protocol/incarnation selectors required')
            epoch=params['epoch'][0]
        elif method=='POST':
            if set(data)!={'protocol','epoch','expectedRevision'} or type(data['expectedRevision']) is not int or not 1<=data['expectedRevision']<=16:
                raise ContractError(422,'Explicit bounded observed revision required')
            epoch=data['epoch']
        else:raise ContractError(404,'Unsupported finite behavior operation')
        row=self.rows.get(pieces[0])
        if row is None or epoch!=self.epoch:raise ContractError(410,'Agent lost/purged/old incarnation; never recreate or replay')
        if row['reference']['contextId']!=root:raise ContractError(403,'Managed behavior belongs to another root')
        if not row['privateSessionDisposed'] and row['privateSessionId'] not in self.state.sessions:
            raise ContractError(410,'Private session disappeared outside managed lifecycle; no cleanup success inferred')
        if method=='GET' and len(pieces)==1:
            if row['state']=='RUNNING':
                row['state']='FINISHED' if row['reference']['recipe']=='finish' else 'FAILED'
                row['outcome']='DECLARED_SUCCESS' if row['state']=='FINISHED' else 'DECLARED_FAILURE'
                row['revision']+=1
            elif row['state']=='STOPPING':row.update(state='STOPPED',outcome='DECLARED_STOP',revision=row['revision']+1)
            elif row['state'] in TERMINAL and not row['privateSessionDisposed']:
                self._dispose(row);row['revision']+=1
            return deepcopy(row),'application/json'
        if method!='POST' or len(pieces)!=2 or pieces[1] not in ('start','stop','purge'):
            raise ContractError(404,'Unsupported managed lifecycle command')
        if data['expectedRevision']!=row['revision']:raise ContractError(409,'Revision changed; explicitly inspect before another command')
        command=pieces[1]
        if command=='purge':
            if row['state'] not in TERMINAL or not row['privateSessionDisposed']:
                raise ContractError(409,'Independent terminal/private-session disposal evidence required before purge')
            del self.rows[pieces[0]]
            return {'purged':True,'reference':row['reference']},'application/json'
        if command=='start' and row['state']=='READY':row['state']='RUNNING'
        elif command=='stop' and row['state'] in ('READY','RUNNING'):row['state']='STOPPING'
        else:raise ContractError(409,'Single-use finite lifecycle; no repeated start/stop')
        row['revision']+=1
        return deepcopy(row),'application/json'
