package com.danilocordeiro.reviewer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestChatModelConfig {

    @Bean
    ScriptedChatModel scriptedChatModel() {
        ScriptedChatModel model = new ScriptedChatModel();
        model.reset();
        return model;
    }
}
