package com.raglaw.rag.contract;

public final class RevisionCalculator {

    private RevisionCalculator() {
    }

    public static String compute(String chunkContent, String excerpt, String suggestion) {
        if (chunkContent == null) {
            chunkContent = "";
        }
        if (excerpt == null || excerpt.isBlank()) {
            return appendSuggestion(chunkContent, suggestion);
        }
        int index = chunkContent.indexOf(excerpt);
        if (index >= 0) {
            return chunkContent.substring(0, index)
                    + (suggestion != null ? suggestion : "")
                    + chunkContent.substring(index + excerpt.length());
        }
        return appendSuggestion(chunkContent, suggestion);
    }

    private static String appendSuggestion(String chunkContent, String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return chunkContent;
        }
        return chunkContent + "\n【建议】" + suggestion;
    }
}
