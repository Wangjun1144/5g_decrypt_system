package com.example.scene.decodersystem;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
