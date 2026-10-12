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
        Device.install(new Device.Backend() {
            @Override
            public void openUrl(String url) {
                // The address was already parsed and rewritten by Device.normalize; its scheme is checked
                // once more as Android reads it, so the two parsers cannot disagree about what it is.
                android.net.Uri uri = android.net.Uri.parse(url);
                String scheme = uri.getScheme();
                boolean web = "http".equals(scheme) || "https".equals(scheme);
                if (!web && !"mailto".equals(scheme) && !"tel".equals(scheme)) {
                    return;
                }
                android.content.Intent view = new android.content.Intent(
                        web ? android.content.Intent.ACTION_VIEW
                                : "tel".equals(scheme) ? android.content.Intent.ACTION_DIAL
                                : android.content.Intent.ACTION_SENDTO, uri);
                if (web) {
                    // Only apps that declare themselves as handlers of links from other apps.
                    view.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
                }
                try {
                    startActivity(view);
                } catch (android.content.ActivityNotFoundException noAppForIt) {
                    // Nothing on the device can open it; there is nothing to do.
                }
            }

            @Override
            public void share(String text) {
                android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(android.content.Intent.EXTRA_TEXT, text);
                startActivity(android.content.Intent.createChooser(send, null));
            }

            @Override
            public void copy(String text) {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", text));
            }

            @Override
            public String clipboard() {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = clipboard.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) {
                    return "";
                }
                // Only text that was copied as text. A clip can instead hold a content: address, and
                // turning that into text would make this app open it with its own permissions.
                CharSequence text = clip.getItemAt(0).getText();
                return text == null ? "" : text.toString();
            }

            @Override
            public long screenWidth() {
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                return (long) (metrics.widthPixels / metrics.density);
            }

            @Override
            public long screenHeight() {
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                return (long) (metrics.heightPixels / metrics.density);
            }

            @Override
            public boolean isDark() {
                int night = getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
