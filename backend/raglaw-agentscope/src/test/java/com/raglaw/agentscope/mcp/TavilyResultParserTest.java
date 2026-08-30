package com.raglaw.agentscope.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TavilyResultParserTest {

    private TavilyResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new TavilyResultParser(new ObjectMapper());
    }

    @Test
    void parse_extractsResultsFromTavilyJson() {
        String raw = """
                {
                  "results": [
                    {
                      "title": "劳动法修订进展",
                      "url": "https://example.com/labor-law",
                      "content": "2026年修订草案公开征求意见。"
                    }
                  ]
                }
                """;

        List<TavilyResultParser.ParsedWebHit> hits = parser.parse(raw);

        assertEquals(1, hits.size());
        assertEquals("劳动法修订进展", hits.get(0).title());
        assertEquals("https://example.com/labor-law", hits.get(0).url());
        assertEquals("2026年修订草案公开征求意见。", hits.get(0).excerpt());
    }

    @Test
    void parse_skipsResultsWithoutUrl() {
        String raw = """
                {"results":[{"title":"无链接","content":"正文"}]}
                """;

        assertTrue(parser.parse(raw).isEmpty());
    }

    @Test
    void parse_returnsEmptyForBlankInput() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
