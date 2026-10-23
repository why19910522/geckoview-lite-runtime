package dev.geckolite.smoke;

import android.app.Activity;
import android.os.Bundle;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class SmokeActivity extends Activity {
    private static GeckoRuntime runtime;
    private GeckoSession session;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (runtime == null) {
            runtime = GeckoRuntime.create(getApplicationContext());
        }
        GeckoView view = new GeckoView(this);
        setContentView(view);
        session = new GeckoSession();
        session.open(runtime);
        view.setSession(session);
        session.loadUri("resource://android/assets/smoke.html");
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
