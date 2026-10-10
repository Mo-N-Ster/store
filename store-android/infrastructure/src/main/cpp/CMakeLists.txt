cmake_minimum_required(VERSION 3.22.1)
project(store_pdf_subject LANGUAGES CXX)

set(PDFIUM_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
if(NOT EXISTS "${PDFIUM_ROOT}/libpdfium.so")
    message(FATAL_ERROR "Qualified PDFium archive missing for ${ANDROID_ABI}")
endif()

add_library(pdfium SHARED IMPORTED GLOBAL)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION "${PDFIUM_ROOT}/libpdfium.so")

add_library(store_pdf_subject SHARED pdf_subject_jni.cpp)
target_include_directories(store_pdf_subject PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_options(store_pdf_subject PRIVATE -Wall -Wextra -Werror)
target_link_libraries(store_pdf_subject PRIVATE pdfium log)
