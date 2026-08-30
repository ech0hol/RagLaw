import { test, expect } from '@playwright/test';

const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? 'admin12345';

test('contract history delete shows native confirm dialog', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录' }).click();

  await page.goto('/contracts');
  await page.getByRole('button', { name: '历史合同' }).click();
  await expect(page.getByRole('dialog', { name: '历史合同' })).toBeVisible();

  const deleteBtn = page.getByRole('button', { name: '删除' }).first();
  const hasContracts = await deleteBtn.isVisible().catch(() => false);
  test.skip(!hasContracts, 'No contract history to delete');

  const dialogPromise = new Promise<void>((resolve) => {
    page.once('dialog', async (dialog) => {
      expect(dialog.type()).toBe('confirm');
      expect(dialog.message()).toMatch(/确定删除/);
      await dialog.dismiss();
      resolve();
    });
  });

  await deleteBtn.click();
  await dialogPromise;
});
