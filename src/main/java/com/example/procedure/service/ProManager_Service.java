package com.example.procedure.service;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class ProManager_Service {

    /** Redis 中流程 Hash 的 TTL（秒），这里是 1 小时 */
    private static final long REDIS_TTL_SECONDS = 3600L;

    /** 归档文件（JSONL），一行一个流程记录 */
    private static final String ARCHIVE_FILE = "data/procedure_history.jsonl";

    /** 时间格式 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Redis key 前缀（可统一管理） */
    private static final String PREFIX_PROCEDURE_HASH = "procedure:hash:";
    private static final String PREFIX_UE_SET = "procedure:set:ue:";
    private static final String PREFIX_SEQ = "procedure:seq:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // -------------------- Redis key helper --------------------
    private String redisKeyForProcedure(String procedureId) {
        return PREFIX_PROCEDURE_HASH + procedureId;
    }

    private String redisKeyForUeSet(String ueId) {
        return PREFIX_UE_SET + ueId;
    }

    private String redisKeyForSeq(String ueId, String code) {
        return PREFIX_SEQ + ueId + ":" + code;
    }
    // -------------------- ID 生成 --------------------

    /**
     * 生成唯一的 procedureId： ueId-code-seq-random6
     */
    private String gen_ProcedureId(String ueId, String code) {
        String seqKey = redisKeyForSeq(ueId, code);

        Long seq = redisTemplate.opsForValue().increment(seqKey);
        if (seq == null) {
            // 理论上不会为 null，保险起见兜底
            seq = 1L;
        }
        // 序列过期时间为 7 天
        redisTemplate.expire(seqKey, 7, TimeUnit.DAYS);
        // 截取 UUID 去掉 '-' 后的前 6 个字符，作为随机后缀
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6);

        return ueId + "-" + code + "-" + seq + "-" + random;

    }

    // -------------------- 对外接口：活跃流程管理 --------------------

    /**
     * 新增活跃场景（流程）
     */
    public Map<String, Object> add_ActProcedure(String ueId,
                                                ProcedureTypeEnum typeEnum,
                                                String msgType) {
        String procedureId = gen_ProcedureId(ueId, typeEnum.getCode());
        String now = LocalDateTime.now().format(FORMATTER);

        Procedure procedure = new Procedure();
        procedure.setProcedureId(procedureId);
        procedure.setUeId(ueId);
        // 内部仍然保留原来的两个字符串字段，方便兼容
        procedure.setProcedureType(typeEnum.getDesc());
        procedure.setProcedureTypeCode(typeEnum.getCode());


        procedure.setLastMessageType(msgType);
        procedure.setActivateTime(now);
        procedure.setLastUpdateTime(now);
        procedure.setMessageNum(1);
        // add_ActProcedure 里删掉这两行（或设置为 -1）
        procedure.setLastPhaseIndex(-1);
        procedure.setLastOrderIndex(-1);


        try {
            // Procedure -> Map<String, Object>
            Map<String, Object> mapObj = objectMapper.convertValue(procedure, Map.class);

            // ⚡ 强制把所有 value 转成 String
            Map<String, String> mapStr = mapObj.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> String.valueOf(e.getValue()),
                            (v1, v2) -> v1   // key 冲突时保留第一个
                    ));

            String procKey = redisKeyForProcedure(procedureId);
            String ueKey = redisKeyForUeSet(ueId);

            // 写入流程 Hash
            redisTemplate.opsForHash().putAll(procKey, mapStr);
            redisTemplate.expire(procKey, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
            // 将流程 ID 加入 UE 的 Set
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
     * 获取某 UE 的活跃流程
     */
    public Map<String, Object> get_ActProcedures(String ueId) {
        List<Procedure> list = listActiveProcedures(ueId);
        return Map.of(
                "status", 0,
                "count", list.size(),
                "data", list
        );
    }

    /**
     * 新增的内部方法：直接返回强类型的 Procedure 列表
     * 方便后续“流程判别”模块使用
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
            // Redis Hash -> Procedure
            Procedure p = objectMapper.convertValue(data, Procedure.class);
            result.add(p);
        }
        return result;
    }

    /**
     * 更新活跃流程：刷最后消息类型、时间、消息数 + 刷新 TTL
     */
    public Map<String, Object> update_ActProcedure(String ueId,
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
        // 更新 Hash 字段
        redisTemplate.opsForHash().put(key, "lastMessageType", msgType);
        redisTemplate.opsForHash().put(key, "lastUpdateTime", now);
        // 这里一定要转成 String，因为 StringRedisTemplate 的 hashValueSerializer 是 StringRedisSerializer
        redisTemplate.opsForHash().put(key, "lastPhaseIndex", String.valueOf(lastPhaseIndex));
        redisTemplate.opsForHash().put(key, "lastOrderIndex", String.valueOf(lastOrderIndex));
        redisTemplate.opsForHash().increment(key, "messageNum", 1);

        // 刷新流程本身和 UE 集合的 TTL（滑动过期）
        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(redisKeyForUeSet(ueId), REDIS_TTL_SECONDS, TimeUnit.SECONDS);


        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "lastUpdateTime", now);
    }

    public Map<String, Object> update_ActProcedureEx(String ueId,
                                                     String procedureId,
                                                     String msgType,
                                                     int lastPhaseIndex,
                                                     int lastOrderIndex,
                                                     boolean endSeen,
                                                     long endSeenAtMs,
                                                     int keyMask) {
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

        // ===== 新增字段持久化（乱序结束控制）=====
        redisTemplate.opsForHash().put(key, "endSeen", String.valueOf(endSeen));
        redisTemplate.opsForHash().put(key, "endSeenAtMs", String.valueOf(endSeenAtMs));
        redisTemplate.opsForHash().put(key, "keyMask", String.valueOf(keyMask));

        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(redisKeyForUeSet(ueId), REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        return Map.of("status", 0, "procedureId", procedureId, "lastUpdateTime", now);
    }


    /**
     * 结束流程（归档到文件，并从 Redis 删除）
     */
    public Map<String, Object> end_Procedure(String ueId, String procedureId) {
        String key = redisKeyForProcedure(procedureId);
        Map<Object, Object> procedureMap = redisTemplate.opsForHash().entries(key);
        if (procedureMap == null || procedureMap.isEmpty()) {
            return Map.of("status", 1, "msg", "procedure not found");
        }
        // Map -> Procedure
        Procedure procedure = objectMapper.convertValue(procedureMap, Procedure.class);
        procedure.setEndTime(LocalDateTime.now().format(FORMATTER));

        // 确保归档目录存在
        ensureArchiveDirExists();

        // 写 JSONL 文件
        try (FileWriter writer = new FileWriter(ARCHIVE_FILE, true)) {
            writer.write(objectMapper.writeValueAsString(procedure));
            writer.write("\n");
        } catch (IOException e) {
            log.error("Failed to archive procedure. ueId={}, procedureId={}", ueId, procedureId, e);
            return Map.of("status", 1, "msg", e.getMessage());
        }
        // 从 Redis 中删除流程 & UE 的 Set 中移除
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(redisKeyForUeSet(ueId), procedureId);

        return Map.of(
                "status", 0,
                "procedureId", procedureId,
                "msg", "archived"
        );
    }

    // -------------------- 内部辅助方法 --------------------

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
