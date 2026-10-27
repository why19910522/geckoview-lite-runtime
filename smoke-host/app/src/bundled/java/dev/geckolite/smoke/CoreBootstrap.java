package dev.geckolite.smoke;
/** Bundled builds have no dependency on the optional installer. */
final class CoreBootstrap {
    static void prepare(android.content.Context context, boolean install) {
        throw new IllegalStateException("Not an external host");
    }
}
