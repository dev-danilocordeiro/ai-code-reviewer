package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.AgentFindings;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** Calls whichever {@code ChatModel} Spring AI wired in (Ollama by default, Anthropic in the claude profile). */
public class ChatClientSpecialistRunner implements SpecialistRunner {

    private final ChatClient chatClient;
    private final boolean nativeStructuredOutput;

    public ChatClientSpecialistRunner(ChatClient chatClient, boolean nativeStructuredOutput) {
        this.chatClient = chatClient;
        this.nativeStructuredOutput = nativeStructuredOutput;
    }

    @Override
    public AgentFindings review(Specialist specialist, Chunk chunk) {
        AgentFindings result = chatClient.prompt()
                .messages(new SystemMessage(PromptFactory.systemPrompt(specialist)),
                        new UserMessage(PromptFactory.userPrompt(chunk)))
                .call()
                .entity(AgentFindings.class, spec -> {
                    if (nativeStructuredOutput) {
                        spec.useProviderStructuredOutput();
                    }
                });
        if (result == null) {
            throw new InvalidModelOutputException("model returned no content");
        }
        return result;
    }
}
