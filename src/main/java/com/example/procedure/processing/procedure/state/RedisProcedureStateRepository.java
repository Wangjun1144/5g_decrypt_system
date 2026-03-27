package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis-backed repository for active procedure state.
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class RedisProcedureStateRepository implements ProcedureStateRepository {

    private static final long REDIS_TTL_SECONDS = 3600L;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PREFIX_PROCEDURE_HASH = "procedure:hash:";
    private static final String PREFIX_UE_SET = "procedure:set:ue:";
    private static final String PREFIX_SEQ = "procedure:seq:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ProcedureStateOperationResult createActiveProcedure(
            String ueId,
            ProcedureTypeEnum typeEnum,
            String msgType
    ) {
        String procedureId = generateProcedureId(ueId, typeEnum.getCode());
        String now = LocalDateTime.now().format(FORMATTER);

        Procedure procedure = new Procedure();
        procedure.setProcedureId(procedureId);
        procedure.setUeId(ueId);
        procedure.setProcedureType(typeEnum.getDesc());
        procedure.setProcedureTypeCode(typeEnum.getCode());
        procedure.setLastMessageType(msgType);
        procedure.setActivateTime(now);
        procedure.setLastUpdateTime(now);
        procedure.setMessageNum(1);
        procedure.setLastPhaseIndex(-1);
        procedure.setLastOrderIndex(-1);

        try {
            java.util.Map<String, Object> mapObj = objectMapper.convertValue(procedure, java.util.Map.class);

            java.util.Map<String, String> mapStr = mapObj.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e -> String.valueOf(e.getValue()),
                            (v1, v2) -> v1
                    ));

            String procKey = redisKeyForProcedure(procedureId);
            String ueKey = redisKeyForUeSet(ueId);

            redisTemplate.opsForHash().putAll(procKey, mapStr);
            redisTemplate.expire(procKey, REDIS_TTL_SECONDS, TimeUnit.SECONDS);

            redisTemplate.opsForSet().add(ueKey, procedureId);
            redisTemplate.expire(ueKey, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalArgumentException | DataAccessException e) {
            log.error("Failed to add active procedure. ueId={}, code={}, msgType={}",
                    ueId, typeEnum.getCode(), msgType, e);
            return ProcedureStateOperationResult.failure(e.getMessage());
        }

        return ProcedureStateOperationResult.success(
                procedureId,
                "activateTime=" + now
        );
    }

    @Override
    public List<Procedure> listActiveProcedures(String ueId) {
        Set<String> procedureIds = redisTemplate.opsForSet().members(redisKeyForUeSet(ueId));
        if (procedureIds == null || procedureIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Procedure> result = new ArrayList<>();
        for (String pid : procedureIds) {
            Procedure procedure = findProcedure(pid);
            if (procedure != null) {
                result.add(procedure);
            }
        }
        return result;
    }

    @Override
    public ActiveProceduresView getActiveProcedures(String ueId) {
        // Query views can now be returned in typed form because callers no longer rely on legacy map payloads.
        return ActiveProceduresView.of(listActiveProcedures(ueId));
    }

    @Override
    public ProcedureStateOperationResult updateActiveProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    ) {
        String key = redisKeyForProcedure(procedureId);
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null || !exists) {
            return ProcedureStateOperationResult.failure("procedure not found");
        }

        String now = LocalDateTime.now().format(FORMATTER);

        redisTemplate.opsForHash().put(key, "lastMessageType", msgType);
        redisTemplate.opsForHash().put(key, "lastUpdateTime", now);
        redisTemplate.opsForHash().put(key, "lastPhaseIndex", String.valueOf(lastPhaseIndex));
        redisTemplate.opsForHash().put(key, "lastOrderIndex", String.valueOf(lastOrderIndex));
        redisTemplate.opsForHash().increment(key, "messageNum", 1);

        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(redisKeyForUeSet(ueId), REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        return ProcedureStateOperationResult.success(
                procedureId,
                "lastUpdateTime=" + now
        );
    }

    @Override
    public ProcedureStateOperationResult updateActiveProcedureEx(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex,
            boolean endSeen,
            long endSeenAtMs,
            int keyMask
    ) {
        String key = redisKeyForProcedure(procedureId);
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null || !exists) {
            return ProcedureStateOperationResult.failure("procedure not found");
        }

        String now = LocalDateTime.now().format(FORMATTER);

        redisTemplate.opsForHash().put(key, "lastMessageType", msgType);
        redisTemplate.opsForHash().put(key, "lastUpdateTime", now);
        redisTemplate.opsForHash().put(key, "lastPhaseIndex", String.valueOf(lastPhaseIndex));
        redisTemplate.opsForHash().put(key, "lastOrderIndex", String.valueOf(lastOrderIndex));
        redisTemplate.opsForHash().increment(key, "messageNum", 1);

        redisTemplate.opsForHash().put(key, "endSeen", String.valueOf(endSeen));
        redisTemplate.opsForHash().put(key, "endSeenAtMs", String.valueOf(endSeenAtMs));
        redisTemplate.opsForHash().put(key, "keyMask", String.valueOf(keyMask));

        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(redisKeyForUeSet(ueId), REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        return ProcedureStateOperationResult.success(
                procedureId,
                "lastUpdateTime=" + now
        );
    }

    @Override
    public Procedure findProcedure(String procedureId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(redisKeyForProcedure(procedureId));
        if (data == null || data.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(data, Procedure.class);
    }

    @Override
    public void deleteProcedure(String ueId, String procedureId) {
        redisTemplate.delete(redisKeyForProcedure(procedureId));
        redisTemplate.opsForSet().remove(redisKeyForUeSet(ueId), procedureId);
    }

    private String generateProcedureId(String ueId, String code) {
        String seqKey = redisKeyForSeq(ueId, code);

        Long seq = redisTemplate.opsForValue().increment(seqKey);
        if (seq == null) {
            seq = 1L;
        }

        redisTemplate.expire(seqKey, 7, TimeUnit.DAYS);

        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6);

        return ueId + "-" + code + "-" + seq + "-" + random;
    }

    private String redisKeyForProcedure(String procedureId) {
        return PREFIX_PROCEDURE_HASH + procedureId;
    }

    private String redisKeyForUeSet(String ueId) {
        return PREFIX_UE_SET + ueId;
    }

    private String redisKeyForSeq(String ueId, String code) {
        return PREFIX_SEQ + ueId + ":" + code;
    }
}
