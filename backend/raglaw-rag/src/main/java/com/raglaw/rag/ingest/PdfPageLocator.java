package com.raglaw.rag.ingest;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfPageLocator {

    public Integer findPage(byte[] pdfBytes, String excerpt) {
        if (pdfBytes == null || pdfBytes.length == 0 || excerpt == null || excerpt.isBlank()) {
            return null;
        }
        String needle = excerpt.length() > 40 ? excerpt.substring(0, 40) : excerpt;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (pageText != null && pageText.contains(needle)) {
                    return page;
                }
            }
        } catch (IOException ex) {
            return null;
        }
        return null;
    }
}
