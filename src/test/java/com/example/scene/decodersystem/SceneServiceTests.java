package com.example.scene.decodersystem;

import com.example.procedure.Application;
import com.example.procedure.model.ProcedureTypeEnum;
import com.example.procedure.application.procedure.ProcedureStateApplicationService;
import com.example.procedure.processing.procedure.state.ActiveProceduresView;
import com.example.procedure.processing.procedure.state.ProcedureStateOperationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest(classes = Application.class)
class SceneServiceTests {

    @Autowired
    private ProcedureStateApplicationService procedureStateApplicationService;

    @Test
    void testAddAndGetScene() {
        String ueId = "460011234567890";
        ProcedureStateOperationResult addResult = procedureStateApplicationService.createActiveProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "RRCSetupRequest"
        );
        System.out.println("Add result = " + addResult);

        ActiveProceduresView activeScenes = procedureStateApplicationService.getActiveProcedures(ueId);
        System.out.println("Active scenes = " + activeScenes);
    }

    @Test
    void testUpdateScene() {
        String ueId = "460011234567890";
        ProcedureStateOperationResult addResult = procedureStateApplicationService.createActiveProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "NAS-AuthRequest"
        );
        String sceneId = addResult.getProcedureId();

        ProcedureStateOperationResult updateResult = procedureStateApplicationService.updateActiveProcedure(
                ueId, sceneId, "SecurityModeCommand", 2, 3
        );
        System.out.println("Update result = " + updateResult);
    }

    @Test
    void testEndScene() {
        String ueId = "460011234567890";
        ProcedureStateOperationResult addResult = procedureStateApplicationService.createActiveProcedure(
                ueId, ProcedureTypeEnum.INITIAL_ACCESS, "NAS-AuthRequest"
        );
        String sceneId = addResult.getProcedureId();

        ProcedureStateOperationResult endResult = procedureStateApplicationService.endProcedure(ueId, sceneId);
        System.out.println("End result = " + endResult);
    }
}
