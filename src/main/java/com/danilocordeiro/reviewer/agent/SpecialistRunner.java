package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.AgentFindings;

/** One model call: a specialist reviewing one chunk. */
@FunctionalInterface
public interface SpecialistRunner {

    AgentFindings review(Specialist specialist, Chunk chunk);
}
