package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.service.ProManager_Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest(classes = Application.class)
class SceneServiceTests {

    @Autowired
    private ProManager_Service proContextService;

    @Test
    void testAddAndGetScene() {
        String ueId = "460011234567890";
        // 1️⃣ 新增场景
        Map<String, Object> addResult = proContextService.add_ActProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "RRCSetupRequest"
        );
        System.out.println("Add result = " + addResult);

        // 2️⃣ 查询场景
        Map<String, Object> activeScenes = proContextService.get_ActProcedures(ueId);
        System.out.println("Active scenes = " + activeScenes);
    }

    @Test
    void testUpdateScene() {
        String ueId = "460011234567890";
        // 先新增
        Map<String, Object> addResult = proContextService.add_ActProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "NAS-AuthRequest"
        );
        String sceneId = (String) addResult.get("procedureId");

        // 再更新
        Map<String, Object> updateResult = proContextService.update_ActProcedure(
                ueId, sceneId, "SecurityModeCommand", 2 ,3
        );
        System.out.println("Update result = " + updateResult);
    }

    @Test
    void testEndScene() {
        String ueId = "460011234567890";
        Map<String, Object> addResult = proContextService.add_ActProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "NAS-AuthRequest"
        );
        String sceneId = (String) addResult.get("procedureId");

        Map<String, Object> endResult = proContextService.end_Procedure(ueId, sceneId);
        System.out.println("End result = " + endResult);
    }
}
