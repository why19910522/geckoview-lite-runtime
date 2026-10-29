package dev.geckolite.runtime;

/** Structured failure; no network or business UI decisions are made by the runtime. */
public final class CoreException extends Exception {
  public enum Code { CORE_NOT_INSTALLED, INVALID_PACKAGE, INVALID_SIGNATURE, CORE_INCOMPATIBLE,
    CORE_CORRUPT, CORE_LOAD_UNSUPPORTED, CORE_IN_USE, CORE_RESTART_REQUIRED, IO_ERROR }
  public final Code code;
  public CoreException(Code code, String detail) { super(detail); this.code=code; }
  public CoreException(Code code, String detail, Throwable cause) { super(detail,cause); this.code=code; }
}
