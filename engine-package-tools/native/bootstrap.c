/* SPDX-License-Identifier: MPL-2.0 */
#define _GNU_SOURCE
#include <errno.h>
#include <android/dlext.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/memfd.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

/* The bridge owns only this bootstrap, never another copy of the Gecko engine.
 * Linux OFD locks survive dup/Binder closes and remain until the last holder dies.
 * Use syscalls available since Linux 3.17; fail closed if the device rejects them.
 */
JNIEXPORT jint JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeReadOnly(JNIEnv* env, jclass cls, jint fd) {
  int flags = fcntl(fd, F_GETFL);
  return flags < 0 ? -errno : ((flags & O_ACCMODE) == O_RDONLY);
}

JNIEXPORT jint JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeLockLease(JNIEnv* env, jclass cls, jint fd, jboolean exclusive) {
  struct flock lock = {.l_type = exclusive ? F_WRLCK : F_RDLCK,
                       .l_whence = SEEK_SET, .l_start = 0, .l_len = 0, .l_pid = 0};
  return fcntl(fd, F_OFD_SETLK, &lock) < 0 ? errno : 0;
}

JNIEXPORT jint JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeSealed(JNIEnv* env, jclass cls, jint fd) {
  const int required = F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE;
  int seals = fcntl(fd, F_GET_SEALS);
  return seals >= 0 && (seals & required) == required;
}

