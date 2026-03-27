package com.example.procedure.processing.context;

import com.example.procedure.processing.context.event.UeContextUpdatedEvent;
import com.example.procedure.processing.context.event.UeContextUpdatedEventPublisher;
import com.example.procedure.processing.context.update.UeContextUpdateDispatcher;
import com.example.procedure.processing.context.update.UeContextUpdateSupport;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import org.springframework.stereotype.Service;

/**
 * UE 涓婁笅鏂囨湇鍔°€?
 *
 * 褰撳墠鑱岃矗锛?
 * 1. 鍔犺浇 UEContext
 * 2. 鍒涘缓榛樿涓婁笅鏂?
 * 3. 璋冨害 updater 浣撶郴鎵ц涓婁笅鏂囨洿鏂?
 * 4. 钀藉簱瀛樺偍
 *
 * 璁捐璇存槑锛?
 * - Redis 缁嗚妭涓嬫矇鍒板熀纭€璁炬柦瀹炵幇
 * - 鏈被淇濈暀鈥滀笟鍔＄骇涓婁笅鏂囩鐞嗏€濈殑鑱岃矗
 * - 杩欐槸鏂扮殑姝ｅ紡鏈嶅姟杈圭晫锛屼緵鏂颁富閾剧洿鎺ヤ緷璧?
 */
@Service
public class UeContextService {
    // REFACTOR STEP: CONTEXT_SUBPACKAGE_REORG

    /**
     * UEContext 姝ｅ紡瀛樺偍瀹炵幇銆?
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    private final UeContextRepository ueContextRepository;

    /**
     * 涓婁笅鏂囨洿鏂板垎鍙戝櫒銆?
     */
    private final UeContextUpdateDispatcher updateDispatcher;

    /**
     * 涓婁笅鏂囨洿鏂拌緟鍔╃粍浠躲€?
     */
    private final UeContextUpdateSupport updateSupport;

    /**
     * 涓婁笅鏂囨洿鏂颁簨浠跺彂甯冨櫒銆?
     */
    private final UeContextUpdatedEventPublisher eventPublisher;

