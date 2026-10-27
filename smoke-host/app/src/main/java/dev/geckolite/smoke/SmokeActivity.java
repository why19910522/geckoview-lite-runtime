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
    private final org.json.JSONObject evidence = new org.json.JSONObject();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.RUNTIME_PROBE) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        recordEvidence("phase", "starting");
        if (BuildConfig.EXTERNAL_CORE) {
            new Thread(() -> {
                try {
                    CoreBootstrap.prepare(this, getIntent().getBooleanExtra("installCore", false));
                    recordEvidence("prepareChecks", "passed");
                    runOnUiThread(this::startGecko);
                } catch (Exception error) {
                    Log.e("GeckoExternalProbe", "PREPARE_FAILED", error);
                    recordEvidence("prepareFailure", error.toString());
                    runOnUiThread(() -> {
                        android.widget.TextView message = new android.widget.TextView(this);
                        message.setText("内核尚未就绪：" + error.getMessage());
                        setContentView(message);
                    });
                }
            }, "core-prepare").start();
        } else {
            startGecko();
        }
    }

    private void startGecko() {
        if (isFinishing() || isDestroyed()) return;
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .isolatedProcessEnabled(getIntent().getBooleanExtra("isolatedCore", BuildConfig.ISOLATED_CORE))
                    .appZygoteProcessEnabled(getIntent().getBooleanExtra("zygoteCore", BuildConfig.ZYGOTE_CORE))
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
                    if (title != null && title.startsWith("TRIM_PROBE:")) recordEvidence("page", title.substring("TRIM_PROBE:".length()));
                }

                @Override
                public void onCrash(GeckoSession current) {
                    Log.e("GeckoTrimProbe", "CONTENT_CRASH");
                    recordEvidence("contentCrash", "true");
                }

                @Override
                public void onExternalResponse(GeckoSession current, WebResponse response) {
                    Log.i("GeckoTrimProbe", "EXTERNAL_RESPONSE:" + response.uri + " " + response.headers);
                    recordEvidence("externalContentType", response.headers.get("content-type"));
                    recordEvidence("externalContentLength", response.headers.get("content-length"));
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

    // Local probe evidence survives OEM log suppression; contains no user browsing.
    private synchronized void recordEvidence(String name, String value) {
        if (!BuildConfig.RUNTIME_PROBE) return;
        android.util.AtomicFile output = new android.util.AtomicFile(new java.io.File(getFilesDir(), "probe-evidence.json"));
        java.io.FileOutputStream stream = null;
        try {
            evidence.put(name, value);
            evidence.put("core", new org.json.JSONObject(org.mozilla.gecko.mozglue.GeckoLoader.getExternalCoreDiagnostics()));
            stream = output.startWrite();
            stream.write(evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) output.failWrite(stream);
            Log.e("GeckoExternalProbe", "Cannot save local probe evidence", error);
        }
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
