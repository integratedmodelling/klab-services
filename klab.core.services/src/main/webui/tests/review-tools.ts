import { expect, type Page } from '@playwright/test';
/** Existing lifecycle tests exercise the expert tools explicitly; default composition has separate regressions. */
export async function revealReviewTools(page: Page) {
  await expect(page.locator('.ontology-review')).toBeVisible();
  const disclosures = page.locator('details');
  for (let index = 0; index < await disclosures.count(); index++) {
    const disclosure = disclosures.nth(index);
    if (await disclosure.getAttribute('open') === null) await disclosure.locator(':scope > summary').click();
  }
}
