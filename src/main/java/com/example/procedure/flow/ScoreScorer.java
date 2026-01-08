package com.example.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;
import com.example.procedure.model.SignalingMessage;

public interface ScoreScorer {
    Score score(Procedure proc, long msgTs, SignalingMessage msg);
}
