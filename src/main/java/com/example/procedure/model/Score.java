package com.example.procedure.model;

import lombok.Data;

@Data
public class Score {
    private int score;
    private int phaseIndex;
    private int orderIndex;

    public Score(int score, int phaseIndex, int orderIndex){
        this.score = score;
        this.phaseIndex = phaseIndex;
        this.orderIndex = orderIndex;
    }
}
