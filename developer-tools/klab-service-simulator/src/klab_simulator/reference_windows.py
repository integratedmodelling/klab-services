"""Finite selected state/window grants; no provider time semantics or resampling."""
from copy import deepcopy
import json,math,re,secrets,time
from urllib.parse import parse_qs,urlsplit

from .read_plans import PROTOCOL as PLAN_PROTOCOL,REVISION_KIND,digest
from .state import ContractError

PROTOCOL='klab.experimental.read-plan-selections/1'
PREFIX='/experimental/read-plan-selections/v1/'
KIND='DECLARED_FINITE_STATE_WINDOW_NOT_NATIVE_TIME_OR_COMMIT'


class ReadPlanSelections:
    def __init__(self,state):self.state=state

    def frames(self,root,observation):
        height,width=self.state.grid['shape'];numbers:list[float | None]=[];mask=[]
        if height*width!=len(self.state.values) or len(self.state.values)>4096:
            raise ContractError(422,'Finite source state shape mismatch')
        for value in self.state.values:
            if value is None or value=='NaN':numbers.append(None);mask.append(False)
            elif type(value) in (int,float) and abs(value)<=2**53 and math.isfinite(value):
                numbers.append(float(value));mask.append(True)
            else:raise ContractError(422,'Unsupported finite source state value')
        alternate=[None if index%11==0 else float(index%9-4) for index in range(len(numbers))]
        validity=[value is not None for value in alternate]
        west,south,east,north=self.state.grid['bounds']
        transform=[(east-west)/width,0,west,0,-(north-south)/height,north]
        result={}
        for identity,label,instant,values,valid in (
                ('baseline','Declared baseline','2020-01-01T00:00:00Z',numbers,mask),
                ('alternate','Declared alternate','2020-01-02T00:00:00Z',alternate,validity)):
            metadata={'id':identity,'label':label,'instant':instant,'runtimeId':self.state.service_ids['runtime'],
                'contextId':root,'observationId':observation,'epoch':self.state.read_plans.epoch,
                'sourceRevision':digest(values,valid),'shape':[height,width],'crs':self.state.grid['crs'],
                'transform':transform[:],'units':self.state.grid['unit']}
            result[identity]=(metadata,values,valid)
        return result

    def request(self,method,path,principal,scope,headers,data):
        if self.state.store is not None:raise ContractError(422,'Selected reference grants are ephemeral only')
        root,_=self.state._context(scope,principal)
        if root!=scope or headers.get('klab-service')!=self.state.service_ids['runtime']:
            raise ContractError(403,'Selected plans require exact owned root/Runtime')
        self.state.read_plans.cleanup()
        url=urlsplit(path);suffix=url.path[len(PREFIX):]
        if method=='GET':
            match=re.fullmatch(r'observations/([1-9][0-9]{0,15})/states',suffix)
            if match is None or parse_qs(url.query,keep_blank_values=True)!={'protocol':[PROTOCOL]}:
                raise ContractError(422,'Exact finite state catalog selectors required')
            observation=int(match[1])
        elif method=='POST' and suffix=='open' and not url.query:
            fields={'protocol','observationId','window','stateId','sourceRevision','epoch','shardCells','leaseSeconds','maxCells'}
            if (not isinstance(data,dict) or set(data)!=fields or data['protocol']!=PROTOCOL
                    or type(data['observationId']) is not int or not 0<data['observationId']<2**53
                    or type(data['shardCells']) is not int or not 1<=data['shardCells']<=128
                    or type(data['leaseSeconds']) is not int or not 1<=data['leaseSeconds']<=60
                    or type(data['maxCells']) is not int or not 1<=data['maxCells']<=4096
                    or not isinstance(data['stateId'],str)):
                raise ContractError(422,'Exact bounded finite state/window grant request required')
            observation=data['observationId']
        else:raise ContractError(404,'Unsupported selected read-plan operation')
        if (root,observation) not in self.state.observations:raise ContractError(404,'Completed owned observation required')
        frames=self.frames(root,observation)
        if method=='GET':return {'protocol':PROTOCOL,'kind':KIND,'states':[frame[0] for frame in frames.values()]},'application/json'
        if data['stateId'] not in frames:raise ContractError(422,'Unknown finite temporal state; no guessed time selection')
        metadata,source,source_valid=frames[data['stateId']]
        if data['epoch']!=metadata['epoch'] or data['sourceRevision']!=metadata['sourceRevision']:
            raise ContractError(409,'Finite source state changed; explicitly relist/reselect, no automatic replan')
        window=data['window']
        if (not isinstance(window,dict) or set(window)!={'rowStart','rowStop','columnStart','columnStop'}
                or any(type(value) is not int for value in window.values())):
            raise ContractError(422,'Exact half-open integer reference window required')
        r0,r1,c0,c1=(window[key] for key in ('rowStart','rowStop','columnStart','columnStop'))
        height,width=metadata['shape']
        if not 0<=r0<r1<=height or not 0<=c0<c1<=width or (r1-r0)*(c1-c0)>data['maxCells']:
            raise ContractError(422,'Window outside exact source state or selected cell budget')
        if len(self.state.read_plans.rows)>=16:raise ContractError(429,'Shared finite read-grant capacity exceeded')
        indices=[row*width+column for row in range(r0,r1) for column in range(c0,c1)]
        values=[source[index] for index in indices];valid=[source_valid[index] for index in indices]
        identity=secrets.token_hex(16);epoch=self.state.read_plans.epoch;revision=digest(values,valid)
        blocks=[];specs=[]
        for index,start in enumerate(range(0,len(values),data['shardCells'])):
            numbers=values[start:start+data['shardCells']];masks=valid[start:start+data['shardCells']];checksum=digest(numbers,masks)
            specs.append({'index':index,'start':start,'count':len(numbers),'sha256':checksum})
            blocks.append({'protocol':PLAN_PROTOCOL,'plan_id':identity,'epoch':epoch,'revision':revision,
                'index':index,'start':start,'values':numbers,'valid':masks,'sha256':checksum})
        sx,_,west,_,sy,north=metadata['transform']
        plan={'protocol':PLAN_PROTOCOL,'plan_id':identity,'epoch':epoch,'revision':revision,'revision_kind':REVISION_KIND,
            'observation_id':observation,'scope':root,'runtime_id':self.state.service_ids['runtime'],'dtype':'float64',
            'shape':[r1-r0,c1-c0],'crs':metadata['crs'],'transform':[sx,0,west+c0*sx,0,sy,north+r0*sy],
            'order':'ROW_MAJOR_NORTH_TO_SOUTH','units':metadata['units'],'shards':specs,'fixture_origin':self.state.origin}
        result={'protocol':PROTOCOL,'kind':KIND,'state':deepcopy(metadata),'window':deepcopy(window),'plan':plan}
        if len(json.dumps(result,allow_nan=False).encode())>65536:
            raise ContractError(413,'Selected manifest exceeds response budget; no grant allocated')
        if identity in self.state.read_plans.rows:raise ContractError(409,'Read grant identity collision; no overwrite')
        self.state.read_plans.rows[identity]={'scope':root,'expires':time.monotonic()+data['leaseSeconds'],'blocks':blocks}
        return result,'application/json'
