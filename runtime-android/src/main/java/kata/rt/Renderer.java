package kata.rt;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Shows Ui values as Views. One component is one View. After the first render only what changed is
 * touched (spec 05 #reconcile), so focus, cursor and scroll positions survive a re-render.
 */
final class Renderer implements Host<View> {
    private final KataActivity activity;
    private final FrameLayout container;
    private final Reconciler<View> reconciler = new Reconciler<View>(this);
    private final float density;
    private Ui current;
    private View rootView;
    /** True while the renderer itself is changing an EditText, so that the change is not reported. */
    private boolean settingText;

    Renderer(KataActivity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    void render(Ui ui) {
        UiValidator.validate(ui);
        if (current == null) {
            rootView = reconciler.mount(ui);
            container.addView(rootView);
        } else {
            View patched = reconciler.patch(rootView, current, ui);
            if (patched != rootView) {
                container.removeAllViews();
                container.addView(patched);
                rootView = patched;
            }
        }
        current = ui;
        layout(rootView, ui, Layout.Parent.OTHER, Layout.Dim.WRAP, Layout.Dim.WRAP, Layout.Gravity.TOP_START);
    }

    // ---- Host

    @Override
    public View create(Ui node) {
        View view = newView(node);
        bind(view, node);
        return view;
    }

    @Override
    public void update(View view, Ui oldNode, Ui newNode) {
        bind(view, newNode);
    }

    @Override
    public void insertChild(View parent, int index, View child) {
        ((ViewGroup) parent).addView(child, index);
    }

    @Override
    public void removeChild(View parent, int index) {
        ((ViewGroup) parent).removeViewAt(index);
    }

    @Override
    public void replaceChild(View parent, int index, View child) {
        ViewGroup group = (ViewGroup) parent;
        group.removeViewAt(index);
        group.addView(child, index);
    }

    @Override
    public View childAt(View parent, int index) {
        return ((ViewGroup) parent).getChildAt(index);
    }

    // ---- views

    private View newView(Ui node) {
        if (node instanceof Column || node instanceof Row) {
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(node instanceof Column ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            return layout;
        }
        if (node instanceof Text || node instanceof StyledText) {
            return new TextView(activity);
        }
        if (node instanceof Button || node instanceof StyledButton) {
            android.widget.Button button = new android.widget.Button(activity);
            button.setAllCaps(false);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // The tag always holds the component of the latest render.
                    Object tag = view.getTag();
                    Action action = tag instanceof Button ? ((Button) tag).onTap : ((StyledButton) tag).onTap;
                    action.run();
                    activity.rerender();
                }
            });
            return button;
        }
        if (node instanceof TextField) {
            return newTextField();
        }
        if (node instanceof Scroll) {
            return new ScrollView(activity);
        }
        if (node instanceof HorizontalScroll) {
            return new HorizontalScrollView(activity);
        }
        if (node instanceof Space) {
            return new View(activity);
        }
        if (UiNodes.child(node) != null) {
            return new FrameLayout(activity);
        }
        throw new IllegalStateException("unknown ui component: " + node.getClass().getName());
    }

    private EditText newTextField() {
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                if (settingText) {
                    return;
                }
                ((TextField) field.getTag()).onChange.run(text.toString());
                activity.rerender();
            }
        });
        return field;
    }

    /** Applies the component's own properties to its view. Sizes and placement are set by layout(). */
    private void bind(View view, Ui node) {
        view.setTag(node);
        if (node instanceof Text) {
            setText((TextView) view, ((Text) node).text);
        } else if (node instanceof StyledText) {
            StyledText styled = (StyledText) node;
            TextView text = (TextView) view;
            setText(text, styled.text);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, styled.size);
            text.setTextColor(argb(styled.color));
        } else if (node instanceof Button) {
            setText((TextView) view, ((Button) node).label);
        } else if (node instanceof StyledButton) {
            StyledButton styled = (StyledButton) node;
            android.widget.Button button = (android.widget.Button) view;
            setText(button, styled.label);
            button.setTextColor(argb(styled.textColor));
            button.setBackgroundColor(argb(styled.background));
        } else if (node instanceof TextField) {
            TextField field = (TextField) node;
            EditText edit = (EditText) view;
            if (!field.hint.contentEquals(edit.getHint() == null ? "" : edit.getHint())) {
                edit.setHint(field.hint);
            }
            if (!edit.getText().toString().equals(field.value)) {
                settingText = true;
                try {
                    edit.setText(field.value);
                    edit.setSelection(edit.getText().length());
                } finally {
                    settingText = false;
                }
            }
        } else if (node instanceof Padding) {
            int all = px(((Padding) node).all);
            view.setPadding(all, all, all, all);
        } else if (node instanceof PaddingEdges) {
            PaddingEdges edges = (PaddingEdges) node;
            view.setPadding(px(edges.left), px(edges.top), px(edges.right), px(edges.bottom));
        } else if (node instanceof Background) {
            view.setBackgroundColor(argb(((Background) node).color));
        }
    }

    private static void setText(TextView view, String text) {
        if (!text.contentEquals(view.getText())) {
            view.setText(text);
        }
    }

    private static int argb(Color color) {
        return android.graphics.Color.argb((int) color.alpha, (int) color.red, (int) color.green, (int) color.blue);
    }

    private int px(long dp) {
        return (int) (dp * density + 0.5f);
    }

    // ---- sizes and placement

    /** Walks the Ui tree and the View tree together and sets every view's LayoutParams from Layout.spec. */
    private void layout(View view, Ui node, Layout.Parent parent, Layout.Dim inheritedWidth,
            Layout.Dim inheritedHeight, Layout.Gravity gravity) {
        Layout.Spec spec = Layout.spec(node, parent, inheritedWidth, inheritedHeight);
        int width = size(spec.width);
        int height = size(spec.height);
        ViewGroup.LayoutParams old = view.getLayoutParams();
        if (view.getParent() instanceof LinearLayout) {
            float weight = spec.weighted ? 1f : 0f;
            boolean same = old instanceof LinearLayout.LayoutParams && old.width == width && old.height == height
                    && ((LinearLayout.LayoutParams) old).weight == weight;
            if (!same) {
                view.setLayoutParams(new LinearLayout.LayoutParams(width, height, weight));
            }
        } else {
            int androidGravity = gravity(gravity);
            boolean same = old instanceof FrameLayout.LayoutParams && old.width == width && old.height == height
                    && ((FrameLayout.LayoutParams) old).gravity == androidGravity;
            if (!same) {
                view.setLayoutParams(new FrameLayout.LayoutParams(width, height, androidGravity));
            }
        }
        List<Ui> children = UiNodes.children(node);
        Layout.Parent kind = Layout.parentKind(node);
        for (int i = 0; i < children.size(); i++) {
            layout(((ViewGroup) view).getChildAt(i), children.get(i), kind, spec.childWidth, spec.childHeight,
                    spec.childGravity);
        }
    }

    private int size(Layout.Dim dim) {
        if (dim.isWrap()) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if (dim.isFill()) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        return px(dim.dp());
    }

    private static int gravity(Layout.Gravity gravity) {
        switch (gravity) {
            case CENTER:
                return Gravity.CENTER;
            case CENTER_HORIZONTAL:
                return Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            case CENTER_VERTICAL:
                return Gravity.CENTER_VERTICAL | Gravity.LEFT;
            case END:
                return Gravity.RIGHT | Gravity.TOP;
            case BOTTOM:
                return Gravity.BOTTOM | Gravity.LEFT;
            default:
                return Gravity.TOP | Gravity.LEFT;
        }
    }
}
