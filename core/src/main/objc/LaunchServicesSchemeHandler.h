/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler */

#ifndef _Included_ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler
#define _Included_ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler
 * Method:    setDefaultHandlerForURLScheme
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler_setDefaultHandler
  (JNIEnv *, jobject, jstring, jstring);

/*
 * Class:     ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler
 * Method:    getDefaultHandlerForURLScheme
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler_getDefaultHandler
  (JNIEnv *, jobject, jstring);

/*
 * Class:     ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler
 * Method:    getAllHandlersForURLScheme
 * Signature: (Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_ch_cyberduck_core_urlhandler_LaunchServicesSchemeHandler_getAllHandlers
  (JNIEnv *, jobject, jstring);
#ifdef __cplusplus
}
#endif
#endif
