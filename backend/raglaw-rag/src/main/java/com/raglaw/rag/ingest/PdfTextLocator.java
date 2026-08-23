package com.raglaw.rag.ingest;

import com.raglaw.rag.dto.HighlightRect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

@Component
public class PdfTextLocator {

    public List<HighlightRect> findRects(byte[] pdfBytes, String excerpt) {
        if (pdfBytes == null || pdfBytes.length == 0 || excerpt == null || excerpt.isBlank()) {
            return List.of();
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PositionTextStripper stripper = new PositionTextStripper();
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.resetPositions();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.getText(document);
                List<HighlightRect> rects = findRectsOnPage(page, stripper.getPositions(), excerpt);
                if (!rects.isEmpty()) {
                    return rects;
                }
            }
        } catch (IOException ex) {
            return List.of();
        }
        return List.of();
    }

    private List<HighlightRect> findRectsOnPage(int page, List<TextPosition> positions, String excerpt) {
        if (positions.isEmpty()) {
            return List.of();
        }
        StringBuilder textBuilder = new StringBuilder();
        List<Integer> charToPositionIndex = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            TextPosition position = positions.get(i);
            String unicode = position.getUnicode();
            for (int charIndex = 0; charIndex < unicode.length(); charIndex++) {
                textBuilder.append(unicode.charAt(charIndex));
                charToPositionIndex.add(i);
            }
        }

        String pageText = textBuilder.toString();
        String needle = excerpt.length() > 40 ? excerpt.substring(0, 40) : excerpt;
        int start = pageText.indexOf(needle);
        if (start < 0 && excerpt.length() > needle.length()) {
            needle = excerpt;
            start = pageText.indexOf(needle);
        }
        if (start < 0) {
            return List.of();
        }

        int end = Math.min(start + needle.length(), charToPositionIndex.size());
        Set<Integer> matchedPositionIndices = new LinkedHashSet<>();
        for (int i = start; i < end; i++) {
            matchedPositionIndices.add(charToPositionIndex.get(i));
        }
        return mergeToRects(page, positions, matchedPositionIndices);
    }

    private List<HighlightRect> mergeToRects(int page, List<TextPosition> positions, Set<Integer> indices) {
        TreeMap<Integer, List<TextPosition>> lines = new TreeMap<>();
        for (int index : indices) {
            TextPosition position = positions.get(index);
            int lineKey = Math.round(position.getYDirAdj());
            lines.computeIfAbsent(lineKey, ignored -> new ArrayList<>()).add(position);
        }

        List<HighlightRect> rects = new ArrayList<>();
        for (List<TextPosition> linePositions : lines.values()) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = 0;
            float maxY = 0;
            for (TextPosition position : linePositions) {
                minX = Math.min(minX, position.getXDirAdj());
                minY = Math.min(minY, position.getYDirAdj());
                maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
                maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());
            }
            rects.add(new HighlightRect(page, minX, minY, maxX - minX, maxY - minY));
        }
        return rects;
    }

    private static final class PositionTextStripper extends PDFTextStripper {

        private final List<TextPosition> positions = new ArrayList<>();

        private PositionTextStripper() throws IOException {
        }

        private void resetPositions() {
            positions.clear();
        }

        private List<TextPosition> getPositions() {
            return List.copyOf(positions);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            positions.addAll(textPositions);
        }
    }
}
