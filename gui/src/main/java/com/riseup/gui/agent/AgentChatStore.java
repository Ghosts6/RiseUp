package com.riseup.gui.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** In-memory + file-backed chat history for the RiseUp Agent page. */
public final class AgentChatStore {
    public record Message(String role, String content) {}

    public static final class Conversation {
        public String id = UUID.randomUUID().toString();
        public String title = "New chat";
        public Instant updatedAt = Instant.now();
        public List<Message> messages = new ArrayList<>();
    }

    private static final AgentChatStore INSTANCE = new AgentChatStore();
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Path STORE_PATH = Path.of(System.getProperty("user.home"), ".riseup", "agent-chats.json");

    private final List<Conversation> conversations = new ArrayList<>();
    private String activeId;

    private AgentChatStore() {
        load();
        if (conversations.isEmpty()) {
            Conversation c = new Conversation();
            conversations.add(c);
            activeId = c.id;
            save();
        } else if (activeId == null || find(activeId) == null) {
            activeId = conversations.get(0).id;
        }
    }

    public static AgentChatStore get() {
        return INSTANCE;
    }

    public synchronized List<Conversation> list() {
        return conversations.stream()
                .sorted(Comparator.comparing((Conversation c) -> c.updatedAt).reversed())
                .toList();
    }

    public synchronized Conversation active() {
        Conversation c = find(activeId);
        if (c == null) {
            c = new Conversation();
            conversations.add(c);
            activeId = c.id;
            save();
        }
        return c;
    }

    public synchronized void setActive(String id) {
        if (find(id) != null) {
            activeId = id;
            save();
        }
    }

    public synchronized Conversation startNew() {
        // Drop empty draft chats so the sidebar stays clean
        conversations.removeIf(c -> c.messages.isEmpty() && !c.id.equals(activeId));
        Conversation current = find(activeId);
        if (current != null && current.messages.isEmpty()) {
            return current;
        }
        Conversation c = new Conversation();
        conversations.add(0, c);
        activeId = c.id;
        save();
        return c;
    }

    public synchronized void touchActive(List<Message> messages) {
        Conversation c = active();
        c.messages = new ArrayList<>(messages);
        c.updatedAt = Instant.now();
        if ((c.title == null || c.title.equals("New chat")) && !messages.isEmpty()) {
            for (Message m : messages) {
                if ("user".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                    String t = m.content().trim().replace('\n', ' ');
                    c.title = t.length() > 42 ? t.substring(0, 42) + "…" : t;
                    break;
                }
            }
        }
        save();
    }

    public synchronized void delete(String id) {
        if (conversations.size() <= 1) {
            Conversation only = conversations.get(0);
            only.messages.clear();
            only.title = "New chat";
            only.updatedAt = Instant.now();
            activeId = only.id;
            save();
            return;
        }
        conversations.removeIf(c -> c.id.equals(id));
        if (id.equals(activeId)) {
            activeId = conversations.get(0).id;
        }
        save();
    }

    private Conversation find(String id) {
        if (id == null) {
            return null;
        }
        for (Conversation c : conversations) {
            if (id.equals(c.id)) {
                return c;
            }
        }
        return null;
    }

    private void load() {
        try {
            if (!Files.isRegularFile(STORE_PATH)) {
                return;
            }
            PersistFile file = JSON.readValue(STORE_PATH.toFile(), PersistFile.class);
            if (file != null && file.conversations != null) {
                conversations.clear();
                conversations.addAll(file.conversations);
                activeId = file.activeId;
            }
        } catch (Exception ignored) {
            conversations.clear();
        }
    }

    private void save() {
        try {
            Files.createDirectories(STORE_PATH.getParent());
            PersistFile file = new PersistFile();
            file.activeId = activeId;
            file.conversations = new ArrayList<>(conversations);
            JSON.writerWithDefaultPrettyPrinter().writeValue(STORE_PATH.toFile(), file);
        } catch (Exception ignored) {
        }
    }

    private static final class PersistFile {
        public String activeId;
        public List<Conversation> conversations = new ArrayList<>();
    }
}
