package com.example.procedure.processing.message.decrypt;

import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;

/**
 * 鈥滄秷鎭В瀵嗛樁娈碘€濈殑缁熶竴鍏ュ彛鎺ュ彛銆?
 *
 * 璁捐鐩爣锛?
 * 1. 鎶婅В瀵嗙浉鍏宠兘鍔涗粠涓婚摼涓娊璞′负鐙珛澶勭悊闃舵銆?
 * 2. 璁╂秷鎭富閾惧崗璋冨櫒涓嶅啀鐩存帴渚濊禆鍏蜂綋瑙ｅ瘑鍗忚皟鍣ㄥ疄鐜般€? * 3. 涓哄悗缁紨杩涘埌寮傛瑙ｅ瘑銆佽繙绋嬭В瀵嗐€佷簨浠堕┍鍔ㄨВ瀵嗘祦姘寸嚎鍋氬噯澶囥€?
 *
 * 褰撳墠闃舵鐨勫鐞嗚寖鍥达細
 * - 鍒ゆ柇褰撳墠娑堟伅鏄惁闇€瑕佽В瀵?
 * - 鎵ц褰撳墠杞В瀵嗗皾璇?
 * - 鍦ㄨВ瀵嗘垚鍔熸椂鎵ц鍥炴祦
 *
 * 褰撳墠闃舵涓嶈礋璐ｏ細
 * - pending 鍏ラ槦鍐崇瓥
 * - pending 閲嶈瘯璋冨害
 * - 涓婚摼閫掑綊鎺у埗
 *
 * 杩欎簺鍐崇瓥浠嶇劧淇濈暀鍦ㄦ秷鎭富閾惧崗璋冨櫒涓紝
 * 杩欐牱鍙互淇濊瘉杩欎竴杞噸鏋勫彧鏀跺彛杈圭晫锛屼笉鏀瑰彉涓婚摼鎺у埗璇箟銆?
 */
public interface MessageDecryptStage {

    /**
     * 瀵瑰綋鍓嶆秷鎭墽琛屼竴娆♀€滃鏈夐渶瑕佸垯灏濊瘯瑙ｅ瘑鈥濈殑闃舵澶勭悊銆?
     *
     * 杩斿洖绾﹀畾淇濇寔涓庡綋鍓嶇郴缁熶竴鑷达細
     * - 杩斿洖 null锛氬綋鍓嶆秷鎭棤闇€鍦ㄦ闃舵鎻愬墠缁撴潫锛屼富閾惧彲缁х画
     * - 杩斿洖闈?null锛氳〃绀烘湰杞凡缁忓彂鐢熶簡瑙ｅ瘑鐩稿叧澶勭悊锛屼富閾鹃渶鏍规嵁鐘舵€佸喅瀹氬悗缁涓?
     */
    DecryptAttemptResult handleEncryptedMessageIfNeeded(MessageProcessingContext context);

    /**
     * 鍦ㄨВ瀵嗘垚鍔熷悗鎵ц鍥炴祦澶勭悊銆?
     *
     * 杩斿洖鍊艰涔変繚鎸佷笌褰撳墠瀹炵幇涓€鑷达細
     * - true  : 宸插彂鐢熸湁鏁堝洖娴侊紝娑堟伅搴旈噸鏂拌繘鍏ュ畬鏁翠富閾?
     * - false : 娌℃湁瀹屾垚鏈夋晥鍥炴祦锛屼富閾惧彲鎸夊綋鍓嶄笂涓嬫枃鐩存帴鏀跺彛
     */
    boolean handleDecryptSuccess(MessageProcessingContext context);
}
