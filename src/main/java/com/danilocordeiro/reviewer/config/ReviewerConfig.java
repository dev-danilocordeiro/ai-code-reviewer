package com.danilocordeiro.reviewer.config;

import com.danilocordeiro.reviewer.agent.ChatClientSpecialistRunner;
import com.danilocordeiro.reviewer.agent.CodeReviewerAgent;
import com.danilocordeiro.reviewer.agent.FindingMerger;
import com.danilocordeiro.reviewer.agent.InputChunker;
import com.danilocordeiro.reviewer.agent.SpecialistRunner;
import com.danilocordeiro.reviewer.agent.VerdictPolicy;
import com.danilocordeiro.reviewer.tools.SecretScanner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class ReviewerConfig {

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService reviewExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("reviewer-", 0).factory());
    }

    @Bean
    @ConditionalOnMissingBean
    SpecialistRunner specialistRunner(ChatClient.Builder builder, ReviewerProperties props) {
        return new ChatClientSpecialistRunner(builder.build(), props.nativeStructuredOutput());
    }

    @Bean
    CodeReviewerAgent codeReviewerAgent(SecretScanner scanner, SpecialistRunner runner,
                                        ExecutorService reviewExecutor, ReviewerProperties props) {
        return new CodeReviewerAgent(
                scanner,
                runner,
                new InputChunker(props.maxInputChars(), props.maxChunks()),
                new FindingMerger(props.titleSimilarity()),
                new VerdictPolicy(props.verdict()),
                reviewExecutor,
                props.maxConcurrentCalls(),
                props.timeout());
    }
}
