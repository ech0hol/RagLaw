package com.raglaw.rag.contract;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.service.IngestService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

@Service
public class ContractExportService {

    private final ContractAccessService contractAccessService;
    private final ContractTextService contractTextService;
    private final IngestService ingestService;

    public ContractExportService(
            ContractAccessService contractAccessService,
            ContractTextService contractTextService,
            IngestService ingestService
    ) {
        this.contractAccessService = contractAccessService;
        this.contractTextService = contractTextService;
        this.ingestService = ingestService;
    }

    public ExportFile export(String documentId, String format) {
        var document = contractAccessService.requireOwnedContract(documentId);
        String revised = contractTextService.buildRevisedText(documentId);
        String baseName = ingestService.resolveOriginalFilename(document).replaceFirst("\\.[^.]+$", "");
        return switch (format.toLowerCase()) {
            case "docx" -> new ExportFile(
                    baseName + "-revised.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    toDocx(revised)
            );
            case "pdf" -> new ExportFile(
                    baseName + "-revised.pdf",
                    "application/pdf",
                    toPdf(revised)
            );
            default -> throw new BusinessException(ErrorCodes.VALIDATION, "不支持的导出格式: " + format);
        };
    }

    private byte[] toDocx(String text) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : text.split("\n")) {
                XWPFParagraph p = document.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(paragraph);
                run.setFontFamily("宋体");
                run.setFontSize(11);
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "生成 DOCX 失败");
        }
    }

    private byte[] toPdf(String text) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float margin = 50;
            float fontSize = 11;
            float leading = 14;
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(margin, page.getMediaBox().getHeight() - margin);
            float y = page.getMediaBox().getHeight() - margin;
            for (String line : wrapLines(text, 90)) {
                if (y < margin) {
                    stream.endText();
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    y = page.getMediaBox().getHeight() - margin;
                    stream.newLineAtOffset(margin, y);
                }
                stream.showText(sanitizePdfText(line));
                y -= leading;
                stream.newLineAtOffset(0, -leading);
            }
            stream.endText();
            stream.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "生成 PDF 失败");
        }
    }

    private static String sanitizePdfText(String text) {
        return text.replace('\t', ' ')
                .chars()
                .filter(ch -> ch < 256)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static java.util.List<String> wrapLines(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int end = Math.min(start + width, paragraph.length());
                lines.add(paragraph.substring(start, end));
                start = end;
            }
        }
        return lines;
    }

    public record ExportFile(String filename, String contentType, byte[] bytes) {
    }
}
