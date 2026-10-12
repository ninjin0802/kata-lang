package kata.rt;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** Hosts the root Screen and re-renders after every handled event. */
public abstract class KataActivity extends Activity {
    private Screen root;
    private Renderer renderer;
    private boolean rendering;
    private boolean renderAgain;

    protected abstract Screen createRoot();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Store values live in the app's private preferences; installed before any kata code runs.
        final android.content.SharedPreferences preferences = getSharedPreferences("kata", MODE_PRIVATE);
        Store.install(new Store.Backend() {
            @Override
            public String get(String key) {
                return preferences.getString(key, null);
            }

            @Override
            public void put(String key, String value) {
                preferences.edit().putString(key, value).apply();
            }

            @Override
            public void remove(String key) {
                preferences.edit().remove(key).apply();
            }
        });
        root = createRoot();
        FrameLayout container = new FrameLayout(this);
        container.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                } else {
                    view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                }
                return insets;
            }
        });
        setContentView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderer = new Renderer(this, container);
        rerender();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!renderer.handleBack()) {
            super.onBackPressed();
        }
    }

    /** Calls view() on the root and reflects the result. A call made while rendering runs once afterwards. */
    public final void rerender() {
        if (rendering) {
            renderAgain = true;
            return;
        }
        rendering = true;
        try {
            do {
                renderAgain = false;
                renderer.render(root.view());
            } while (renderAgain);
        } finally {
            rendering = false;
        }
    }
}
