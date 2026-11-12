import { chromium } from '../klab.core.services/src/main/webui/node_modules/@playwright/test/index.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const phase = process.argv[2] || 'before';
const output = new URL(`./${phase}/`, import.meta.url);
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({channel:'chrome',headless:true});
const results=[];
try {
 for (const [port,service] of [[8091,'reasoner'],[8092,'resources'],[8093,'resolver'],[8094,'runtime']]) {
  const page=await browser.newPage();
  const base=`http://localhost:${port}/${service}/`;
  if(phase==='after') {
   const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../klab.core.services/target/generated-resources/webui/static');
   await page.route(`http://localhost:${port}/**`,async route=>{
    const relative=new URL(route.request().url()).pathname.slice(('/'+service+'/').length);
    if(!relative||relative.startsWith('ui/')||relative.startsWith('apps/')) {
     await route.fulfill({contentType:'text/html',body:(await fs.readFile(path.join(root,'index.html'),'utf8')).replaceAll('__KLAB_BASE_HREF__','/'+service+'/')});return;
    }
    if(relative.startsWith('assets/')||['klab-logo.svg','favicon.svg'].includes(relative)){
     let file=path.join(root,relative);
     try { await fs.access(file); } catch {
      const extension=path.extname(relative).slice(1);
      const html=await fs.readFile(path.join(root,'index.html'),'utf8');
      const replacement=html.match(new RegExp('assets/index-[^" ]+\\.'+extension));
      if(relative.startsWith('assets/index-')&&replacement)file=path.join(root,replacement[0]);else {await route.continue();return;}
     }
     await route.fulfill({path:file});return;
    }
    await route.continue();
   });
  }
  const config=await (await fetch(base+'public/ui/config')).json();
  const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base); await page.locator('.intro-row h1').waitFor();
  await page.getByRole('button',{name:'Refresh dashboard'}).click();
  await page.waitForTimeout(700);
  await page.locator('.capability-content').waitFor();
  const controls=await page.locator('a,button,input,select,textarea').evaluateAll(els=>els.map(e=>({tag:e.tagName,label:e.getAttribute('aria-label')||e.textContent?.trim(),href:e.getAttribute('href')})));
  for(const width of [1440,1024,820,768,390,320]) {
   await page.setViewportSize({width,height:width<600?844:900});
   await page.screenshot({path:new URL(`${service}-${width}.png`,output).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
   const measurements=await page.evaluate(()=>{
    const s=getComputedStyle(document.querySelector('h1'));const card=getComputedStyle(document.querySelector('.metric-card'));const main=document.querySelector('.dashboard-main').getBoundingClientRect();
    return {overflow:document.documentElement.scrollWidth-innerWidth,pageHeight:document.documentElement.scrollHeight,h1Size:s.fontSize,h1Font:s.fontFamily,cardRadius:card.borderRadius,cardShadow:card.boxShadow,leftEdge:main.left,rightEdge:innerWidth-main.right,background:getComputedStyle(document.querySelector('.dashboard-shell')).backgroundImage};
   });
   results.push({service,width,...measurements});
  }
  await page.setViewportSize({width:1440,height:900});
  const expansion=page.getByText('Installed components',{exact:true});
  if(await expansion.count()){await expansion.click();await expansion.click();}
  for(const route of config.pages){
   await page.locator(`.header-links a[href="ui/${route.name}"]`).click();
   await page.locator('.full-page-heading h1').waitFor();
   if(route.requiresAuthentication) await page.locator('.auth-dialog').waitFor();
   await page.waitForTimeout(350);
   await page.screenshot({path:new URL(`${service}-${route.name}.png`,output).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
   const gated=await page.locator('.auth-dialog').isVisible();
   results.push({service,route:route.name,title:await page.locator('h1').textContent(),gated,forms:await page.locator('input,select,textarea').count()});
   if(gated) await page.getByRole('link',{name:'Back to dashboard',exact:true}).click();
   else await page.getByRole('link',{name:'Service dashboard'}).click();
   await page.locator('.capability-content').waitFor();
  }
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('button',{name:'Service interfaces'}).click();
  await page.getByRole('link',{name:config.pages[0].title,exact:true}).last().click();
  await page.locator('.full-page-heading h1').waitFor();
  if(config.pages[0].requiresAuthentication) await page.locator('.auth-dialog').waitFor();
  if(await page.locator('.auth-dialog').isVisible()) await page.getByRole('link',{name:'Back to dashboard',exact:true}).click();
  const api=await page.request.get(base+'swagger-ui/index.html');
  results.push({service,controls,errors,openapi:api.status(),mobileMenu:true});
  if(phase==='after'){
   await page.getByRole('link',{name:'Service dashboard'}).click();
   await page.locator('.capability-content').waitFor();
   await page.getByRole('button',{name:'Switch to dark theme'}).click();
   await page.reload();
   await page.locator('html[data-theme="dark"]').waitFor();
   await page.locator('.capability-content').waitFor();
   await page.setViewportSize({width:1440,height:900});
   await page.screenshot({path:new URL(`${service}-dark.png`,output).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
   await page.getByRole('button',{name:'Switch to light theme'}).click();
  }
  await page.goto(base);
  await page.locator('.capability-content').waitFor();
  await page.setViewportSize({width:1440,height:900});
  await page.locator('.header-links a').filter({hasText:'OpenAPI'}).click();
  await page.waitForURL(/swagger-ui/);
  results.push({service,openapiClick:true,url:page.url()});
  await page.goto(base);
  await page.locator('.capability-content').waitFor();
  let handoff;
  await page.route('https://login.integratedmodelling.org/**',async route=>{
   const u=new URL(route.request().url());
   if(u.pathname.endsWith('/auth')&&u.searchParams.get('prompt')!=='none') {
    handoff={path:u.pathname,client:u.searchParams.get('client_id'),returnUri:u.searchParams.get('redirect_uri')};
    await route.fulfill({contentType:'text/html',body:'<!doctype html><title>Authentication handoff checked</title>'});
   } else await route.continue();
  });
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.waitForURL(/login\.integratedmodelling\.org/);
  results.push({service,signInHandoff:handoff});
  await page.close();
 }
} finally { await fs.writeFile(new URL('measurements.json',output),JSON.stringify(results,null,2));await browser.close(); }

