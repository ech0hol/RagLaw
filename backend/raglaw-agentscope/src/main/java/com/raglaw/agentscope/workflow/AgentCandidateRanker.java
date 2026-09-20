package com.raglaw.agentscope.workflow;

import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.List;

public interface AgentCandidateRanker {
    AgentRankResult rank(RoleRequirement requirement, List<AgentVersionSnapshot> eligibleCandidates);
}
