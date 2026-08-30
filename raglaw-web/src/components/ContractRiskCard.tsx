import { useState } from 'react';
import { ChevronDown, Copy } from 'lucide-react';
import type { ContractRisk } from '../lib/api';

type ContractRiskCardProps = {
  risk: ContractRisk;
  active: boolean;
  onSelect: () => void;
  onAccept?: (riskId: string) => void;
  onUnaccept?: (riskId: string) => void;
  acceptLoading?: boolean;
};

export function ContractRiskCard({
  risk,
  active,
  onSelect,
  onAccept,
  onUnaccept,
  acceptLoading = false,
}: ContractRiskCardProps) {
  const [expanded, setExpanded] = useState(true);
  const [compareVisible, setCompareVisible] = useState(true);

  return (
    <article
      id={`risk-card-${risk.id}`}
      className={[
        'rl-contract-risk-card',
        `rl-contract-risk-card--${risk.severity.toLowerCase()}`,
        active && 'rl-contract-risk-card--active',
        risk.accepted && 'rl-contract-risk-card--accepted',
      ].filter(Boolean).join(' ')}
      onClick={onSelect}
    >
      <header className="rl-contract-risk-card__header">
        <div className="rl-contract-risk-card__title-row">
          <h4 className="rl-contract-risk-card__title">{risk.summary}</h4>
          {risk.accepted ? (
            <span className="rl-contract-risk-card__accepted-badge">已采纳</span>
          ) : null}
        </div>
        <button
          type="button"
          className="rl-contract-risk-card__expand"
          aria-expanded={expanded}
          onClick={(e) => {
            e.stopPropagation();
            setExpanded((prev) => !prev);
          }}
        >
          <ChevronDown size={18} className={expanded ? 'rl-contract-risk-card__chevron--open' : ''} />
        </button>
      </header>
      {expanded && (
        <div className="rl-contract-risk-card__body">
          <section className="rl-contract-risk-card__section">
            <h5>风险分析</h5>
            <p>{risk.summary}</p>
          </section>
          <section className="rl-contract-risk-card__section">
            <h5>修订建议</h5>
            <p>{risk.suggestion}</p>
          </section>
          {risk.legalReferences && risk.legalReferences.length > 0 && (
            <section className="rl-contract-risk-card__section">
              <h5>法律依据</h5>
              <ul className="rl-contract-risk-card__legal-list">
                {risk.legalReferences.map((ref, index) => (
                  <li key={`${ref.title}-${index}`}>
                    <strong>[{ref.docType}]</strong> {ref.title}
                    {ref.excerpt ? <span className="rl-contract-risk-card__legal-excerpt"> — {ref.excerpt}</span> : null}
                  </li>
                ))}
              </ul>
            </section>
          )}
          {compareVisible && (
            <div className="rl-contract-risk-card__diff">
              <button type="button" className="rl-contract-risk-card__diff-badge">修改</button>
              <p className="rl-contract-risk-card__diff-old">{risk.excerpt}</p>
              <p className="rl-contract-risk-card__diff-new">{risk.suggestion}</p>
            </div>
          )}
        </div>
      )}
      <footer className="rl-contract-risk-card__footer">
        <span className="rl-contract-risk-card__tag">{risk.dimension}</span>
        <div className="rl-contract-risk-card__actions">
          {risk.accepted ? (
            <button
              type="button"
              className="rl-btn rl-btn--sm rl-contract-risk-card__accept-btn"
              disabled={acceptLoading || !onUnaccept}
              onClick={(e) => {
                e.stopPropagation();
                onUnaccept?.(risk.id);
              }}
            >
              撤销采纳
            </button>
          ) : (
            <button
              type="button"
              className="rl-btn rl-btn--primary rl-btn--sm rl-contract-risk-card__accept-btn"
              disabled={acceptLoading || !onAccept}
              onClick={(e) => {
                e.stopPropagation();
                onAccept?.(risk.id);
              }}
            >
              采纳修订
            </button>
          )}
          <label
            className="rl-contract-risk-card__compare-toggle"
            onClick={(e) => e.stopPropagation()}
          >
            <span>比对</span>
            <input
              type="checkbox"
              checked={compareVisible}
              onChange={(e) => setCompareVisible(e.target.checked)}
            />
          </label>
          <button
            type="button"
            className="rl-contract-risk-card__action"
            title="复制修订建议"
            onClick={(e) => {
              e.stopPropagation();
              void navigator.clipboard.writeText(risk.suggestion);
            }}
          >
            <Copy size={14} />
            复制
          </button>
        </div>
      </footer>
    </article>
  );
}