JNIEXPORT jint JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeSealContainer(JNIEnv* env, jclass cls, jint input, jlong size, jstring identity) {
  if (size < 1 || size > 512LL * 1024 * 1024) return -EINVAL;
  const char* id = (*env)->GetStringUTFChars(env, identity, NULL);
  if (!id) return -EINVAL;
  char memname[96];
  int valid = strlen(id) == 64 && strspn(id, "0123456789abcdef") == 64;
  snprintf(memname, sizeof(memname), "geckolite-core-%s", valid ? id : "invalid");
  (*env)->ReleaseStringUTFChars(env, identity, id);
  if (!valid) return -EINVAL;
  int fd = (int)syscall(__NR_memfd_create, memname, MFD_CLOEXEC | MFD_ALLOW_SEALING);
  if (fd < 0) return -errno;
  int saved = 0;
  char buffer[65536];
  for (off_t offset = 0; offset < size;) {
    size_t count = (size - offset < (int64_t)sizeof(buffer)) ? (size_t)(size - offset) : sizeof(buffer);
    ssize_t n;
    do { n = pread(input, buffer, count, offset); } while (n < 0 && errno == EINTR);
    if (n <= 0) { saved = n == 0 ? EIO : errno; goto failed; }
    for (ssize_t written = 0; written < n;) {
      ssize_t w;
      do { w = pwrite(fd, buffer + written, n - written, offset + written); } while (w < 0 && errno == EINTR);
      if (w <= 0) { saved = w == 0 ? EIO : errno; goto failed; }
      written += w;
    }
    offset += n;
  }
  if (fcntl(fd, F_ADD_SEALS,
      F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0) {
    saved = errno; goto failed;
  }
  char path[64];
  snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
  int readonly = open(path, O_RDONLY | O_CLOEXEC);
  if (readonly < 0) { saved = errno; goto failed; }
  close(fd);
  return readonly;
failed:
  close(fd);
  return -saved;
}

static void throw_link(JNIEnv* env, const char* message) {
  jclass error = (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError");
  if (error) (*env)->ThrowNew(env, error, message);
}

/* RTLD_NOLOAD checks the same ClassLoader namespace as the real load. Check
 * the complete non-system closure before any engine constructor can run. The
 * embedding app must serialize other loads of these SONAMEs during bootstrap;
 * this is fail-closed validation, not a private linker namespace. */
JNIEXPORT void JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativePreflight(JNIEnv* env, jclass cls, jobjectArray libraries) {
  jsize count = (*env)->GetArrayLength(env, libraries);
  for (jsize i = 0; i < count; ++i) {
    jstring library = (*env)->GetObjectArrayElement(env, libraries, i);
    const char* name = (*env)->GetStringUTFChars(env, library, NULL);
    if (!name) return;
    void* loaded = dlopen(name, RTLD_NOW | RTLD_NOLOAD);
    (*env)->ReleaseStringUTFChars(env, library, name);
    (*env)->DeleteLocalRef(env, library);
    if (loaded) {
      dlclose(loaded);
      throw_link(env, "Engine SONAME already loaded in host namespace");
      return;
    }
  }
}

static int register_glue(JNIEnv* env, void* glue, const char* name,
                         const char* prefix, JNINativeMethod* methods, int count) {
  jclass cls = (*env)->FindClass(env, name);
  if (!cls) return -1;
  for (int i = 0; i < count; ++i) {
    char symbol[256];
    snprintf(symbol, sizeof(symbol), "Java_%s_%s", prefix, methods[i].name);
    methods[i].fnPtr = dlsym(glue, symbol);
    if (!methods[i].fnPtr) { throw_link(env, "Missing matched mozglue JNI export"); return -1; }
  }
  int result = (*env)->RegisterNatives(env, cls, methods, count);
  (*env)->DeleteLocalRef(env, cls);
  return result;
}

JNIEXPORT void JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeLoad(JNIEnv* env, jclass cls,
                                                     jint fd, jlong offset, jstring library, jstring identity, jstring export) {
  if (offset < 0 || offset % 16384) { throw_link(env, "Invalid core ELF offset"); return; }
  const char* name = (*env)->GetStringUTFChars(env, library, NULL);
  if (!name) return;
  android_dlextinfo info = {.flags = ANDROID_DLEXT_USE_LIBRARY_FD | ANDROID_DLEXT_USE_LIBRARY_FD_OFFSET,
                           .library_fd = fd, .library_fd_offset = offset};
  const char* id = (*env)->GetStringUTFChars(env, identity, NULL);
  const char* anchor = (*env)->GetStringUTFChars(env, export, NULL);
  if (!id || !anchor) {
    if (id) (*env)->ReleaseStringUTFChars(env, identity, id);
    if (anchor) (*env)->ReleaseStringUTFChars(env, export, anchor);
    (*env)->ReleaseStringUTFChars(env, library, name);
    return;
  }
  char qualified[256], fdpath[64], source[256];
  snprintf(qualified, sizeof(qualified), "/geckolite/%s/%s", id, name);
  snprintf(fdpath, sizeof(fdpath), "/proc/self/fd/%d", fd);
  ssize_t n = readlink(fdpath, source, sizeof(source)-1);
  if (n >= 0) source[n] = 0;
  void* existing = dlopen(name, RTLD_NOW | RTLD_NOLOAD);
  void* handle = NULL;
  if (existing) {
    dlclose(existing);
    throw_link(env, "Engine SONAME appeared during bootstrap");
  } else if (n < 0) {
    throw_link(env, "Cannot identify sealed core source");
  } else {
    /* A slash skips Bionic's early SONAME reuse before FD processing. */
    handle = android_dlopen_ext(qualified, RTLD_NOW | RTLD_GLOBAL, &info);
  }
  if (handle) {
    Dl_info origin;
    void* symbol = dlsym(handle, anchor);
    if (!symbol || !dladdr(symbol, &origin) || !origin.dli_fname || strcmp(source, origin.dli_fname)) {
      dlclose(handle); handle = NULL;
      throw_link(env, "Loaded native export is not from selected sealed container");
    }
  }
  (*env)->ReleaseStringUTFChars(env, identity, id);
  (*env)->ReleaseStringUTFChars(env, export, anchor);
  if (!handle && !(*env)->ExceptionCheck(env)) {
    const char* detail = dlerror();
    throw_link(env, detail ? detail : "Cannot load matched core FD");
  } else if (handle && strcmp(name, "libmozglue.so") == 0) {
    /* android_dlopen_ext does not register a library with ART. Register all
     * mozglue JNI exports from the matched handle, without a second dlopen. */
    JNINativeMethod loader[] = {
      {"putenv", "(Ljava/lang/String;)V", NULL},
      {"nativeRun", "([Ljava/lang/String;[IILjava/lang/String;)V", NULL},
      {"loadGeckoLibsNative", "()V", NULL}, {"loadSQLiteLibsNative", "()V", NULL},
      {"loadNSSLibsNative", "()V", NULL}, {"suppressCrashDialog", "()V", NULL}};
    JNINativeMethod shared[] = {{"map", "(II)J", NULL}, {"unmap", "(JI)V", NULL}};
    JNINativeMethod sample[] = {
      {"nativeReadFromDirectBuffer", "(Ljava/nio/ByteBuffer;JII)V", NULL},
      {"nativeWriteToDirectBuffer", "(JLjava/nio/ByteBuffer;II)V", NULL},
      {"nativeMemcpy", "(JJI)V", NULL}};
    if (register_glue(env, handle, "org/mozilla/gecko/mozglue/GeckoLoader",
                      "org_mozilla_gecko_mozglue_GeckoLoader", loader, 6) == 0 &&
        register_glue(env, handle, "org/mozilla/gecko/mozglue/SharedMemory",
                      "org_mozilla_gecko_mozglue_SharedMemory", shared, 2) == 0 &&
        register_glue(env, handle, "org/mozilla/gecko/media/SampleBuffer",
                      "org_mozilla_gecko_media_SampleBuffer", sample, 3) == 0) {
      setenv("MOZ_ANDROID_CORE_PRELOADED", "1", 1);
    }
  }
  (*env)->ReleaseStringUTFChars(env, library, name);
  /* Handles intentionally remain for the selected process lifetime. */
}

JNIEXPORT jint JNICALL
Java_org_mozilla_gecko_mozglue_ExternalCore_nativeReserveFd(JNIEnv* env, jclass cls, jint input, jint slot) {
  // Gecko serializes resource file URIs across processes. Reserve the same free
  // slot without dup2/dup3: never overwrite a descriptor owned by the embedding app.
  if (slot < 64 || slot > 1023) return -EINVAL;
  int fd = fcntl(input, F_DUPFD_CLOEXEC, slot);
  if (fd < 0) return -errno;
  if (fd != slot) { close(fd); return -EBUSY; }
  return fd;
}
