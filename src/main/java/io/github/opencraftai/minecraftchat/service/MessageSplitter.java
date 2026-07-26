package io.github.opencraftai.minecraftchat.service;

import java.util.ArrayList;
import java.util.List;

public final class MessageSplitter {

    private MessageSplitter() {
    }

    public static List<String> split(String message, int maxChars) {
        String normalized = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return List.of("I do not have a reply yet.");
        }

        int safeLimit = Math.max(40, maxChars);
        if (normalized.length() <= safeLimit) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        String remaining = normalized;
        while (remaining.length() > safeLimit) {
            int splitIndex = findSplitIndex(remaining, safeLimit);
            chunks.add(remaining.substring(0, splitIndex).trim());
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private static int findSplitIndex(String text, int maxChars) {
        String preferredBreaks = ".!?;,: ";
        for (int index = maxChars; index >= Math.max(20, maxChars / 2); index--) {
            if (preferredBreaks.indexOf(text.charAt(index - 1)) >= 0) {
                return index;
            }
        }

        return maxChars;
    }
}
