package io.github.opencraftai.minecraftchat.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationStore {

    private final Map<UUID, Deque<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private volatile int maxConversationPairs;

    public ConversationStore(int maxConversationPairs) {
        this.maxConversationPairs = Math.max(1, maxConversationPairs);
    }

    public List<ChatMessage> getHistory(UUID playerId) {
        Deque<ChatMessage> history = conversations.get(playerId);
        if (history == null) {
            return List.of();
        }

        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void addUserMessage(UUID playerId, String message) {
        addMessage(playerId, new ChatMessage("user", message));
    }

    public void addAssistantMessage(UUID playerId, String message) {
        addMessage(playerId, new ChatMessage("assistant", message));
    }

    public void clear(UUID playerId) {
        conversations.remove(playerId);
    }

    public void setMaxConversationPairs(int maxConversationPairs) {
        this.maxConversationPairs = Math.max(1, maxConversationPairs);
        conversations.values().forEach(this::trimToLimit);
    }

    private void addMessage(UUID playerId, ChatMessage message) {
        Deque<ChatMessage> history = conversations.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(message);
            trimToLimit(history);
        }
    }

    private void trimToLimit(Deque<ChatMessage> history) {
        int maxMessages = maxConversationPairs * 2;
        synchronized (history) {
            while (history.size() > maxMessages) {
                history.removeFirst();
            }
        }
    }
}
