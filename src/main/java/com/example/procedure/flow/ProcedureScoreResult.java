package com.example.procedure.flow;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.Score;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProcedureScoreResult {
    private final Procedure procedure;
    private final Score score;
}
