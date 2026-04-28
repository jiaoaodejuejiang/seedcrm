import { test, expect } from '@playwright/test';
test('homepage loads', async ({ page }) => {
  await page.goto('http://127.0.0.1:4173/', { waitUntil: 'networkidle' });
  await expect(page.locator('body')).toContainText(/登录|CRM 控制台/);
});
