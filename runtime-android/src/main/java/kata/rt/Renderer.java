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
    final KataActivity activity;
    final FrameLayout container;
    private final Reconciler<View> reconciler = new Reconciler<View>(this);
    private final float density;
    private Ui current;
    private View rootView;
    /** True while the renderer itself is changing a control, so that the change is not reported. */
    boolean binding;

    Renderer(KataActivity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
        this.density = activity.getResources().getDisplayMetrics().density;
        container.setClipChildren(false);
        container.setBackgroundColor(Material.SURFACE);
    }

    /** The accent color of the current screen: the outermost Theme's, or the default. */
    int accent = Extras.DEFAULT_ACCENT;

    private static Theme findTheme(Ui node) {
        if (node instanceof Theme) {
            return (Theme) node;
        }
        for (Ui child : UiNodes.children(node)) {
            Theme found = findTheme(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    void render(Ui ui) {
        UiValidator.validate(ui);
        Theme theme = findTheme(ui);
        accent = theme == null ? Extras.DEFAULT_ACCENT : argb(theme.accent);
        if (current == null) {
            rootView = reconciler.mount(ui);
            container.addView(rootView);
        } else {
            View patched = reconciler.patch(rootView, current, ui);
            if (patched != rootView) {
                // Only the root is swapped: the container may also hold overlay bars.
                container.removeView(rootView);
                container.addView(patched, 0);
                rootView = patched;
            }
        }
        current = ui;
        layout(rootView, ui, Layout.Parent.OTHER, Layout.Dim.WRAP, Layout.Dim.WRAP, Layout.Gravity.TOP_START);
    }

    /** Runs the innermost BackHandler of the current screen. Returns false when there is none. */
    boolean handleBack() {
        BackHandler handler = current == null ? null : findBackHandler(current);
        if (handler == null) {
            return false;
        }
        handler.onBack.run();
        activity.rerender();
        return true;
    }

    private static BackHandler findBackHandler(Ui node) {
        BackHandler found = node instanceof BackHandler ? (BackHandler) node : null;
        for (Ui child : UiNodes.children(node)) {
            BackHandler inner = findBackHandler(child);
            if (inner != null) {
                found = inner;
            }
        }
        return found;
    }

    // ---- rows of a LazyList, which are small trees of their own

    View mountRow(Ui node) {
        return reconciler.mount(node);
    }

    View patchRow(View view, Ui oldNode, Ui newNode) {
        return reconciler.patch(view, oldNode, newNode);
    }

    void layoutRow(View view, Ui node) {
        layout(view, node, Layout.Parent.OTHER, Layout.Dim.FILL, Layout.Dim.WRAP, Layout.Gravity.TOP_START);
    }

    // ---- Host

    @Override
    public View create(Ui node) {
        View view = newView(node);
        // Like the root container, components do not cut off children that are drawn outside their box
        // (a scaled or rotated child, a shadow). Scrolling containers still clip to their viewport.
        if (view instanceof ViewGroup && !(view instanceof android.widget.ScrollView)
                && !(view instanceof android.widget.HorizontalScrollView)
                && !(view instanceof android.widget.AbsListView)) {
            ((ViewGroup) view).setClipChildren(false);
            ((ViewGroup) view).setClipToPadding(false);
        }
        bind(view, node);
        return view;
    }

    @Override
    public void update(View view, Ui oldNode, Ui newNode) {
        bind(view, newNode);
    }

    @Override
    public void insertChild(View parent, int index, View child) {
        Extras.childContainer(parent).addView(child, index);
    }

    @Override
    public void removeChild(View parent, int index) {
        Extras.childContainer(parent).removeViewAt(index);
    }

    @Override
    public void replaceChild(View parent, int index, View child) {
        ViewGroup group = Extras.childContainer(parent);
        group.removeViewAt(index);
        group.addView(child, index);
    }

    @Override
    public View childAt(View parent, int index) {
        return Extras.childContainer(parent).getChildAt(index);
    }

    // ---- views

    private View newView(Ui node) {
        View extra = Extras.create(this, node);
        if (extra != null) {
            return extra;
        }
        if (node instanceof Column || node instanceof Row) {
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(node instanceof Column ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            return layout;
        }
        if (node instanceof Text || node instanceof StyledText) {
            return new TextView(activity);
        }
        if (node instanceof Button || node instanceof StyledButton || node instanceof OutlinedButton
                || node instanceof TonalButton || node instanceof TextButton) {
            android.widget.Button button = new android.widget.Button(activity);
            button.setAllCaps(false);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // The tag always holds the component of the latest render.
                    Object tag = view.getTag();
                    Action action = tag instanceof Button ? ((Button) tag).onTap
                            : tag instanceof StyledButton ? ((StyledButton) tag).onTap
                            : tag instanceof OutlinedButton ? ((OutlinedButton) tag).onTap
                            : tag instanceof TonalButton ? ((TonalButton) tag).onTap : ((TextButton) tag).onTap;
                    action.run();
                    activity.rerender();
                }
            });
            return button;
        }
        if (node instanceof TextField || node instanceof TextArea || node instanceof SecretField
                || node instanceof NumberField) {
            EditText input = newTextInput(node instanceof TextArea);
            if (node instanceof SecretField) {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else if (node instanceof NumberField) {
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            return input;
        }
        if (node instanceof Checkbox || node instanceof Switch) {
            android.widget.CompoundButton toggle = node instanceof Checkbox
                    ? new android.widget.CheckBox(activity) : new android.widget.Switch(activity);
            toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton view, boolean checked) {
                    if (binding) {
                        return;
                    }
                    Object tag = view.getTag();
                    Handler handler = tag instanceof Checkbox ? ((Checkbox) tag).onChange : ((Switch) tag).onChange;
                    handler.run(Boolean.valueOf(checked));
                    activity.rerender();
                }
            });
            return toggle;
        }
        if (node instanceof Slider) {
            android.widget.SeekBar bar = new android.widget.SeekBar(activity);
            bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar view, int progress, boolean fromUser) {
                    if (binding || !fromUser) {
                        return;
                    }
                    ((Slider) view.getTag()).onChange.run(Long.valueOf(progress));
                    activity.rerender();
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar view) {}

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar view) {}
            });
            return bar;
        }
        if (node instanceof Progress) {
            return new android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        }
        if (node instanceof Loading) {
            return new android.widget.ProgressBar(activity);
        }
        if (node instanceof Divider) {
            View line = new View(activity);
            line.setBackgroundColor(Material.OUTLINE_VARIANT);
            return line;
        }
        if (node instanceof Grid) {
            return new android.widget.GridLayout(activity);
        }
        if (node instanceof Rating) {
            android.widget.RatingBar stars = new android.widget.RatingBar(activity);
            stars.setStepSize(1f);
            stars.setOnRatingBarChangeListener(new android.widget.RatingBar.OnRatingBarChangeListener() {
                @Override
                public void onRatingChanged(android.widget.RatingBar view, float rating, boolean fromUser) {
                    Rating node = (Rating) view.getTag();
                    // Reported only when it differs from what the Screen holds.
                    if (fromUser && (long) rating != node.value) {
                        node.onChange.run(Long.valueOf((long) rating));
                        activity.rerender();
                    }
                }
            });
            return stars;
        }
        if (node instanceof RadioGroup) {
            android.widget.RadioGroup group = new android.widget.RadioGroup(activity);
            group.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.RadioGroup view, int checkedId) {
                    RadioGroup node = (RadioGroup) view.getTag();
                    long index = checkedId - 1;
                    if (!binding && checkedId > 0 && index != node.selected) {
                        node.onChange.run(Long.valueOf(index));
                        activity.rerender();
                    }
                }
            });
            return group;
        }
        if (node instanceof Dropdown) {
            android.widget.Spinner spinner = new android.widget.Spinner(activity);
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    // Android also calls this for selections the renderer made, some time later;
                    // only a position that differs from the Screen's value is a change by the user.
                    Dropdown node = (Dropdown) parent.getTag();
                    if (position != node.selected) {
                        node.onChange.run(Long.valueOf(position));
                        activity.rerender();
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            return spinner;
        }
        if (node instanceof Card) {
            FrameLayout card = new FrameLayout(activity);
            android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
            surface.setColor(Material.SURFACE_CONTAINER_LOW);
            surface.setCornerRadius(px(12));
            card.setBackground(surface);
            card.setElevation(px(1));
            card.setClipToOutline(true);
            return card;
        }
        if (node instanceof Tappable) {
            FrameLayout frame = new FrameLayout(activity);
            // The platform's bounded ripple, so that a tap is visible.
            TypedValue ripple = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            frame.setForeground(activity.getDrawable(ripple.resourceId));
            frame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((Tappable) view.getTag()).onTap.run();
                    activity.rerender();
                }
            });
            return frame;
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
        if (UiNodes.child(node) != null || node instanceof Stack) {
            return new FrameLayout(activity);
        }
        throw new IllegalStateException("unknown ui component: " + node.getClass().getName());
    }

    private EditText newTextInput(boolean multiLine) {
        final EditText field = new EditText(activity);
        if (multiLine) {
            field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            field.setGravity(Gravity.TOP | Gravity.LEFT);
        } else {
            field.setSingleLine(true);
        }
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                Object tag = field.getTag();
                // No tag yet: the field is still being set up, and nothing was typed.
                if (binding || tag == null) {
                    return;
                }
                Handler handler = tag instanceof TextField ? ((TextField) tag).onChange
                        : tag instanceof TextArea ? ((TextArea) tag).onChange
                        : tag instanceof SecretField ? ((SecretField) tag).onChange : ((NumberField) tag).onChange;
                handler.run(text.toString());
                activity.rerender();
            }
        });
        return field;
    }

    /** Shows the stored text in an input, without reporting it as a change made by the user. */
    private void bindText(EditText edit, String value, String hint) {
        if (!hint.contentEquals(edit.getHint() == null ? "" : edit.getHint())) {
            edit.setHint(hint);
        }
        if (!edit.getText().toString().equals(value)) {
            binding = true;
            try {
                edit.setText(value);
                edit.setSelection(edit.getText().length());
            } finally {
                binding = false;
            }
        }
    }

    /** Applies the component's own properties to its view. Sizes and placement are set by layout(). */
    private void bind(View view, Ui node) {
        view.setTag(node);
        if (Extras.bind(this, view, node)) {
            return;
        }
        if (node instanceof Text) {
            setText((TextView) view, ((Text) node).text);
            ((TextView) view).setTextColor(Material.ON_SURFACE);
        } else if (node instanceof StyledText) {
            StyledText styled = (StyledText) node;
            TextView text = (TextView) view;
            setText(text, styled.text);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, styled.size);
            text.setTextColor(argb(styled.color));
        } else if (node instanceof Button) {
            setText((TextView) view, ((Button) node).label);
            Material.filledButton(this, (TextView) view, accent, Material.on(accent));
        } else if (node instanceof TonalButton) {
            setText((TextView) view, ((TonalButton) node).label);
            Material.filledButton(this, (TextView) view, Material.container(accent), Material.onContainer(accent));
        } else if (node instanceof OutlinedButton) {
            setText((TextView) view, ((OutlinedButton) node).label);
            Material.outlinedButton(this, (TextView) view, accent, true);
        } else if (node instanceof TextButton) {
            setText((TextView) view, ((TextButton) node).label);
            Material.outlinedButton(this, (TextView) view, accent, false);
        } else if (node instanceof StyledButton) {
            StyledButton styled = (StyledButton) node;
            android.widget.Button button = (android.widget.Button) view;
            setText(button, styled.label);
            Material.filledButton(this, button, argb(styled.background), argb(styled.textColor));
        } else if (node instanceof TextField) {
            Material.outlinedField(this, (EditText) view, accent);
            bindText((EditText) view, ((TextField) node).value, ((TextField) node).hint);
        } else if (node instanceof SecretField) {
            Material.outlinedField(this, (EditText) view, accent);
            bindText((EditText) view, ((SecretField) node).value, ((SecretField) node).hint);
        } else if (node instanceof NumberField) {
            Material.outlinedField(this, (EditText) view, accent);
            bindText((EditText) view, ((NumberField) node).value, ((NumberField) node).hint);
        } else if (node instanceof TextArea) {
            TextArea area = (TextArea) node;
            EditText edit = (EditText) view;
            Material.outlinedField(this, edit, accent);
            if (edit.getMinLines() != (int) area.lines) {
                edit.setLines((int) area.lines);
            }
            bindText(edit, area.value, area.hint);
        } else if (node instanceof Checkbox || node instanceof Switch) {
            android.widget.CompoundButton toggle = (android.widget.CompoundButton) view;
            boolean checked = node instanceof Checkbox ? ((Checkbox) node).checked : ((Switch) node).on;
            setText(toggle, node instanceof Checkbox ? ((Checkbox) node).label : ((Switch) node).label);
            if (toggle instanceof android.widget.Switch) {
                ((android.widget.Switch) toggle).setThumbTintList(Extras.checkedTint(accent));
            } else {
                toggle.setButtonTintList(Extras.checkedTint(accent));
            }
            if (toggle.isChecked() != checked) {
                binding = true;
                try {
                    toggle.setChecked(checked);
                } finally {
                    binding = false;
                }
            }
        } else if (node instanceof Slider || node instanceof Progress) {
            android.widget.ProgressBar bar = (android.widget.ProgressBar) view;
            int max = (int) Math.min(Integer.MAX_VALUE, node instanceof Slider ? ((Slider) node).max
                    : ((Progress) node).max);
            int value = (int) Math.min(Integer.MAX_VALUE, node instanceof Slider ? ((Slider) node).value
                    : ((Progress) node).value);
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(accent);
            bar.setProgressTintList(tint);
            if (bar instanceof android.widget.SeekBar) {
                ((android.widget.SeekBar) bar).setThumbTintList(tint);
            }
            binding = true;
            try {
                if (bar.getMax() != max) {
                    bar.setMax(max);
                }
                if (bar.getProgress() != value) {
                    bar.setProgress(value);
                }
            } finally {
                binding = false;
            }
        } else if (node instanceof Grid) {
            android.widget.GridLayout grid = (android.widget.GridLayout) view;
            int columns = (int) Math.min(1000, ((Grid) node).columns);
            if (grid.getColumnCount() != columns) {
                grid.setColumnCount(columns);
            }
        } else if (node instanceof Rating) {
            android.widget.RatingBar stars = (android.widget.RatingBar) view;
            Rating rating = (Rating) node;
            if (stars.getNumStars() != (int) rating.max) {
                stars.setNumStars((int) Math.min(100, rating.max));
            }
            if ((long) stars.getRating() != rating.value) {
                stars.setRating(rating.value);
            }
        } else if (node instanceof RadioGroup) {
            bindRadioGroup((android.widget.RadioGroup) view, (RadioGroup) node);
        } else if (node instanceof Dropdown) {
            bindDropdown((android.widget.Spinner) view, (Dropdown) node);
        } else if (node instanceof Border) {
            Border border = (Border) node;
            android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
            line.setColor(0x00000000);
            line.setStroke(px(border.width), argb(border.color));
            line.setCornerRadius(px(border.radius));
            view.setBackground(line);
            // The child sits inside the line rather than under it.
            int inset = px(border.width);
            view.setPadding(inset, inset, inset, inset);
        } else if (node instanceof Rounded) {
            Rounded rounded = (Rounded) node;
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(argb(rounded.color));
            shape.setCornerRadius(px(rounded.radius));
            view.setBackground(shape);
            view.setClipToOutline(true);
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

    /** Rebuilds the buttons only when the options changed, then checks the selected one. */
    private void bindRadioGroup(android.widget.RadioGroup group, RadioGroup node) {
        binding = true;
        try {
            int count = (int) node.options.size();
            boolean same = group.getChildCount() == count;
            for (int i = 0; same && i < count; i++) {
                same = ((String) node.options.get(i)).contentEquals(((TextView) group.getChildAt(i)).getText());
            }
            if (!same) {
                group.clearCheck();
                group.removeAllViews();
                for (int i = 0; i < count; i++) {
                    android.widget.RadioButton button = new android.widget.RadioButton(activity);
                    button.setId(i + 1);
                    button.setText((String) node.options.get(i));
                    group.addView(button);
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                ((android.widget.RadioButton) group.getChildAt(i)).setButtonTintList(Extras.checkedTint(accent));
            }
            int wanted = (int) node.selected + 1;
            if (node.selected < 0) {
                group.clearCheck();
            } else if (group.getCheckedRadioButtonId() != wanted) {
                group.check(wanted);
            }
        } finally {
            binding = false;
        }
    }

    private void bindDropdown(android.widget.Spinner spinner, Dropdown node) {
        int count = (int) node.options.size();
        android.widget.SpinnerAdapter current = spinner.getAdapter();
        boolean same = current != null && current.getCount() == count;
        for (int i = 0; same && i < count; i++) {
            same = node.options.get(i).equals(current.getItem(i));
        }
        if (!same) {
            java.util.ArrayList<String> items = new java.util.ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                items.add((String) node.options.get(i));
            }
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(activity,
                    android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
        if (spinner.getSelectedItemPosition() != (int) node.selected) {
            spinner.setSelection((int) node.selected);
        }
    }

    private static void setText(TextView view, String text) {
        if (!text.contentEquals(view.getText())) {
            view.setText(text);
        }
    }

    static int argb(Color color) {
        return android.graphics.Color.argb((int) color.alpha, (int) color.red, (int) color.green, (int) color.blue);
    }

    int px(long dp) {
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
        if (view.getParent() instanceof android.widget.GridLayout) {
            // Every cell takes an equal share of the width; the grid flows cells into rows itself.
            // A freshly added cell has the grid's default params (content width); ours have width 0.
            if (!(old instanceof android.widget.GridLayout.LayoutParams) || old.width != 0) {
                android.widget.GridLayout.LayoutParams cell = new android.widget.GridLayout.LayoutParams(
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f));
                cell.width = 0;
                cell.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(cell);
            }
        } else if (view.getParent() instanceof LinearLayout) {
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
            layout(Extras.childContainer(view).getChildAt(i), children.get(i), kind, spec.childWidth, spec.childHeight,
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
