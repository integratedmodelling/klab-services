// Inspect the production UI against the four running services without restarting their JVMs.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../klab.core.services/target/generated-resources/webui/static');
const ports={reasoner:8091,resources:8092,resolver:8093,runtime:8094};
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.json':'application/json'};
http.createServer((req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1');
 const [,service,...parts]=url.pathname.split('/');
 if(!ports[service]){res.writeHead(302,{location:'/resources/'});res.end();return;}
 const relative=parts.join('/');
 // Visual preview only: the identity provider does not register port 5190.
 // Keep the real gates but skip automatic SSO redirects on this preview origin.
 if(relative==='public/ui/config') {
  fetch('http://127.0.0.1:'+ports[service]+req.url).then(r=>r.json()).then(config=>{
   delete config.authentication.url;
   res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify(config));
  }).catch(()=>{res.writeHead(502);res.end();});return;
 }
 if(!relative||relative.startsWith('ui/')||relative.startsWith('apps/')){
  res.writeHead(200,{'content-type':'text/html','cache-control':'no-store'});
  res.end(fs.readFileSync(path.join(root,'index.html'),'utf8').replaceAll('__KLAB_BASE_HREF__','/'+service+'/'));return;
 }
 if(relative.startsWith('assets/')||['favicon.svg','klab-logo.svg'].includes(relative)){
  const file=path.resolve(root,relative);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.writeHead(200,{'content-type':mime[path.extname(file)]||'application/octet-stream','cache-control':'no-store'});
  fs.createReadStream(file).pipe(res);return;
 }
 const upstream=http.request({hostname:'127.0.0.1',port:ports[service],path:req.url,method:req.method,headers:{...req.headers,host:'localhost:'+ports[service]}},reply=>{res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);});
 upstream.on('error',()=>{res.writeHead(502);res.end('Local service unavailable');});req.pipe(upstream);
}).listen(5190,'127.0.0.1',()=>process.stdout.write('Built UI preview: http://127.0.0.1:5190/resources/\n'));
