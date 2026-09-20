import { describe, expect, it } from 'vitest';
import { canDisablePublishedVersion } from './AgentsAdminPage';

describe('expert lifecycle controls', () => {
  it('only enables disable for a published version', () => {
    expect(canDisablePublishedVersion({ agentCode: 'LABOR', version: 2, status: 'PUBLISHED', evaluationScore: 0.95, configChecksum: 'sha256:x' })).toBe(true);
    expect(canDisablePublishedVersion({ agentCode: 'LABOR', version: 2, status: 'SHADOW', evaluationScore: 0.95, configChecksum: 'sha256:x' })).toBe(false);
    expect(canDisablePublishedVersion(undefined)).toBe(false);
  });
});
