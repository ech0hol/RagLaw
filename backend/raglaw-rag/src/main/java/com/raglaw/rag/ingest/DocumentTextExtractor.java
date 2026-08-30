package com.raglaw.rag.ingest;

import com.raglaw.rag.ocr.DashScopeOcrClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractor {

    private static final int OCR_FALLBACK_THRESHOLD = 80;

    private final DashScopeOcrClient dashScopeOcrClient;
    private final Tika tika = new Tika();

    public DocumentTextExtractor(DashScopeOcrClient dashScopeOcrClient) {
        this.dashScopeOcrClient = dashScopeOcrClient;
    }

    public ExtractionResult extract(InputStream inputStream, String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase();
        try {
            byte[] bytes = inputStream.readAllBytes();
            if (lowerName.endsWith(".pdf")) {
                return extractPdf(bytes);
            }
            if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                return extractImage(bytes, "image/jpeg", filename);
            }
            if (lowerName.endsWith(".png")) {
                return extractImage(bytes, "image/png", filename);
            }
            if (lowerName.endsWith(".md") || lowerName.endsWith(".txt")) {
                String text = new String(bytes, StandardCharsets.UTF_8);
                return new ExtractionResult(text, "text", false);
            }
            return extractWithTika(bytes, filename);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文档失败: " + ex.getMessage(), ex);
        }
    }

    public ExtractionResult extractFromStorageKey(InputStream inputStream, String storageKey) {
        String filename = Path.of(storageKey).getFileName().toString();
        return extract(inputStream, filename);
    }

    private ExtractionResult extractWithTika(byte[] bytes, String filename) {
        try {
            String text = tika.parseToString(new ByteArrayInputStream(bytes)).trim();
            if (text.isBlank()) {
                throw new IllegalStateException(
                        "无法从文档提取文本（" + filename + "），请检查格式或改用可编辑文档"
                );
            }
            return new ExtractionResult(text, "tika", false);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("解析文档失败: " + ex.getMessage(), ex);
        }
    }

    private ExtractionResult extractImage(byte[] imageBytes, String mimeType, String filename) {
        var ocrResult = dashScopeOcrClient.extractTextFromImage(imageBytes, mimeType);
        if (ocrResult.isPresent()) {
            String ocrText = ocrResult.get().trim();
            if (!ocrText.isBlank()) {
                return new ExtractionResult(ocrText, "dashscope-ocr", true);
            }
        }
        throw new IllegalStateException(
                "图片 OCR 失败（" + filename + "）：请配置 DASHSCOPE_API_KEY 以启用 OCR"
        );
    }

    private ExtractionResult extractPdf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            if (text.length() >= OCR_FALLBACK_THRESHOLD) {
                return new ExtractionResult(text, "pdfbox", false);
            }
            var ocrResult = dashScopeOcrClient.extractTextFromPdf(pdfBytes);
            if (ocrResult.isPresent()) {
                String ocrText = ocrResult.get().trim();
                if (!ocrText.isBlank()) {
                    return new ExtractionResult(ocrText, "dashscope-ocr", true);
                }
            }
            if (text.length() >= OCR_FALLBACK_THRESHOLD / 4) {
                return new ExtractionResult(text, "pdfbox", false);
            }
            throw new IllegalStateException(
                    "扫描版 PDF 文本提取失败：请配置 DASHSCOPE_API_KEY 以启用 OCR，或上传可搜索的 PDF"
            );
        }
    }

    public record ExtractionResult(String text, String method, boolean ocrUsed) {
    }
}
