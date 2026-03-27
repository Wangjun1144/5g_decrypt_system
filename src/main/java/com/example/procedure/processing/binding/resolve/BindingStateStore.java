package com.example.procedure.processing.binding.resolve;

/**
 * Storage boundary for resolved UE binding indexes.
 */
public interface BindingStateStore {

    String lookupUeIdByNgapId(String ngapId);

    String lookupUeIdByRntiType(String rntiType);

    boolean isUeNgapUnbound(String ueId);

    boolean isUeRntiUnbound(String ueId);

    boolean isNgapUnbound(String ngapId);

    boolean isRntiTypeUnbound(String rntiType);

    void bindNgapIdToUe(String ngapId, String ueId);

    void bindRntiTypeToUe(String rntiType, String ueId);
}
