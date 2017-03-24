LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CPPFLAGS += -fexceptions
LOCAL_LDLIBS := -llog
LOCAL_MODULE    := scanner
LOCAL_SRC_FILES := scanner.cpp


include $(BUILD_SHARED_LIBRARY)
