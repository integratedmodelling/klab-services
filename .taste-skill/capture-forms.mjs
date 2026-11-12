// Visual inspection of authenticated form components using the existing isolated fixtures.
import {chromium} from '../klab.core.services/src/main/webui/node_modules/@playwright/test/index.mjs';
import {spawn,execFileSync} from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const repository=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const ui=path.join(repository,'klab.core.services/src/main/webui');
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','4251','--strictPort'],{cwd:ui,windowsHide:true,stdio:'ignore'});
let browser;
try {
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:4251/tests/review.html')).ok)break;}catch{}await new Promise(r=>setTimeout(r,200));}
 browser=await chromium.launch({channel:'chrome',headless:true});
 const css=(await fs.readFile(path.join(ui,'src/styles/theme.css'),'utf8'))+'\n'+(await fs.readFile(path.join(ui,'src/styles.css'),'utf8')).replace('@import "./styles/theme.css";','');
 const quasar=await fs.readFile(path.join(ui,'node_modules/quasar/dist/quasar.css'),'utf8');
 for(const [name,url,selector] of [['review','review.html?flow=flow-1&stage=stage-1','.ontology-review'],['codelists','authority-codelists.html','input'],['credentials','credentials.html','.q-form']]){
  const page=await browser.newPage();
  await page.route('**/credentials/schemata',r=>r.fulfill({json:[{id:'basic',label:'Username and password',fields:[{name:'username',label:'Username',required:true,secret:false},{name:'password',label:'Password',required:true,secret:true}]}]}));
  await page.route(/\/credentials$/,r=>r.fulfill({json:[]}));
  await page.goto('http://127.0.0.1:4251/tests/'+url);
  if(name==='codelists') { await page.getByRole('button',{name:'Refresh',exact:true}).click(); await page.getByRole('button',{name:'Review',exact:true}).click(); }
  await page.locator(selector).first().waitFor();
  await page.addStyleTag({url:'http://127.0.0.1:4251/node_modules/@quasar/extras/exports/material-icons/material-icons.css'});
  await page.addStyleTag({content:quasar});
  await page.addStyleTag({content:css});
  for(const theme of ['light','dark']){
   await page.evaluate(async value=>{const m=await import('/src/services/theme.ts');m.theme.value=value;m.initializeTheme();},theme);
   for(const width of [1440,390]){
    await page.setViewportSize({width,height:width<600?844:900});
    await page.screenshot({path:path.join(repository,'.taste-skill/after',name+'-'+theme+'-'+width+'.png'),fullPage:true});
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
    if(overflow>0) throw new Error(name+' '+theme+' '+width+': '+overflow+'px overflow');
   }
  }
  await page.close();
 }
} finally {
 await browser?.close();
 try{execFileSync('taskkill',['/PID',String(server.pid),'/T','/F'],{stdio:'ignore',windowsHide:true});}catch{server.kill();}
}
