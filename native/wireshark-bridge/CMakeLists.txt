cmake_minimum_required(VERSION 3.20)
project(wireshark_native_bridge C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(wireshark_native_bridge SHARED
    src/ws_native_bridge_stub.c
)

set_target_properties(wireshark_native_bridge PROPERTIES
    PREFIX ""
    OUTPUT_NAME "wireshark_native_bridge"
)

target_include_directories(wireshark_native_bridge
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_definitions(wireshark_native_bridge
    PRIVATE
        WS_NATIVE_BRIDGE_STUB=1
)

find_package(JNI)

set(WS_NATIVE_JNI_INCLUDE_DIRS "")
if(JNI_FOUND)
    set(WS_NATIVE_JNI_INCLUDE_DIRS ${JNI_INCLUDE_DIRS})
elseif(DEFINED WS_NATIVE_JAVA_HOME)
    file(TO_CMAKE_PATH "${WS_NATIVE_JAVA_HOME}" WS_NATIVE_JAVA_HOME_CMAKE)
    set(WS_NATIVE_JNI_INCLUDE_DIRS
        "${WS_NATIVE_JAVA_HOME_CMAKE}/include"
        "${WS_NATIVE_JAVA_HOME_CMAKE}/include/win32"
    )
elseif(DEFINED ENV{JAVA_HOME})
    file(TO_CMAKE_PATH "$ENV{JAVA_HOME}" WS_NATIVE_JAVA_HOME_CMAKE)
    set(WS_NATIVE_JNI_INCLUDE_DIRS
        "${WS_NATIVE_JAVA_HOME_CMAKE}/include"
        "${WS_NATIVE_JAVA_HOME_CMAKE}/include/win32"
    )
endif()

if(WS_NATIVE_JNI_INCLUDE_DIRS)
    add_library(wireshark_native_bridge_jni SHARED
        src/ws_native_bridge_jni_stub.c
    )

    set_target_properties(wireshark_native_bridge_jni PROPERTIES
        PREFIX ""
        OUTPUT_NAME "wireshark_native_bridge_jni"
    )

    target_include_directories(wireshark_native_bridge_jni
        PRIVATE
            ${WS_NATIVE_JNI_INCLUDE_DIRS}
            ${CMAKE_CURRENT_SOURCE_DIR}/include
    )

    target_link_libraries(wireshark_native_bridge_jni
        PRIVATE
            wireshark_native_bridge
    )
endif()
