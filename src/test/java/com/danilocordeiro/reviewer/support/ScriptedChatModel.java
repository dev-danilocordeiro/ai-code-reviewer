package com.danilocordeiro.reviewer.support;

import com.danilocordeiro.reviewer.agent.Specialist;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A {@link ChatModel} stand-in that answers per specialist. It sits under the real ChatClient, so prompt
 * building, structured-output conversion and error handling all run as in production.
 */
public class ScriptedChatModel implements ChatModel {

    public record Call(Specialist specialist, String system, String user) {
    }

    private final Map<Specialist, Function<Call, String>> script = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public ScriptedChatModel reply(Specialist specialist, Function<Call, String> behaviour) {
        script.put(specialist, behaviour);
        return this;
    }

    public ScriptedChatModel reply(Specialist specialist, String json) {
        return reply(specialist, call -> json);
    }

    public ScriptedChatModel replyAll(String json) {
        for (Specialist s : Specialist.values()) {
            reply(s, json);
        }
        return this;
    }

    public void reset() {
        script.clear();
        calls.clear();
        replyAll("{\"findings\": []}");
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String system = text(prompt, MessageType.SYSTEM);
        String user = text(prompt, MessageType.USER);
        Specialist specialist = identify(system);
        Call call = new Call(specialist, system, user);
        calls.add(call);
        String answer = script.getOrDefault(specialist, c -> "{\"findings\": []}").apply(call);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    private static Specialist identify(String system) {
        for (Specialist s : Specialist.values()) {
            if (system.contains(s.persona())) {
                return s;
            }
        }
        throw new IllegalStateException("system prompt does not contain a known persona");
    }

    private static String text(Prompt prompt, MessageType type) {
        return prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == type)
                .map(Message::getText)
                .reduce("", (a, b) -> a + b);
    }

    public static String sleepThen(long millis, String json) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return json;
    }
}
