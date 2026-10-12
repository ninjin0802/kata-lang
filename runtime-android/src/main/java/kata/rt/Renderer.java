package kata.rt;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Turns Ui values into Views. This version rebuilds everything on each render. */
final class Renderer {
    private final KataActivity activity;
    private final FrameLayout container;

    Renderer(KataActivity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
    }

    void render(Ui ui) {
        container.removeAllViews();
        container.addView(build(ui), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    private View build(Ui ui) {
        if (ui instanceof Column) {
            return linear(LinearLayout.VERTICAL, ((Column) ui).children);
        }
        if (ui instanceof Row) {
            return linear(LinearLayout.HORIZONTAL, ((Row) ui).children);
        }
        if (ui instanceof Text) {
            TextView view = new TextView(activity);
            view.setText(((Text) ui).text);
            return view;
        }
        if (ui instanceof Button) {
            final Button button = (Button) ui;
            android.widget.Button view = new android.widget.Button(activity);
            view.setAllCaps(false);
            view.setText(button.label);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    button.onTap.run();
                    activity.rerender();
                }
            });
            return view;
        }
        throw new IllegalStateException("unknown ui component: " + ui.getClass().getName());
    }

    private View linear(int orientation, KList children) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(orientation);
        for (long i = 0; i < children.size(); i++) {
            layout.addView(build((Ui) children.get(i)));
        }
        return layout;
    }
}
