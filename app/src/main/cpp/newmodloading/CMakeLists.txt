cmake_minimum_required(VERSION 3.22)
project(newmodloading LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(newmodloading SHARED
    src/main.cpp
)

target_include_directories(newmodloading PRIVATE 
    src
    ${CMAKE_CURRENT_SOURCE_DIR}/../preloader/src
)

find_library(log-lib log)
find_library(egl-lib EGL)

target_link_libraries(newmodloading
    PRIVATE
    preloader
    ${log-lib}
    ${egl-lib}
)
