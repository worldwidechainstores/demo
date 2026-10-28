import { expect, type Page } from '@playwright/test';

/**
 * Fills the IdP sign-in form without submitting it. The Company dropdown defaults to the
 * first company alphabetically, so any seeded code sorting before WCS makes a login that
 * leaves it alone fail as if the password were wrong (CSNX-14820). The pick is retried
 * because the form's own default-company effect can overwrite a selection made in the
 * same tick the options render.
 */
export async function fillIdpLogin(page: Page, username: string, password: string, company = 'WCS'): Promise<void> {
  const select = page.getByRole('combobox', { name: /company/i });
  await expect(select.locator(`option[value="${company}"]`)).toBeAttached({ timeout: 20_000 });
  await expect(async () => {
    await select.selectOption(company);
    await expect(select).toHaveValue(company, { timeout: 1_000 });
  }).toPass({ timeout: 10_000 });
  await page.getByRole('textbox', { name: /username/i }).fill(username);
  await page.getByRole('textbox', { name: /password/i }).fill(password);
}
