package com.example.procedure.processing.context;

import com.example.procedure.processing.result.ResultMetadata;
import com.example.procedure.processing.result.ResultStatus;

/**
 * UEContext 鏇存柊缁撴灉銆?
 *
 * 褰撳墠鐢ㄩ€旓細
 * 1. 璁╀笂涓嬫枃鏇存柊閾惧叿澶囨寮忚緭鍑虹粨鏋?
 * 2. 璁╀笂灞傜煡閬撹繖娆℃槸鍚﹀垱寤轰簡鏂颁笂涓嬫枃銆佹槸鍚﹀疄闄呮墽琛屼簡鏇存柊
 * 3. 涓哄悗缁璁°€佸紓姝ヨˉ鍋裤€佺粨鏋滄眹鎬绘彁渚涚ǔ瀹氱粨鏋滄ā鍨?
 */
public class UeContextUpdateResult {

    /**
     * 褰撳墠 UE ID銆?
     */
    private final String ueId;

    /**
     * 鏄惁鍒涘缓浜嗘柊鐨勪笂涓嬫枃銆?
     */
    private final boolean created;

    /**
     * 鏄惁鎵ц浜嗘洿鏂般€?
     */
    private final boolean updated;

    /**
     * 褰撳墠娴佺▼ ID銆?
     */
    private final String procedureId;

    /**
     * 缁撴灉璇存槑銆?
     */
    private final String message;

    /**
     * 鏋勯€?UEContext 鏇存柊缁撴灉銆?
     *
     * @param ueId 褰撳墠 UE ID
     * @param created 鏄惁鏂板缓
     * @param updated 鏄惁宸叉洿鏂?
     * @param procedureId 褰撳墠娴佺▼ ID
     * @param message 缁撴灉璇存槑
     */
    public UeContextUpdateResult(
            String ueId,
            boolean created,
            boolean updated,
            String procedureId,
            String message
    ) {
        this.ueId = ueId;
        this.created = created;
        this.updated = updated;
        this.procedureId = procedureId;
        this.message = message;
    }

    /**
     * 鏋勯€犫€滆烦杩囨洿鏂扳€濈粨鏋溿€?
     *
     * @param ueId UE ID
     * @param procedureId 娴佺▼ ID
     * @param message 缁撴灉璇存槑
     * @return 鏇存柊缁撴灉
     */
    public static UeContextUpdateResult skipped(String ueId, String procedureId, String message) {
        return new UeContextUpdateResult(ueId, false, false, procedureId, message);
    }

    /**
     * 鏋勯€犫€滃凡鏇存柊鈥濈粨鏋溿€?
     *
     * @param ueId UE ID
     * @param created 鏄惁鏂板缓
     * @param procedureId 娴佺▼ ID
     * @param message 缁撴灉璇存槑
     * @return 鏇存柊缁撴灉
     */
    public static UeContextUpdateResult updated(String ueId, boolean created, String procedureId, String message) {
        return new UeContextUpdateResult(ueId, created, true, procedureId, message);
    }

    /**
     * 鑾峰彇 UE ID銆?
     *
     * @return UE ID
     */
    public String getUeId() {
        return ueId;
    }

    /**
     * 鍒ゆ柇鏄惁鍒涘缓浜嗘柊涓婁笅鏂囥€?
     *
     * @return true 琛ㄧず鏂板缓
     */
    public boolean isCreated() {
        return created;
    }

    /**
     * 鍒ゆ柇鏄惁鎵ц浜嗘洿鏂般€?
     *
     * @return true 琛ㄧず宸叉洿鏂?
     */
    public boolean isUpdated() {
        return updated;
    }

    /**
     * 鑾峰彇娴佺▼ ID銆?
     *
     * @return 娴佺▼ ID
     */
    public String getProcedureId() {
        return procedureId;
    }

    /**
     * 鑾峰彇缁撴灉璇存槑銆?
     *
     * @return 缁撴灉璇存槑
     */
    public String getMessage() {
        return message;
    }

    /**
     * 杞崲鎴愮粺涓€缁撴灉鍏冩暟鎹€?
     *
     * 杩欓噷鐨勭姸鎬佽涔夋槸锛?
     * - updated=true -> SUCCESS
     * - updated=false -> SKIPPED
     *
     * @return 缁熶竴缁撴灉鍏冩暟鎹?
     */
    // REFACTOR STEP: RESULT_METADATA_CONTRACT
    public ResultMetadata toResultMetadata() {
        return new ResultMetadata(
                "UeContextUpdateResult",
                updated ? ResultStatus.SUCCESS : ResultStatus.SKIPPED,
                ueId,
                message
        );
    }
}
