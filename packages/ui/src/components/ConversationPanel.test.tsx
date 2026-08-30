import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConversationPanel } from './ConversationPanel';
import type { ChatMessage } from './ConversationPanel';

vi.mock('./CitationMarkdown', () => ({
  CitationMarkdown: ({ content }: { content: string }) => <div data-testid="markdown">{content}</div>,
}));

function renderPanel(
  messages: ChatMessage[],
  streaming: boolean,
  options?: { onRegenerate?: (id?: string) => void },
) {
  return render(
    <ConversationPanel
      messages={messages}
      input=""
      streaming={streaming}
      onInputChange={() => {}}
      onSubmit={(e) => e.preventDefault()}
      canRegenerate
      onRegenerate={options?.onRegenerate}
    />,
  );
}

const catalogReferences = [
  {
    index: 1,
    chunkId: 'catalog-doc-1',
    documentId: 'doc-1',
    path: '/STATUTE/CIVIL',
    excerpt: '类型: 法规 | 分类: /STATUTE/CIVIL',
    score: 1,
    title: '民法典',
    source: 'knowledge' as const,
  },
];

afterEach(() => {
  cleanup();
});

describe('ConversationPanel streaming ellipsis', () => {
  it('shows no ellipsis when streaming assistant already has content', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        { role: 'assistant', content: '部分回答' },
      ],
      true,
    );
    expect(screen.queryByTestId('chat-streaming-ellipsis')).toBeNull();
    expect(screen.getByTestId('markdown').textContent).toBe('部分回答');
  });

  it('shows one ellipsis when streaming assistant content is empty', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        { role: 'assistant', content: '' },
      ],
      true,
    );
    expect(screen.getAllByTestId('chat-streaming-ellipsis').length).toBe(1);
  });

  it('shows fallback ellipsis when streaming and last message is user', () => {
    renderPanel([{ role: 'user', content: '问题' }], true);
    expect(screen.getAllByTestId('chat-streaming-ellipsis').length).toBe(1);
  });
});

describe('ConversationPanel reference chip', () => {
  it('hides inline references while streaming without content', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        {
          role: 'assistant',
          content: '',
          references: catalogReferences,
          streamStatus: '正在检索知识库…',
        },
      ],
      true,
    );
    expect(screen.queryByTestId('reference-list')).toBeNull();
    expect(screen.queryByTestId('chat-stream-status')).toBeNull();
    expect(screen.getAllByTestId('chat-streaming-ellipsis').length).toBe(1);
  });

  it('shows footer chip after streaming completes', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        {
          role: 'assistant',
          content: '回答正文',
          references: catalogReferences,
        },
      ],
      false,
    );
    expect(screen.queryByTestId('reference-list')).toBeNull();
    expect(screen.getByText(/已阅读知识库目录/)).toBeTruthy();
  });

  it('opens materials panel when chip is clicked', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        {
          role: 'assistant',
          content: '回答正文',
          references: catalogReferences,
        },
      ],
      false,
    );
    fireEvent.click(screen.getByText(/已阅读知识库目录/));
    expect(screen.getByText('检索依据')).toBeTruthy();
    expect(screen.getByTestId('reference-material-item').textContent).toContain('民法典');
  });

  it('shows regenerate on earlier assistant messages when they have ids', () => {
    renderPanel(
      [
        { role: 'user', content: '问题1' },
        { id: 'a1', role: 'assistant', content: '回答1', references: catalogReferences },
        { role: 'user', content: '问题2' },
        { id: 'a2', role: 'assistant', content: '回答2' },
      ],
      false,
      { onRegenerate: () => {} },
    );
    expect(screen.getAllByLabelText('重新生成').length).toBe(2);
  });

  it('keeps earlier assistant footer visible while streaming last reply', () => {
    renderPanel(
      [
        { role: 'user', content: '问题1' },
        { id: 'a1', role: 'assistant', content: '回答1', references: catalogReferences },
        { role: 'user', content: '问题2' },
        { role: 'assistant', content: '', references: catalogReferences },
      ],
      true,
    );
    expect(screen.getAllByText(/已阅读知识库目录/).length).toBe(2);
    expect(screen.queryByLabelText('复制')).toBeNull();
    expect(screen.queryByLabelText('重新生成')).toBeNull();
    expect(screen.getAllByTestId('chat-streaming-ellipsis').length).toBe(1);
  });

  it('hides copy and regenerate while streaming even with references', () => {
    renderPanel(
      [
        { role: 'user', content: '问题' },
        {
          role: 'assistant',
          content: '草稿回答',
          references: catalogReferences,
        },
      ],
      true,
    );
    expect(screen.queryByLabelText('复制')).toBeNull();
    expect(screen.queryByLabelText('重新生成')).toBeNull();
  });
});

const knowledgeReferences = [
  {
    index: 1,
    chunkId: 'chunk-1',
    documentId: 'doc-1',
    path: '/STATUTE/CRIMINAL',
    excerpt: '第三十三条 刑罚分为主刑和附加刑。',
    score: 0.9,
    title: '中华人民共和国刑法',
    source: 'knowledge' as const,
  },
  {
    index: 2,
    chunkId: 'chunk-2',
    documentId: 'doc-1',
    path: '/STATUTE/CRIMINAL',
    excerpt: '第三十四条 附加刑的种类如下。',
    score: 0.8,
    title: '中华人民共和国刑法',
    source: 'knowledge' as const,
  },
];

describe('ConversationPanel multi-turn consistency', () => {
  it('shows reference chip only on turns with references', () => {
    renderPanel(
      [
        { role: 'user', content: '刑法中刑罚种类有哪些' },
        {
          id: 'a1',
          role: 'assistant',
          content: '依据 **《中华人民共和国刑法》第三十三条**[1]。',
          references: knowledgeReferences,
        },
        { role: 'user', content: '那我想买社保呢' },
        {
          id: 'a2',
          role: 'assistant',
          content: '参保需依据 **《社会保险法》第五十八条** 办理。',
          references: [],
        },
        { role: 'user', content: '相关法规的适用条件是什么？' },
        {
          id: 'a3',
          role: 'assistant',
          content: '知识库中未检索到明确条款。',
          references: knowledgeReferences,
        },
      ],
      false,
    );

    expect(screen.getAllByText(/已阅读相关资料/).length).toBe(2);
    expect(screen.queryByText(/已阅读相关资料/, { selector: '.rl-message:nth-child(4) *' })).toBeNull();
  });
});
