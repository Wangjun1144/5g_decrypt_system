package com.example.scene.decodersystem;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture guard rails for the layered monolith boundary.
 */
@AnalyzeClasses(
        packages = "com.example.procedure",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTests {

    @ArchTest
    static final ArchRule no_active_code_should_depend_on_legacy_packages =
            noClasses()
                    .that().resideOutsideOfPackage("..legacy..")
                    .should().dependOnClassesThat().resideInAnyPackage("..legacy..");

    @ArchTest
    static final ArchRule application_layer_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    @ArchTest
    static final ArchRule processing_layer_should_not_reach_up_into_application_layer =
            noClasses()
                    .that().resideInAnyPackage("..processing..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application..");

    @ArchTest
    static final ArchRule infrastructure_layer_should_not_depend_on_application_layer =
            noClasses()
                    .that().resideInAnyPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application..");

    @ArchTest
    static final ArchRule support_layer_should_remain_framework_neutral_and_not_depend_on_business_layers =
            noClasses()
                    .that().resideInAnyPackage("..support..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..processing..",
                            "..infrastructure.."
                    );

    @ArchTest
    static final ArchRule model_layer_should_not_depend_on_application_processing_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..model..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..processing..",
                            "..infrastructure..",
                            "..support.."
                    );

    @ArchTest
    static final ArchRule configuration_properties_should_live_under_infrastructure =
            classes()
                    .that().areAnnotatedWith(ConfigurationProperties.class)
                    .should().resideInAnyPackage("..infrastructure..");

    @ArchTest
    static final ArchRule external_process_invocation_should_be_confined_to_decode_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages("..infrastructure.decode..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(ProcessBuilder.class.getName());

    @ArchTest
    static final ArchRule native_modifier_should_be_confined_to_historical_jni_anchor =
            classes()
                    .that().doNotHaveFullyQualifiedName("com.example.procedure.keyderivation.KeyDerivationNative")
                    .should().notHaveModifier(JavaModifier.NATIVE);

    @Test
    void historical_jni_anchor_must_remain_present_and_native() {
        Class<?> anchor = assertDoesNotThrow(
                () -> Class.forName("com.example.procedure.keyderivation.KeyDerivationNative")
        );

        Method kamfFromKseaf = assertDoesNotThrow(
                () -> anchor.getDeclaredMethod("kamfFromKseaf", String.class, byte[].class, String.class)
        );
        assertTrue(
                Modifier.isNative(kamfFromKseaf.getModifiers()),
                "Historical JNI anchor must keep native method signatures."
        );
    }
}
