package com.example.procedure.processing.context.event;

/**
 * UEContext 鏇存柊浜嬩欢鍙戝竷杈圭晫銆?
 *
 * 褰撳墠闃舵锛?
 * 1. 鍏堝湪鍗曚綋鍐呮敹鍙ｄ笂涓嬫枃鏇存柊浜嬩欢鍙戝竷鍔ㄤ綔
 * 2. 榛樿瀹炵幇浠嶇劧鏄棩蹇楀彂甯冿紝涓嶅紩鍏ュ紓姝ュ鏉傚害
 */
public interface UeContextUpdatedEventPublisher {

    /**
     * 鍙戝竷涓€鏉?UEContext 鏇存柊浜嬩欢銆?
     *
     * @param event UEContext 鏇存柊浜嬩欢
     */
    void publish(UeContextUpdatedEvent event);
}
