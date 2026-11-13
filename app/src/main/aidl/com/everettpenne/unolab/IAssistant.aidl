package com.everettpenne.unolab;

import com.everettpenne.unolab.IAnswerSink;

/**
 * Uno Lab's on-device assistant, for Uno Launcher. Only callers signed with Uno's key are served.
 * state(): 0 no model, 1 loading, 2 ready, 3 failed.
 * ask() mode: 0 ANSWER (a short reply, streamed), 1 ACTION (exactly one JSON object; see Assistant.GRAMMAR).
 * A new ask() replaces one still running.
 * version(): 1 (added with askWithContext). An older Uno Lab has no version(), and the call returns 0.
 * askWithContext(): ask() with a few lines of phone context (date, battery, alarm…) placed before the question; capped at 2,000
 * characters, used for that question only and kept nowhere. New methods go at the end, so older callers keep working.
 */
interface IAssistant {
    int state();
    void warm();
    void ask(String query, int mode, IAnswerSink sink);
    void cancel();
    int version();
    void askWithContext(String query, String context, int mode, IAnswerSink sink);

    /**
     * The agent planner: request is JSON (question, phone facts, grounding, the generated tool catalogue and GBNF grammar); the reply is
     * JSON — steps, a clarifying question, or a refusal. Returns null when Uno Lab cannot serve it.
     * version() 2 means this method exists.
     */
    String plan(String request);
}
