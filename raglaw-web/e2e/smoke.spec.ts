import { test, expect } from '@playwright/test';

const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? 'raglaw-eval';

test('login and chat smoke', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: '欢迎使用 RagLaw' })).toBeVisible();
  await page.getByPlaceholder('描述您的法律问题…').fill('劳动合同试用期最长多久？');
  await page.getByLabel('发送').click();
  await expect(page.locator('.rl-bubble--assistant')).toBeVisible({ timeout: 30_000 });
});

test('reference cards when corpus is seeded', async ({ page, request }) => {
  test.skip(process.env.E2E_WITH_CORPUS !== '1', 'Run scripts/seed-e2e-corpus.sh and set E2E_WITH_CORPUS=1');

  const loginRes = await request.post('/api/v1/auth/login', {
    data: { email: 'admin@raglaw.local', password: adminPassword },
  });
  const loginBody = await loginRes.json() as { success: boolean; data?: { accessToken: string } };
  expect(loginBody.success).toBe(true);
  const token = loginBody.data!.accessToken;

  const convRes = await request.post('/api/v1/conversations', {
    headers: { Authorization: `Bearer ${token}` },
    data: { agentCode: 'STATUTE_CIVIL' },
  });
  const convBody = await convRes.json() as { success: boolean; data?: { id: string } };
  expect(convBody.success).toBe(true);
  const conversationId = convBody.data!.id;

  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await page.goto(`/?c=${conversationId}`);

  await page.getByPlaceholder('描述您的法律问题…').fill('公司拖欠工资如何维权？');
  await page.getByLabel('发送').click();

  await expect(page.locator('.rl-bubble--assistant')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId('reference-card').first()).toBeVisible({ timeout: 30_000 });
});
