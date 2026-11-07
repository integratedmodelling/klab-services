import { test, expect } from '@playwright/test';
import { revealReviewTools } from './review-tools';

const participant = '/tests/review-rounds.html?flow=flow-1&cycle=cycle-2&round=community-2';
const shell = '/tests/review-shell.html?flow=flow-1&cycle=cycle-2&round=community-2';

test('candidate reading and feedback lead; exact source, alternatives and history stay accessible', async ({page}) => {
  await page.goto(participant);
  await expect(page.getByLabel('Readable candidate')).toContainText('ontology-review');
  await expect(page.getByLabel('Your feedback', {exact:true})).toBeVisible();
  await expect(page.getByLabel('Candidate proposal source')).not.toBeVisible();
  await expect(page.getByLabel('Your alternative proposal', {exact:true})).not.toBeVisible();
  await expect(page.getByLabel('Feedback proposal')).not.toBeVisible();
  await page.getByText('Exact proposal source', {exact:true}).click();
  await expect(page.getByText('Exact proposal source', {exact:true})).toHaveCSS('list-style-type', 'disclosure-open');
  await expect(page.getByLabel('Candidate proposal source')).toHaveValue(/"r2"/);
  await expect(page.getByRole('button', {name:'Save new draft attachment',exact:true})).toHaveCount(0);
  await page.setViewportSize({width:390,height:844});
  await expect(page.getByRole('link',{name:'Feedback and next steps',exact:true})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('YAML is readable, markup stays text, and unparseable source has an honest fallback', async ({page}) => {
  await page.goto('/tests/review.html?flow=flow-1&stage=stage-1');
  await revealReviewTools(page);
  await page.getByLabel('File purpose',{exact:true}).selectOption('bootstrap-proposal');
  const source = 'proposal:\n  id: reader-test\n  revision_id: r1\n  purpose: "<script>window.readerExecuted=true</script>"\n  evidence:\n    - title: Supplied evidence\n';
  await page.getByLabel('Stage files',{exact:true}).setInputFiles({name:'candidate.yaml',mimeType:'application/yaml',buffer:Buffer.from(source)});
  await page.getByRole('button',{name:'Upload selected files',exact:true}).click();
  await expect(page.getByLabel('Readable candidate')).toContainText('Supplied evidence');
  await expect(page.getByLabel('Readable candidate')).toContainText('<script>');
  expect(await page.evaluate(() => (window as any).readerExecuted)).toBeUndefined();
  await revealReviewTools(page);
  await expect(page.getByLabel('Candidate proposal source')).toHaveValue(source);
  const downloading = page.waitForEvent('download');
  await page.getByRole('button',{name:'Download draft',exact:true}).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe('proposal-draft.yaml');
  const stream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream!) chunks.push(Buffer.from(chunk));
  expect(Buffer.concat(chunks).toString('utf8')).toBe(source);
  const large = JSON.stringify({proposal:{title:'Large candidate',assets:Array.from({length:1200},(_,i)=>({id:`asset-${i}`,description:'Supplied content'}))}});
  await page.getByLabel('Candidate proposal source').fill(large);
  await expect(page.getByLabel('Readable candidate')).toContainText('shortened in the reading view');
  await expect(page.getByLabel('Candidate proposal source')).toHaveValue(large);
  await page.getByLabel('Candidate proposal source').fill('proposal: [invalid');
  await expect(page.getByLabel('Readable candidate')).toContainText('structured reading view is unavailable');
  await expect(page.getByLabel('Candidate proposal source')).toHaveValue('proposal: [invalid');
  const malformed = 'proposal: [invalid ' + 'retained-source '.repeat(3000);
  await page.getByLabel('Candidate proposal source').fill(malformed);
  await expect(page.getByLabel('Readable candidate')).toContainText('shortened in the reading view');
  expect((await page.locator('.reader-fallback').textContent())!.length).toBeLessThanOrEqual(20000);
  await expect(page.getByLabel('Candidate proposal source')).toHaveValue(malformed);
});

test('long candidate titles and expanded history fit mobile and zoomed layouts', async ({page}) => {
  await page.goto(participant+'&readable&long');
  await expect(page.getByLabel('Readable candidate')).toContainText('Review the supplied scope');
  await revealReviewTools(page);
  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.locator('.ontology-review').evaluate(el => (el as HTMLElement).style.zoom = '2');
  await page.setViewportSize({width:1440,height:1000});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('sharing requires explicit commitment, prevents duplicate writes, and returns an audience-bound receipt', async ({page}) => {
  await page.goto(participant);
  await page.getByLabel('Your feedback',{exact:true}).fill('Scientific assessment');
  await page.getByRole('button',{name:'Submit feedback',exact:true}).click();
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(0);
  await expect(page.getByRole('heading',{name:'Review before sharing'})).toBeVisible();
  await expect(page.getByText('Audience: permitted participants in community review.',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Keep editing',exact:true}).click();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Scientific assessment');
  await page.getByRole('button',{name:'Submit feedback',exact:true}).click();
  await page.getByRole('button',{name:'Share feedback',exact:true}).dblclick();
  await expect(page.getByRole('heading',{name:'Feedback shared',exact:true})).toBeVisible();
  await expect(page.getByRole('status').filter({hasText:'Feedback shared'})).toContainText('revision r2');
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(1);
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('');
});

test('lost submission response is reconciled without sharing twice', async ({page}) => {
  await page.goto(participant+'&submission-lost');
  await page.getByLabel('Your feedback',{exact:true}).fill('Retain this assessment');
  await page.getByRole('button',{name:'Submit feedback',exact:true}).click();
  await page.getByRole('button',{name:'Share feedback',exact:true}).click();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Retain this assessment');
  await expect(page.getByRole('button',{name:'Submit feedback',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'Check submission status',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Feedback shared',exact:true})).toBeVisible();
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(1);
});

test('failed submission retains text and only re-enables sharing after an explicit status check', async ({page}) => {
  await page.goto(participant+'&submit-unavailable');
  await page.getByLabel('Your feedback',{exact:true}).fill('Keep this draft');
  await page.getByRole('button',{name:'Submit feedback',exact:true}).click();
  await page.getByRole('button',{name:'Share feedback',exact:true}).click();
  await expect(page.getByRole('button',{name:'Submit feedback',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'Check submission status',exact:true}).click();
  await expect(page.getByText('No matching submitted contribution was found.',{exact:false})).toBeVisible();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Keep this draft');
  await expect(page.getByRole('button',{name:'Submit feedback',exact:true})).toBeEnabled();
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(1);
});

test('shell navigation offers stay, save privately, or explicit discard', async ({page}) => {
  await page.goto(shell);
  await page.getByLabel('Your feedback',{exact:true}).fill('Private work in the shell');
  await page.getByRole('link',{name:'Workflows',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('Keep your review work?');
  await page.getByRole('button',{name:'Stay here',exact:true}).click();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Private work in the shell');
  await page.getByRole('link',{name:'Workflows',exact:true}).click();
  await page.getByRole('button',{name:'Save and leave',exact:true}).click();
  await expect(page).toHaveURL(/\/ui\/workflows$/);
  const calls = await page.evaluate(() => (window as any).calls);
  expect(calls).toHaveLength(1);
  expect(calls[0].command).toMatchObject({operation:'SAVE_CONTRIBUTION',submit:false,text:'Private work in the shell'});
});

test('browser back preserves the current URL and draft until explicitly discarded', async ({page}) => {
  await page.goto(shell);
  await page.getByLabel('Your feedback',{exact:true}).fill('Keep my browser-back draft');
  const original = page.url();
  await page.evaluate(() => history.back());
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page).toHaveURL(original);
  await page.getByRole('button',{name:'Stay here',exact:true}).click();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Keep my browser-back draft');
  await page.evaluate(() => history.back());
  await page.getByRole('button',{name:'Discard and leave',exact:true}).click();
  await expect(page).toHaveURL('http://127.0.0.1:4229/');
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(0);
});

test('a save conflict in the leave dialog retains the route and private work', async ({page}) => {
  await page.goto(shell+'&conflict');
  await page.getByLabel('Your feedback',{exact:true}).fill('Keep this conflicting draft');
  const original = page.url();
  await page.getByRole('link',{name:'Workflows',exact:true}).click();
  await page.getByRole('button',{name:'Save and leave',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('Your work could not be saved');
  await expect(page).toHaveURL(original);
  await page.getByRole('button',{name:'Stay here',exact:true}).click();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Keep this conflicting draft');
  expect(await page.evaluate(() => (window as any).calls)).toHaveLength(1);
});

test('workspace jump links preserve the review route and unsaved feedback under a service base URL', async ({page}) => {
  await page.goto(shell);
  await page.getByLabel('Your feedback',{exact:true}).fill('Keep this comment while moving within the review');
  const original = new URL(page.url());
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('link',{name:'Feedback and next steps',exact:true}).click();
  const destination = new URL(page.url());
  expect(destination.pathname).toBe(original.pathname);
  expect(destination.search).toBe(original.search);
  expect(destination.hash).toBe('#review-feedback');
  await expect(page.locator('#review-feedback')).toBeFocused();
  await expect(page.getByLabel('Your feedback',{exact:true})).toHaveValue('Keep this comment while moving within the review');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('link',{name:'Read candidate',exact:true}).click();
  await expect(page.locator('#review-candidate')).toBeFocused();
  expect(new URL(page.url()).pathname).toBe(original.pathname);
});
