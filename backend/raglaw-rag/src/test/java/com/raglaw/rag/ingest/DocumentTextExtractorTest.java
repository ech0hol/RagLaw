package com.raglaw.rag.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.raglaw.rag.ocr.DashScopeOcrClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTest {

    private DashScopeOcrClient ocrClient;
    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        ocrClient = mock(DashScopeOcrClient.class);
        extractor = new DocumentTextExtractor(ocrClient);
    }

    @Test
    void extractsPlainTextFromMd() {
        byte[] bytes = "第九十八条 法规内容".getBytes(StandardCharsets.UTF_8);
        DocumentTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(bytes),
                "statute.md"
        );
        assertEquals("第九十八条 法规内容", result.text());
        assertEquals("text", result.method());
        assertEquals(false, result.ocrUsed());
    }

    @Test
    void scannedPdfWithoutOcrThrows() throws Exception {
        when(ocrClient.extractTextFromPdf(any())).thenReturn(Optional.empty());
        byte[] blankPdf = createBlankPdf();
        assertThrows(IllegalStateException.class, () -> extractor.extract(
                new ByteArrayInputStream(blankPdf),
                "scan.pdf"
        ));
    }

    @Test
    void scannedPdfUsesOcrWhenAvailable() throws Exception {
        when(ocrClient.extractTextFromPdf(any())).thenReturn(Optional.of("OCR 识别正文"));
        byte[] blankPdf = createBlankPdf();
        DocumentTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(blankPdf),
                "scan.pdf"
        );
        assertEquals("OCR 识别正文", result.text());
        assertEquals("dashscope-ocr", result.method());
        assertTrue(result.ocrUsed());
    }

    @Test
    void imageWithoutOcrThrows() {
        when(ocrClient.extractTextFromImage(any(), any())).thenReturn(Optional.empty());
        byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertThrows(IllegalStateException.class, () -> extractor.extract(
                new ByteArrayInputStream(bytes),
                "contract.jpg"
        ));
    }

    @Test
    void imageUsesOcrWhenAvailable() {
        when(ocrClient.extractTextFromImage(any(), any())).thenReturn(Optional.of("图片 OCR 正文"));
        byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        DocumentTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(bytes),
                "contract.png"
        );
        assertEquals("图片 OCR 正文", result.text());
        assertEquals("dashscope-ocr", result.method());
        assertTrue(result.ocrUsed());
    }

    private static byte[] createBlankPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
