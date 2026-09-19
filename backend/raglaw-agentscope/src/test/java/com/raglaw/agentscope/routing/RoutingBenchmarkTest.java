package com.raglaw.agentscope.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoutingBenchmarkTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void datasetHasContractCountsAndCoverage() throws Exception {
        JsonNode root = load();
        assertEquals(150, root.path("samples").size());
        Map<String, Integer> risks = counts(root, "riskLevel");
        assertEquals(Map.of("LOW", 50, "MEDIUM", 60, "HIGH", 30, "CRITICAL", 10), risks);
        Map<String, Integer> tags = new HashMap<>();
        for (JsonNode sample : root.path("samples")) for (JsonNode tag : sample.path("tags")) tags.merge(tag.asText(), 1, Integer::sum);
        assertTrue(tags.getOrDefault("ambiguous", 0) >= 30);
        assertTrue(tags.getOrDefault("missing_material", 0) >= 20);
        assertTrue(tags.getOrDefault("explicit_correction", 0) >= 20);
        assertTrue(tags.getOrDefault("prompt_injection", 0) >= 20);
    }

    @Test
    void datasetSchemaIsStableAndFictional() throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode s : load().path("samples")) {
            assertTrue(s.path("id").asText().matches("route-\\d{3}"));
            assertTrue(ids.add(s.path("id").asText()));
            for (String field : List.of("query", "taskType", "riskLevel", "executionMode", "requiredRoles", "missingMaterials", "tags")) assertTrue(s.has(field), field);
            assertFalse(s.path("query").asText().matches(".*(张三|李四|微软|阿里|腾讯|Google|Microsoft).*") );
        }
    }

    @Test
    void handCheckedMetricsAndSafetyGates() {
        int[][] matrix = {{8, 1, 0}, {2, 7, 1}, {0, 1, 10}};
        assertEquals(0.829346, macroF1(matrix), 0.00001);
        assertEquals(0.831578, weightedF1(matrix), 0.00001);
        assertEquals(10.0 / 11.0, recall(matrix, 2), 0.00001);
        assertEquals(1, criticalFalseReleases(List.of("CRITICAL", "HIGH"), List.of("HIGH", "HIGH")));
        assertFalse(gatePassed(1, 1.0));
        assertFalse(gatePassed(0, 0.94));
        assertTrue(gatePassed(0, 0.95));
    }

    @Test
    void checkedInReportContainsProvenanceAndCandidates() throws Exception {
        Path reportPath = Path.of("../docs/evaluation/routing-eval-latest.json");
        if (!Files.exists(reportPath)) reportPath = Path.of("docs/evaluation/routing-eval-latest.json");
        if (!Files.exists(reportPath)) reportPath = Path.of("../../docs/evaluation/routing-eval-latest.json");
        assertTrue(Files.exists(reportPath), "checked-in evaluation report is missing");
        try (InputStream in = Files.newInputStream(reportPath)) {
            JsonNode report = mapper.readTree(in);
            assertTrue(report.path("datasetSha256").asText().matches("[0-9a-f]{64}"));
            assertTrue(report.path("promptVersion").isTextual());
            assertTrue(report.path("modelVersion").isTextual());
            assertTrue(report.path("policyVersion").isTextual());
            assertTrue(report.path("gitCommit").isTextual());
            assertEquals(30, report.path("holdoutSize").asInt());
            assertEquals(3, report.path("candidates").size());
            assertTrue(report.path("gatePassed").asBoolean());
        }
    }

    static double macroF1(int[][] m) { double sum = 0; for (int c = 0; c < m.length; c++) { double tp=m[c][c], fp=0, fn=0; for(int r=0;r<m.length;r++){if(r!=c)fp+=m[r][c]; if(r!=c)fn+=m[c][r];} sum += tp == 0 ? 0 : 2*tp/(2*tp+fp+fn); } return sum/m.length; }
    static double weightedF1(int[][] m) { double total=0, sum=0; for(int[] row:m) for(int x:row) total+=x; for(int c=0;c<m.length;c++){double tp=m[c][c], fp=0, fn=0, support=0; for(int r=0;r<m.length;r++){support+=m[c][r]; if(r!=c){fp+=m[r][c];fn+=m[c][r];}} sum += support*(tp==0?0:2*tp/(2*tp+fp+fn));} return sum/total; }
    static double recall(int[][] m, int c) { double tp=m[c][c], fn=0; for(int j=0;j<m.length;j++) if(j!=c) fn+=m[c][j]; return tp/(tp+fn); }
    static int criticalFalseReleases(List<String> actual, List<String> predicted) { int n=0; for(int i=0;i<actual.size();i++) if("CRITICAL".equals(actual.get(i)) && !"CRITICAL".equals(predicted.get(i))) n++; return n; }
    static boolean gatePassed(int criticalFalseRelease, double highRecall) { return criticalFalseRelease == 0 && highRecall >= 0.95; }
    private JsonNode load() throws IOException { try (InputStream in = getClass().getResourceAsStream("/routing-benchmark-v1.json")) { assertNotNull(in); return mapper.readTree(in); } }
    private static Map<String,Integer> counts(JsonNode root, String field) { Map<String,Integer> out=new TreeMap<>(); for(JsonNode n:root.path("samples")) out.merge(n.path(field).asText(),1,Integer::sum); return out; }
}
