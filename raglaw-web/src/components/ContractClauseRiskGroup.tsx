import { ContractRiskCard } from './ContractRiskCard';
import type { ClauseRiskGroup } from '../lib/contractRiskGroups';

type ContractClauseRiskGroupProps = {
  group: ClauseRiskGroup;
  groupIndex: number;
  activeRiskId?: string | null;
  onSelectRisk: (riskId: string) => void;
  onAcceptRisk?: (riskId: string) => void;
  onUnacceptRisk?: (riskId: string) => void;
  acceptLoading?: boolean;
};

export function ContractClauseRiskGroup({
  group,
  groupIndex,
  activeRiskId,
  onSelectRisk,
  onAcceptRisk,
  onUnacceptRisk,
  acceptLoading,
}: ContractClauseRiskGroupProps) {
  return (
    <section className="rl-contract-clause-group">
      <header className="rl-contract-clause-group__header" title={group.content}>
        <span className="rl-contract-clause-group__index">条款 {groupIndex + 1}</span>
        <p className="rl-contract-clause-group__text">{group.content}</p>
      </header>
      <div className="rl-contract-clause-group__risks">
        {group.risks.map((risk) => (
          <ContractRiskCard
            key={risk.id}
            risk={risk}
            active={activeRiskId === risk.id}
            onSelect={() => onSelectRisk(risk.id)}
            onAccept={onAcceptRisk}
            onUnaccept={onUnacceptRisk}
            acceptLoading={acceptLoading}
          />
        ))}
      </div>
    </section>
  );
}
