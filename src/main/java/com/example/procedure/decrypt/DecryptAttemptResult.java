package com.example.procedure.decrypt;

public class DecryptAttemptResult {

    public enum Status { OK, SKIP, WAITING, FAILED }

    public enum WaitReason {
        WAIT_NAS_KEYS,
        WAIT_RRC_KEYS,
        WAIT_ALG,
        WAIT_PARAMS,
        UNKNOWN
    }

    private final Status status;
    private final WaitReason reason;
    private final String error;

    private DecryptAttemptResult(Status status, WaitReason reason, String error) {
        this.status = status;
        this.reason = reason;
        this.error = error;
    }

    public static DecryptAttemptResult ok() { return new DecryptAttemptResult(Status.OK, null, null); }
    public static DecryptAttemptResult skip() { return new DecryptAttemptResult(Status.SKIP, null, null); }
    public static DecryptAttemptResult waiting(WaitReason r) { return new DecryptAttemptResult(Status.WAITING, r, null); }
    public static DecryptAttemptResult failed(String e) { return new DecryptAttemptResult(Status.FAILED, null, e); }

    public Status getStatus() { return status; }
    public WaitReason getReason() { return reason; }
    public String getError() { return error; }
}