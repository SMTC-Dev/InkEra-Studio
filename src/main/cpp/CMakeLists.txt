cmake_minimum_required(VERSION 3.10)

project(InkEraEngine)

# C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# Zlib - InkPage katman sıkıştırması
find_package(ZLIB REQUIRED)

# Shared library
add_library(
    inkera_engine
    SHARED
    InkEraEngine.cpp
)

# Maksimum performans
target_compile_options(
    inkera_engine
    PRIVATE
    -O3
    -ffast-math
)

# Zlib bağlantısı
target_link_libraries(
    inkera_engine
    PRIVATE
    ZLIB::ZLIB
)
