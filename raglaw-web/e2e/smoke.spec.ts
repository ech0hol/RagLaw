import { test, expect } from '@playwright/test';

test('login and chat smoke', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill('admin-test-password');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: '智能法律咨询' })).toBeVisible();
  await page.getByPlaceholder('描述您的法律问题…').fill('劳动合同试用期最长多久？');
  await page.getByRole('button', { name: '发送' }).click();
  await expect(page.locator('.bubble.assistant')).toBeVisible({ timeout: 30_000 });
});
