import { useMemo, useState } from 'react';
import { Badge } from '@raglaw/ui';
import {
  STAGE_LABELS,
  buildTimeline,
  formatJson,
  parseStageDetail,
  type TraceDetail,
} from './traceDetailTypes';

type TraceDetailContentProps = {
  detail: TraceDetail;
};

function copyToClipboard(text: string) {
  void navigator.clipboard.writeText(text);
}

export function TraceDetailContent({ detail }: TraceDetailContentProps) {
  const { trace, stages, chunks, llmUsage, a2aCalls, shadowLogs } = detail;
  const [copied, setCopied] = useState(false);

  const timeline = useMemo(() => buildTimeline(stages), [stages]);
  const totalSpanMs = useMemo(() => {
    const sum = stages.reduce((acc, s) => acc + (s.durationMs ?? 0), 0);
    return Math.max(sum, trace.latencyMs ?? 0, 1);
  }, [stages, trace.latencyMs]);

  const llmTotals = useMemo(() => {
    return llmUsage.reduce(
      (acc, usage) => ({
        prompt: acc.prompt + (usage.promptTokens ?? 0),
        completion: acc.completion + (usage.completionTokens ?? 0),
      }),
      { prompt: 0, completion: 0 },
    );
  }, [llmUsage]);

  const maxChunkScore = useMemo(() => {
    if (chunks.length === 0) return 1;
    return Math.max(...chunks.map((c) => c.score ?? 0), 0.01);
  }, [chunks]);

  function handleCopyId() {
    copyToClipboard(trace.id);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="rl-trace-detail">
      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">概览</h3>
        <dl className="rl-trace-kv">
          <div className="rl-trace-kv__row">
            <dt>Trace ID</dt>
            <dd>
              <code className="rl-trace-id">{trace.id}</code>
              <button type="button" className="rl-btn rl-btn--ghost rl-btn--sm" onClick={handleCopyId}>
                {copied ? '已复制' : '复制'}
              </button>
            </dd>
          </div>
          <div className="rl-trace-kv__row">
            <dt>时间</dt>
            <dd>{new Date(trace.createdAt).toLocaleString()}</dd>
          </div>
          <div className="rl-trace-kv__row">
            <dt>延迟</dt>
            <dd>{trace.latencyMs ?? '—'} ms</dd>
          </div>
          <div className="rl-trace-kv__row">
            <dt>Agent</dt>
            <dd><Badge variant="muted">{trace.agentCode}</Badge></dd>
          </div>
          <div className="rl-trace-kv__row">
            <dt>问题</dt>
            <dd>{trace.queryText}</dd>
          </div>
          {trace.langfuseUrl && (
            <div className="rl-trace-kv__row">
              <dt>Langfuse</dt>
              <dd>
                <a href={trace.langfuseUrl} target="_blank" rel="noreferrer">
                  在外部查看
                </a>
              </dd>
            </div>
          )}
        </dl>
      </section>

      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">时间线</h3>
        {timeline.length === 0 ? (
          <p className="rl-text-muted">暂无阶段数据</p>
        ) : (
          <div className="rl-trace-timeline">
            <div className="rl-trace-timeline__axis">
              <span>0 ms</span>
              <span>{totalSpanMs} ms</span>
            </div>
            <ul className="rl-trace-timeline__list">
              {timeline.map((stage) => {
                const duration = stage.durationMs ?? 0;
                const leftPct = (stage.offsetMs / totalSpanMs) * 100;
                const widthPct = Math.max((duration / totalSpanMs) * 100, duration > 0 ? 0.5 : 0);
                const { structured, raw } = parseStageDetail(stage.detailJson);
                return (
                  <li key={stage.id} className="rl-trace-timeline__item">
                    <div className="rl-trace-timeline__label">
                      <strong>{STAGE_LABELS[stage.stage] ?? stage.stage}</strong>
                      <span className="rl-text-muted">
                        +{stage.offsetMs} ms · {duration} ms
                      </span>
                    </div>
                    <div className="rl-trace-timeline__track">
                      <div
                        className="rl-trace-timeline__bar"
                        style={{ left: `${leftPct}%`, width: `${widthPct}%` }}
                        title={`${duration} ms`}
                      />
                    </div>
                    {structured.length > 0 && (
                      <dl className="rl-trace-kv rl-trace-kv--compact">
                        {structured.map((row) => (
                          <div key={row.key} className="rl-trace-kv__row">
                            <dt>{row.key}</dt>
                            <dd>{row.value}</dd>
                          </div>
                        ))}
                      </dl>
                    )}
                    {raw && (
                      <details className="rl-obs-json-detail">
                        <summary>更多属性</summary>
                        <pre className="rl-trace-detail-json">{formatJson(JSON.stringify(raw))}</pre>
                      </details>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </section>

      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">检索片段</h3>
        {chunks.length === 0 ? (
          <p className="rl-text-muted">暂无检索片段</p>
        ) : (
          <div className="rl-data-table-wrap">
            <table className="rl-data-table rl-data-table--compact">
              <thead>
                <tr>
                  <th>#</th>
                  <th>分数</th>
                  <th>路径</th>
                  <th>摘要</th>
                </tr>
              </thead>
              <tbody>
                {chunks.map((chunk, index) => (
                  <tr key={chunk.id}>
                    <td>{index + 1}</td>
                    <td>
                      <div className="rl-trace-score-cell">
                        <span>{(chunk.score ?? 0).toFixed(2)}</span>
                        <div className="rl-trace-score-bar">
                          <div
                            className="rl-trace-score-bar__fill"
                            style={{ width: `${((chunk.score ?? 0) / maxChunkScore) * 100}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>{chunk.path}</td>
                    <td className="rl-trace-excerpt">{chunk.excerpt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">LLM 用量</h3>
        {llmUsage.length === 0 ? (
          <p className="rl-text-muted">暂无 LLM 调用记录</p>
        ) : (
          <div className="rl-data-table-wrap">
            <table className="rl-data-table rl-data-table--compact">
              <thead>
                <tr>
                  <th>模型</th>
                  <th>Prompt tokens</th>
                  <th>Completion tokens</th>
                </tr>
              </thead>
              <tbody>
                {llmUsage.map((usage, index) => (
                  <tr key={`${usage.model}-${index}`}>
                    <td>{usage.model}</td>
                    <td>{usage.promptTokens ?? '—'}</td>
                    <td>{usage.completionTokens ?? '—'}</td>
                  </tr>
                ))}
                <tr className="rl-trace-totals-row">
                  <td><strong>合计</strong></td>
                  <td><strong>{llmTotals.prompt}</strong></td>
                  <td><strong>{llmTotals.completion}</strong></td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">A2A 委派</h3>
        {a2aCalls.length === 0 ? (
          <p className="rl-text-muted">暂无 A2A 调用</p>
        ) : (
          <div className="rl-data-table-wrap">
            <table className="rl-data-table rl-data-table--compact">
              <thead>
                <tr>
                  <th>路由</th>
                  <th>延迟</th>
                  <th>输入</th>
                  <th>输出</th>
                </tr>
              </thead>
              <tbody>
                {a2aCalls.map((call, index) => (
                  <tr key={index}>
                    <td>{call.fromAgent} → {call.toAgent}</td>
                    <td>{call.latencyMs ?? '—'} ms</td>
                    <td className="rl-trace-excerpt">{call.inputSummary ?? '—'}</td>
                    <td className="rl-trace-excerpt">{call.outputSummary ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rl-trace-detail__section">
        <h3 className="rl-trace-detail__section-title">影子路由</h3>
        {shadowLogs.length === 0 ? (
          <p className="rl-text-muted">暂无影子路由日志</p>
        ) : (
          <div className="rl-data-table-wrap">
            <table className="rl-data-table rl-data-table--compact">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>用户</th>
                  <th>系统</th>
                  <th>命中</th>
                </tr>
              </thead>
              <tbody>
                {shadowLogs.map((log) => (
                  <tr key={log.id}>
                    <td>{log.shadowType}</td>
                    <td>{log.userValue ?? '—'}</td>
                    <td>{log.systemValue ?? '—'}</td>
                    <td>
                      <Badge variant={log.hit ? 'success' : 'muted'}>
                        {log.hit == null ? '—' : log.hit ? '命中' : '未命中'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
