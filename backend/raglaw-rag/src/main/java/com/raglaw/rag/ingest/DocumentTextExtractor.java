package com.raglaw.rag.ingest;

import com.raglaw.rag.ocr.DashScopeOcrClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractor {

    private static final int OCR_FALLBACK_THRESHOLD = 80;

    private final DashScopeOcrClient dashScopeOcrClient;

    public DocumentTextExtractor(DashScopeOcrClient dashScopeOcrClient) {
        this.dashScopeOcrClient = dashScopeOcrClient;
    }

    public ExtractionResult extract(InputStream inputStream, String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase();
        try {
            if (lowerName.endsWith(".pdf")) {
                return extractPdf(inputStream.readAllBytes());
            }
            if (lowerName.endsWith(".md") || lowerName.endsWith(".txt")) {
                String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return new ExtractionResult(text, "text", false);
            }
            String fallback = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new ExtractionResult(fallback, "text", false);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文档失败: " + ex.getMessage(), ex);
        }
    }

    public ExtractionResult extractFromStorageKey(InputStream inputStream, String storageKey) {
        String filename = Path.of(storageKey).getFileName().toString();
        return extract(inputStream, filename);
    }

    private ExtractionResult extractPdf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            if (text.length() >= OCR_FALLBACK_THRESHOLD) {
                return new ExtractionResult(text, "pdfbox", false);
            }
            String ocrText = dashScopeOcrClient.extractTextFromPdf(pdfBytes).orElse(text);
            boolean usedOcr = !ocrText.equals(text) && ocrText.length() > text.length();
            return new ExtractionResult(
                    ocrText.isBlank() ? text : ocrText,
                    usedOcr ? "dashscope-ocr" : "pdfbox",
                    usedOcr
            );
        }
    }

    public record ExtractionResult(String text, String method, boolean ocrUsed) {
    }
}
