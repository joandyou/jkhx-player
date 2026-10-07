cmake_minimum_required(VERSION 3.22.1)
project("quickjs-ng")

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_definitions(-DCONFIG_VERSION="2024-02-14")
add_definitions(-D_GNU_SOURCE)

include_directories(quickjs)

set(QUICKJS_SOURCES
    quickjs/quickjs.c
    quickjs/libregexp.c
    quickjs/libunicode.c
    quickjs/dtoa.c
    quickjs_jni.c
)

add_library(quickjs-ng SHARED ${QUICKJS_SOURCES})

find_library(log-lib log)

target_link_libraries(quickjs-ng
    ${log-lib}
    m
)
