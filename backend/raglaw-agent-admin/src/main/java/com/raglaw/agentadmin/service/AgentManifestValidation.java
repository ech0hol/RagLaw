package com.raglaw.agentadmin.service;

import java.util.List;

public record AgentManifestValidation(List<String> errors, List<String> warnings) {
    public AgentManifestValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
    public boolean valid() { return errors.isEmpty(); }
}
