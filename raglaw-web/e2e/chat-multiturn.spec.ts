import { test, expect } from '@playwright/test';

const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? 'admin12345';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel('邮箱').fill('admin@raglaw.local');
  await page.getByLabel('密码').fill(adminPassword);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: '欢迎使用 RagLaw' })).toBeVisible();
}

function countNumberedSectionStarts(text: string): number {
  const matches = text.match(/(?:^|\n\n)1\.\s+\*\*/g);
  return matches?.length ?? 0;
}

test.describe('chat multiturn matrix', () => {
  test.skip(process.env.E2E_WITH_CORPUS !== '1', 'Run scripts/seed-e2e-corpus.sh and set E2E_WITH_CORPUS=1');

  test('session A: three turns UI and content consistency', async ({ page }) => {
    await login(page);

    const questions = [
      '刑法中刑罚种类有哪些',
      '那我想买社保呢',
      '城乡居民医保可以异地报销吗？',
    ];

    for (const question of questions) {
      await page.getByPlaceholder('描述您的法律问题…').fill(question);
      await page.getByLabel('发送').click();
      await expect(page.locator('.rl-message--assistant').last()).toBeVisible({ timeout: 60_000 });
      const lastAssistant = page.locator('.rl-message--assistant').last();
      await expect(lastAssistant.locator('.rl-assistant-body')).not.toBeEmpty({ timeout: 60_000 });
    }

    const assistants = page.locator('.rl-message--assistant');
    await expect(assistants).toHaveCount(3);

    const turn1 = assistants.nth(0);
    const turn2 = assistants.nth(1);
    const turn3 = assistants.nth(2);

    const turn1ChipCount = await turn1.getByText(/已阅读相关资料/).count();
    if (turn1ChipCount > 0) {
      await expect(turn1.getByText(/已阅读相关资料/)).toBeVisible();
    }

    const turn2ChipCount = await turn2.getByText(/已阅读相关资料/).count();
    if (turn2ChipCount === 0) {
      await expect(turn2.locator('.rl-citation-mark')).toHaveCount(0);
    }

    const turn3ChipCount = await turn3.getByText(/已阅读相关资料/).count();
    if (turn3ChipCount > 0) {
      await expect(turn3.getByText(/已阅读相关资料/)).toBeVisible();
    }

    const turn3Text = await turn3.locator('.rl-assistant-body').innerText();
    expect(countNumberedSectionStarts(turn3Text)).toBeLessThanOrEqual(1);
    expect(turn3Text.match(/1\.\s+/g)?.length ?? 0).toBeLessThanOrEqual(3);

    const citationMarks = turn2.locator('.rl-citation-mark');
    await expect(citationMarks).toHaveCount(0);
  });

  test('session B: new conversation isolates state', async ({ page }) => {
    await login(page);

    await page.getByPlaceholder('描述您的法律问题…').fill('刑法中刑罚种类有哪些');
    await page.getByLabel('发送').click();
    await expect(page.locator('.rl-message--assistant').last()).toBeVisible({ timeout: 60_000 });

    await page.getByTestId('history-toggle').click();
    await page.getByRole('button', { name: '新建' }).click();
    await expect(page.getByRole('heading', { name: '欢迎使用 RagLaw' })).toBeVisible();

    await page.getByPlaceholder('描述您的法律问题…').fill('刑法中刑罚种类有哪些');
    await page.getByLabel('发送').click();
    await expect(page.locator('.rl-message--assistant')).toHaveCount(1, { timeout: 60_000 });
  });

  test('session C: reload preserves assistant footers', async ({ page, request }) => {
    const loginRes = await request.post('/api/v1/auth/login', {
      data: { email: 'admin@raglaw.local', password: adminPassword },
    });
    const loginBody = await loginRes.json() as { success: boolean; data?: { accessToken: string } };
    expect(loginBody.success).toBe(true);
    const token = loginBody.data!.accessToken;

    const convRes = await request.post('/api/v1/conversations', {
      headers: { Authorization: `Bearer ${token}` },
      data: { agentCode: 'STATUTE' },
    });
    const convBody = await convRes.json() as { success: boolean; data?: { id: string } };
    expect(convBody.success).toBe(true);
    const conversationId = convBody.data!.id;

    await login(page);
    await page.goto(`/?c=${conversationId}`);

    for (const question of ['刑法中刑罚种类有哪些', '那我想买社保呢']) {
      await page.getByPlaceholder('描述您的法律问题…').fill(question);
      await page.getByLabel('发送').click();
      await expect(page.locator('.rl-message--assistant').last()).toBeVisible({ timeout: 60_000 });
    }

    await page.reload();
    await expect(page.locator('.rl-message--assistant')).toHaveCount(2, { timeout: 30_000 });
    await expect(page.getByText(/已阅读相关资料/).first()).toBeVisible();
  });
});
