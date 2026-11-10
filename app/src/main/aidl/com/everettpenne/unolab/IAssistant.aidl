package com.everettpenne.unolab;

import com.everettpenne.unolab.IAnswerSink;

/**
 * Uno Lab's on-device assistant, for Uno Launcher. Only callers signed with Uno's key are served.
 * state(): 0 no model, 1 loading, 2 ready, 3 failed.
 * ask() mode: 0 ANSWER (a short reply, streamed), 1 ACTION (exactly one JSON object; see Assistant.GRAMMAR).
 * A new ask() replaces one still running.
 */
interface IAssistant {
    int state();
    void warm();
    void ask(String query, int mode, IAnswerSink sink);
    void cancel();
}
