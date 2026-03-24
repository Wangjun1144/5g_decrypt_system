package com.example.procedure.processing.procedure;

import com.example.procedure.model.Procedure;
import com.example.procedure.model.ProcedureTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 流程状态底层存储服务。
 *
 * 当前阶段定位：
 * - 这是 procedure 状态的正式底层实现
 * - 负责活跃流程在 Redis 中的持久化，以及结束后的归档
 * - 当前仍保持现有 Redis key、TTL、JSONL 归档等行为不变
 *
 * 这样做的意义：
 * - 新主链和正式服务边界不再依赖 legacy 命名 ProManager_Service
 * - legacy service 可以退化为纯兼容层
 * - 后续如果要引入 repository / infrastructure 包，这里是自然迁移点
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcedureStateStoreService {

    /** Redis 中流程 Hash 的 TTL（秒），这里是 1 小时 */
    private static final long REDIS_TTL_SECONDS = 3600L;

    /** 归档文件（JSONL），一行一个流程记录 */
    private static final String ARCHIVE_FILE = "data/procedure_history.jsonl";

    /** 时间格式 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Redis key 前缀 */
    private static final String PREFIX_PROCEDURE_HASH = "procedure:hash:";
    private static final String PREFIX_UE_SET = "procedure:set:ue:";
    private static final String PREFIX_SEQ = "procedure:seq:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String redisKeyForProcedure(String procedureId) {
        return PREFIX_PROCEDURE_HASH + procedureId;
    }

    private String redisKeyForUeSet(String ueId) {
        return PREFIX_UE_SET + ueId;
    }

    private String redisKeyForSeq(String ueId, String code) {
        return PREFIX_SEQ + ueId + ":" + code;
    }

    /**
     * 生成唯一 procedureId：ueId-code-seq-random6
     */
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

    /**
     * 新增活跃流程。
     */
    public Map<String, Object> createActiveProcedure(
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
            Map<String, Object> mapObj = objectMapper.convertValue(procedure, Map.class);

            Map<String, String> mapStr = mapObj.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
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
            return Map.of("status", 1, "msg", e.getMessage());
        }

        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "activateTime", now
        );
    }

    /**
     * 获取某 UE 的活跃流程列表。
     */
    public List<Procedure> listActiveProcedures(String ueId) {
        Set<String> procedureIds = redisTemplate.opsForSet().members(redisKeyForUeSet(ueId));
        if (procedureIds == null || procedureIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Procedure> result = new ArrayList<>();
        for (String pid : procedureIds) {
            Map<Object, Object> data =
                    redisTemplate.opsForHash().entries(redisKeyForProcedure(pid));
            if (data == null || data.isEmpty()) {
                continue;
            }

            Procedure p = objectMapper.convertValue(data, Procedure.class);
            result.add(p);
        }
        return result;
    }

    /**
     * 获取某 UE 的活跃流程，保留旧结构。
     */
    public Map<String, Object> getActiveProcedures(String ueId) {
        List<Procedure> list = listActiveProcedures(ueId);
        return Map.of(
                "status", 0,
                "count", list.size(),
                "data", list
        );
    }

    /**
     * 更新活跃流程基础字段。
     */
    public Map<String, Object> updateActiveProcedure(
            String ueId,
            String procedureId,
            String msgType,
            int lastPhaseIndex,
            int lastOrderIndex
    ) {
        String key = redisKeyForProcedure(procedureId);
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null || !exists) {
            return Map.of("status", 1, "msg", "procedure not found");
        }

        String now = LocalDateTime.now().format(FORMATTER);

        redisTemplate.opsForHash().put(key, "lastMessageType", msgType);
        redisTemplate.opsForHash().put(key, "lastUpdateTime", now);
        redisTemplate.opsForHash().put(key, "lastPhaseIndex", String.valueOf(lastPhaseIndex));
        redisTemplate.opsForHash().put(key, "lastOrderIndex", String.valueOf(lastOrderIndex));
        redisTemplate.opsForHash().increment(key, "messageNum", 1);

        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(redisKeyForUeSet(ueId), REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "lastUpdateTime", now
        );
    }

    /**
     * 更新活跃流程扩展字段。
     */
    public Map<String, Object> updateActiveProcedureEx(
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
            return Map.of("status", 1, "msg", "procedure not found");
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

        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "lastUpdateTime", now
        );
    }

    /**
     * 结束流程：归档到文件，并从 Redis 删除。
     */
    public Map<String, Object> endProcedure(String ueId, String procedureId) {
        String key = redisKeyForProcedure(procedureId);
        Map<Object, Object> procedureMap = redisTemplate.opsForHash().entries(key);
        if (procedureMap == null || procedureMap.isEmpty()) {
            return Map.of("status", 1, "msg", "procedure not found");
        }

        Procedure procedure = objectMapper.convertValue(procedureMap, Procedure.class);
        procedure.setEndTime(LocalDateTime.now().format(FORMATTER));

        ensureArchiveDirExists();

        try (FileWriter writer = new FileWriter(ARCHIVE_FILE, true)) {
            writer.write(objectMapper.writeValueAsString(procedure));
            writer.write("\n");
        } catch (IOException e) {
            log.error("Failed to archive procedure. ueId={}, procedureId={}", ueId, procedureId, e);
            return Map.of("status", 1, "msg", e.getMessage());
        }

        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(redisKeyForUeSet(ueId), procedureId);

        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "msg", "archived"
        );
    }

    private void ensureArchiveDirExists() {
        File file = new File(ARCHIVE_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok) {
                log.warn("Failed to create archive directory: {}", parent.getAbsolutePath());
            }
        }
    }
}
