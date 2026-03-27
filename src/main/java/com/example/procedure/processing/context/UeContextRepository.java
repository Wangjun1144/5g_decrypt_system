package com.example.procedure.processing.context;

import com.example.procedure.model.UEContext;

/**
 * UEContext 浠撳偍杈圭晫銆?
 *
 * 褰撳墠瀹氫綅锛?
 * 1. 杩欐槸 context 棰嗗煙瀵瑰鏆撮湶鐨勬寮忎粨鍌ㄦ帴鍙?
 * 2. 涓氬姟灞傚彧渚濊禆杩欎釜鎺ュ彛锛屼笉渚濊禆鍏蜂綋 Redis/DB 瀹炵幇
 * 3. 鍏蜂綋瀹炵幇搴旀斁鍦?infrastructure.context 绛夊熀纭€璁炬柦鍖呬笅
 *
 * 杩欐牱鍋氱殑鎰忎箟锛?
 * 1. 璁?UeContextService 鍙繚鐣欎笟鍔＄骇涓婁笅鏂囨湇鍔¤亴璐?
 * 2. 璁╁瓨鍌ㄥ疄鐜颁粠 context 鍖呬腑涓嬫矇
 * 3. 涓哄悗缁垏鎹?Redis/DB/缂撳瓨缁勫悎瀹炵幇棰勭暀娓呮櫚鎵╁睍鐐?
 */
public interface UeContextRepository {

    /**
     * 鏍规嵁 UE ID 鏌ユ壘涓婁笅鏂囥€?
     *
     * @param ueId UE 鏍囪瘑
     * @return UEContext锛涘鏋滀笉瀛樺湪鍒欒繑鍥?null
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    UEContext findByUeId(String ueId);

    /**
     * 淇濆瓨涓婁笅鏂囥€?
     *
     * @param ctx 褰撳墠涓婁笅鏂?
     */
    // REFACTOR STEP: PACKAGE_REORG_INFRA_CONTEXT
    void save(UEContext ctx);
}
