package dev.geckolite.smoke;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebResponse;

public final class SmokeActivity extends Activity {
    private static GeckoRuntime runtime;
    private GeckoSession session;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(BuildConfig.RUNTIME_PROBE)
                    .consoleOutput(BuildConfig.RUNTIME_PROBE)
                    .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }
        GeckoView view = new GeckoView(this);
        setContentView(view);
        session = new GeckoSession();
        if (BuildConfig.RUNTIME_PROBE) {
            session.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onTitleChange(GeckoSession current, String title) {
                    Log.i("GeckoTrimProbe", title);
                }

                @Override
                public void onCrash(GeckoSession current) {
                    Log.e("GeckoTrimProbe", "CONTENT_CRASH");
                }

                @Override
                public void onExternalResponse(GeckoSession current, WebResponse response) {
                    Log.i("GeckoTrimProbe", "EXTERNAL_RESPONSE:" + response.uri + " " + response.headers);
                    if (response.body != null) {
                        try {
                            response.body.close();
                        } catch (java.io.IOException error) {
                            Log.e("GeckoTrimProbe", "CLOSE_EXTERNAL_RESPONSE", error);
                        }
                    }
                }
            });
        }
        session.open(runtime);
        view.setSession(session);
        session.loadUri("resource://android/assets/" +
                (BuildConfig.RUNTIME_PROBE ? "runtime-probe.html?trimmed=" + BuildConfig.EXPECT_TRIMMED : "smoke.html"));
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
