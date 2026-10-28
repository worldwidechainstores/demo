import { test as setup, expect } from '@playwright/test';
import { fillIdpLogin } from './idp-login';

/**
 * One-time sign-in through the central platform IdP, persisted as Playwright
 * storage state so every spec reuses the session instead of logging in per
 * test. This is the standard Playwright auth pattern and it matters here for
 * two reasons:
 *
 *  - The IdP's {@code AuthRateLimitFilter} throttles {@code /login} at
 *    20 requests/min/IP, and all docker traffic collapses to one source IP —
 *    a per-test login (14+ specs) trips it. Reusing the session means only
 *    THIS setup hits {@code /login}; every spec silently re-auths via the IdP
 *    session cookie through {@code /oauth2/authorize}, which is not throttled.
 *  - The credential is entered exactly once.
 *
 * Prereq: the platform stack (IdP on :8090, with the DEMO module + demo-module
 * client registered) and the demo stack (frontend :8082) must be running.
 */
// Relative to the config dir (frontend/), matching `storageState` in
// playwright.config.ts.
const authFile = '.auth/state.json';

setup('authenticate', async ({ page }) => {
  await page.goto('/');
  // RequireAuth fires the OIDC redirect → styled IdP login page.
  await expect(page).toHaveURL(/:8090\/login/);
  // `wcstest` is the dedicated e2e ADMIN (platform AppUserSeeder); `wcs` is
  // the human dev login and is never used by automated tests. See
  // pom/frontend/tests/e2e/README.md for the full user matrix.
  await fillIdpLogin(page, process.env.WCS_USERNAME ?? 'wcstest', process.env.WCS_PASSWORD ?? 'wcstest123');
  const signIn = page.getByRole('button', { name: /sign in/i });
  await expect(signIn).toBeEnabled();
  await signIn.click();
  // Code flow completes via /auth/callback and lands authenticated on home.
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('button', { name: /sign out/i })).toBeVisible();
  // Persist cookies (incl. the IdP session cookie) for silent re-auth.
  await page.context().storageState({ path: authFile });
});