    /**
     * 鏋勯€?UE 涓婁笅鏂囨湇鍔°€?
     *
     * 杩欓噷鐩存帴渚濊禆姝ｅ紡 Redis 瀹炵幇绫伙紝
     * 閬垮厤涓氬姟涓婚摼鍐嶅洖鍒版棫鐨勪粨鍌ㄥ懡鍚嶅３涓娿€?     *
     * @param ueContextRepository UEContext 姝ｅ紡瀛樺偍瀹炵幇
     * @param updateDispatcher 鏇存柊鍒嗗彂鍣?
     * @param updateSupport 鏇存柊杈呭姪缁勪欢
     * @param eventPublisher 涓婁笅鏂囨洿鏂颁簨浠跺彂甯冨櫒
     */
    public UeContextService(
            UeContextRepository ueContextRepository,
            UeContextUpdateDispatcher updateDispatcher,
            UeContextUpdateSupport updateSupport,
            UeContextUpdatedEventPublisher eventPublisher
    ) {
        // REFACTOR STEP: COMPAT_SHELL_PRUNE
        this.ueContextRepository = ueContextRepository;
        this.updateDispatcher = updateDispatcher;
        this.updateSupport = updateSupport;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 鍔犺浇鏌愪釜 UE 鐨勪笂涓嬫枃銆?
     *
     * @param ueId UE 鏍囪瘑
     * @return UEContext锛涘鏋滀笉瀛樺湪鍒欒繑鍥?null
     */
    public UEContext getContext(String ueId) {
        return ueContextRepository.findByUeId(ueId);
    }

    /**
     * 淇濆瓨涓婁笅鏂囥€?
     *
     * @param ctx 褰撳墠涓婁笅鏂?
     */
    public void saveContext(UEContext ctx) {
        ueContextRepository.save(ctx);
    }

    /**
     * 鑻ヤ笉瀛樺湪鍒欏垱寤洪粯璁や笂涓嬫枃銆?
     *
     * @param ueId UE 鏍囪瘑
     * @return 宸插瓨鍦ㄦ垨鏂板缓鐨勪笂涓嬫枃
     */
    public UEContext getOrCreate(String ueId) {
        UEContext ctx = getContext(ueId);
        if (ctx == null) {
            ctx = createDefaultContext(ueId);
        }
        return ctx;
    }

    /**
     * 姝ｅ紡鍏ュ彛锛氭牴鎹姹傚璞℃洿鏂?UE 涓婁笅鏂囥€?
     *
     * @param request 涓婁笅鏂囨洿鏂拌姹?
     * @return 涓婁笅鏂囨洿鏂扮粨鏋?
     */
    public UeContextUpdateResult process(UeContextUpdateRequest request) {
        SignalingMessage msg = request.getMessage();
        String ueId = msg == null ? null : msg.getUeId();

        if (ueId == null || ueId.isEmpty()) {
            UeContextUpdateResult result = UeContextUpdateResult.skipped(
                    null,
                    request.getProcedureId(),
                    "skip ue context update: ueId is empty"
            );

            publishContextEvent(request, result, "ue-context-update-skipped");
            return result;
        }

        UEContext existing = getContext(ueId);
        boolean created = existing == null;

        UEContext ctx = created ? createDefaultContext(ueId) : existing;

        updateDispatcher.dispatch(msg, ctx, request.getProcedureId(), updateSupport);
        saveContext(ctx);

        UeContextUpdateResult result = UeContextUpdateResult.updated(
                ueId,
                created,
                request.getProcedureId(),
                created ? "ue context created and updated" : "ue context updated"
        );

        publishContextEvent(request, result, "ue-context-updated");
        return result;
    }

    /**
     * 鍏煎鏃у叆鍙ｏ細鏍规嵁褰撳墠娑堟伅鍜屾祦绋嬫爣璇嗘洿鏂?UE 涓婁笅鏂囥€?
     *
     * @param msg 褰撳墠娑堟伅
     * @param procedureId 褰撳墠娴佺▼ ID
     */
    public void updateOnInitialAccess(SignalingMessage msg, String procedureId) {
        process(new UeContextUpdateRequest(
                msg,
                procedureId,
                null,
                null,
                null,
                false
        ));
    }

    /**
     * 鍒涘缓榛樿涓婁笅鏂囥€?
     *
     * @param ueId UE 鏍囪瘑
     * @return 鏂板缓鐨勯粯璁や笂涓嬫枃
     */
    private UEContext createDefaultContext(String ueId) {
        UEContext ctx = new UEContext();
        ctx.setUeId(ueId);
        ctx.setAttachState("INIT");
        return ctx;
    }

    /**
     * 鍙戝竷涓€鏉?UEContext 鏇存柊浜嬩欢銆?
     *
     * @param request 褰撳墠鏇存柊璇锋眰
     * @param result 褰撳墠鏇存柊缁撴灉
     * @param action 褰撳墠鍔ㄤ綔鍚?
     */
    private void publishContextEvent(
            UeContextUpdateRequest request,
            UeContextUpdateResult result,
            String action
    ) {
        SignalingMessage msg = request.getMessage();

        UeContextUpdatedEvent event = new UeContextUpdatedEvent(
                action,
                request.getCorrelationId(),
                result.getUeId(),
                result.getProcedureId(),
                msg == null ? null : msg.getMsgId(),
                msg == null ? null : msg.getMsgType(),
                msg == null ? null : msg.getFrameNo(),
                msg == null ? null : msg.getTimestamp(),
                request.getSourceType(),
                request.getSourceName(),
                request.isReentry(),
                result.isCreated(),
                result.isUpdated(),
                result.getMessage()
        );

        eventPublisher.publish(event);
    }
}
