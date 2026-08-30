import { test, expect, type Page } from '@playwright/test';

const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? 'admin12345';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: '欢迎使用 RagLaw' })).toBeVisible({ timeout: 15_000 });
}

test('contract review page requires doc id', async ({ page }) => {
  await login(page);
  await page.goto('/contracts/review');
  await expect(page.getByText('缺少合同文档 ID')).toBeVisible();
});

test('contracts upload page shows workbench', async ({ page }) => {
  await login(page);
  await page.goto('/contracts');
  await expect(page.getByText('上传后自动进入审查页')).toBeVisible();
});

test('contract review workbench shows split panels when doc is open', async ({ page, request }) => {
  test.skip(process.env.E2E_CONTRACT_REVIEW !== '1', 'Set E2E_CONTRACT_REVIEW=1 with backend running');

  const loginRes = await request.post('/api/v1/auth/login', {
    data: { email: 'admin@raglaw.local', password: adminPassword },
  });
  const loginBody = await loginRes.json() as { success: boolean; data?: { accessToken: string } };
  expect(loginBody.success).toBe(true);
  const token = loginBody.data!.accessToken;

  const listRes = await request.get('/api/v1/contracts', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const listBody = await listRes.json() as {
    success: boolean;
    data?: Array<{ documentId: string; status: string }>;
  };
  test.skip(!listBody.success || !listBody.data?.length, 'No contracts in history');

  const completed = listBody.data!.find((item) => item.status !== 'PENDING') ?? listBody.data![0];
  await login(page);
  await page.goto(`/contracts/review?doc=${completed.documentId}`);

  await expect(page.locator('.rl-contract-doc-panel')).toBeVisible();
  await expect(page.locator('.rl-contract-risk-panel')).toBeVisible();
  await expect(page.getByRole('button', { name: '风险' })).toBeVisible();
});

test('contract upload navigates to review with auto pipeline', async ({ page }) => {
  test.skip(process.env.E2E_CONTRACT_REVIEW !== '1', 'Set E2E_CONTRACT_REVIEW=1 with backend running');

  await login(page);
  await page.goto('/contracts');

  await page.setInputFiles('input[type="file"]', {
    name: 'sample-contract.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('劳动合同\n甲方：A公司\n乙方：张三'),
  });
  await page.getByRole('button', { name: '开始审查' }).click();

  await expect(page).toHaveURL(/\/contracts\/review\?doc=.+&auto=1/, { timeout: 30_000 });
  await expect(page.locator('.rl-contract-workbench__body')).toBeVisible();
  await expect(page.locator('.rl-contract-doc-panel, .rl-contract-risk-panel')).toHaveCount(2);
});

test('accept revision updates risk card state', async ({ page, request }) => {
  test.skip(process.env.E2E_CONTRACT_REVIEW !== '1', 'Set E2E_CONTRACT_REVIEW=1 with backend running');

  const loginRes = await request.post('/api/v1/auth/login', {
    data: { email: 'admin@raglaw.local', password: adminPassword },
  });
  const loginBody = await loginRes.json() as { success: boolean; data?: { accessToken: string } };
  const token = loginBody.data!.accessToken;

  const listRes = await request.get('/api/v1/contracts', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const listBody = await listRes.json() as {
    success: boolean;
    data?: Array<{ documentId: string; status: string }>;
  };
  test.skip(!listBody.success || !listBody.data?.length, 'No contracts in history');

  const docId = listBody.data!.find((item) => item.status !== 'PENDING')?.documentId ?? listBody.data![0].documentId;
  const reviewRes = await request.get(`/api/v1/contracts/${docId}/review`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const reviewBody = await reviewRes.json() as {
    success: boolean;
    data?: { risks: Array<{ id: string; accepted: boolean }> };
  };
  test.skip(!reviewBody.success || !reviewBody.data?.risks?.length, 'No risks to accept');

  const riskId = reviewBody.data!.risks.find((risk) => !risk.accepted)?.id ?? reviewBody.data!.risks[0].id;

  await login(page);
  await page.goto(`/contracts/review?doc=${docId}`);
  await page.getByRole('button', { name: '风险' }).click();

  const acceptButton = page.locator(`#risk-card-${riskId}`).getByRole('button', { name: '采纳修订' });
  test.skip(!(await acceptButton.isVisible().catch(() => false)), 'Risk already accepted');

  await acceptButton.click();
  await expect(page.locator(`#risk-card-${riskId}`).getByText('已采纳')).toBeVisible({ timeout: 15_000 });
});
