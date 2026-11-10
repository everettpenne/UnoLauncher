package com.everettpenne.unolab;

/** Receives one answer from Uno Lab's assistant as it is written. */
oneway interface IAnswerSink {
    void onPiece(String piece);
    void onDone(long ms, int tokens);
    void onError(String why);
}
