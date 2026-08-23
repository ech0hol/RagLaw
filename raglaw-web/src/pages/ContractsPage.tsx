import { FormEvent, useState } from 'react';
import { Card, PageHeader } from '@raglaw/ui';
import { ChatPage } from './ChatPage';

export function ContractsPage() {
  const [showChat, setShowChat] = useState(false);
  const [fileName, setFileName] = useState('');

  function onUpload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const fileInput = form.elements.namedItem('contract') as HTMLInputElement;
    const file = fileInput.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setShowChat(true);
  }

  if (showChat) {
    return (
      <div>
        <PageHeader title="合同审查" subtitle={`已选择：${fileName}（文本级审查 MVP，OCR 后续接入）`} />
        <ChatPage fixedAgentCode="CONTRACT_GENERAL" />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="合同审查"
        subtitle="上传合同文档，由合同审查助手识别风险条款并给出修订建议。"
      />
      <Card>
        <form className="rl-admin-form" onSubmit={onUpload}>
          <label>
            合同文件（PDF / Word / Markdown）
            <input name="contract" type="file" accept=".pdf,.doc,.docx,.md,.txt" />
          </label>
          <button type="submit" className="rl-btn rl-btn--primary">
            开始审查
          </button>
        </form>
        <p className="rl-text-muted">MVP：上传后进入合同专家对话；PDF 高亮与 OCR 在 Phase 4 后续迭代。</p>
      </Card>
    </div>
  );
}
