package kata.rt;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Views for the components that are more than one plain Android widget: bars, overlays, long lists. */
final class Extras {
    private Extras() {}

    private static final int ACCENT = 0xFF009688;
    private static final int SURFACE = 0xFFF5F5F5;
    private static final int TEXT = 0xDE000000;
    private static final int MUTED = 0x99000000;

    /** The view for the node, or null when the node is not one of these components. */
    static View create(final Renderer r, Ui node) {
        final KataActivity activity = r.activity;
        if (node instanceof Label) {
            return new TextView(activity);
        }
        if (node instanceof TopBar) {
            TextView bar = new TextView(activity);
            styleBar(r, bar);
            bar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            bar.setTextColor(TEXT);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(r.px(16), 0, r.px(16), 0);
            return bar;
        }
        if (node instanceof BackBar) {
            final LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            styleBar(r, bar);
            TextView back = new TextView(activity);
            back.setText("←");
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            back.setTextColor(TEXT);
            back.setPadding(r.px(16), 0, r.px(16), 0);
            back.setOnClickListener(view -> {
                ((BackBar) bar.getTag()).onBack.run();
                activity.rerender();
            });
            TextView title = new TextView(activity);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setTextColor(TEXT);
            bar.addView(back);
            bar.addView(title);
            return bar;
        }
        if (node instanceof Tabs || node instanceof BottomNav) {
            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            if (node instanceof BottomNav) {
                styleBar(r, bar);
                bar.setElevation(r.px(8));
            }
            return bar;
        }
        if (node instanceof Fab) {
            TextView fab = new TextView(activity);
            fab.setGravity(Gravity.CENTER);
            fab.setMinWidth(r.px(56));
            fab.setMinHeight(r.px(56));
            fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            fab.setTextColor(0xFFFFFFFF);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(ACCENT);
            fab.setBackground(circle);
            fab.setElevation(r.px(6));
            fab.setOnClickListener(view -> {
                ((Fab) view.getTag()).onTap.run();
                activity.rerender();
            });
            return fab;
        }
        if (node instanceof Chip) {
            TextView chip = new TextView(activity);
            chip.setTextColor(TEXT);
            chip.setPadding(r.px(12), r.px(6), r.px(12), r.px(6));
            chip.setOnClickListener(view -> {
                ((Chip) view.getTag()).onTap.run();
                activity.rerender();
            });
            return chip;
        }
        if (node instanceof Badge) {
            TextView badge = new TextView(activity);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(r.px(20));
            badge.setMinHeight(r.px(20));
            badge.setPadding(r.px(6), 0, r.px(6), 0);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            badge.setTextColor(0xFFFFFFFF);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(0xFFD32F2F);
            pill.setCornerRadius(r.px(10));
            badge.setBackground(pill);
            return badge;
        }
        if (node instanceof Alert) {
            return new AlertAnchor(r);
        }
        if (node instanceof Modal || node instanceof BottomSheet) {
            return new SheetAnchor(r, node instanceof BottomSheet);
        }
        if (node instanceof Snackbar) {
            return new SnackbarAnchor(r);
        }
        if (node instanceof LazyList) {
            final ListView list = new ListView(activity);
            list.setDivider(null);
            list.setAdapter(new RowAdapter(r, list));
            return list;
        }
        return null;
    }

    /** Applies the node to a view made by create. Returns false when the node is not one of these components. */
    static boolean bind(Renderer r, View view, Ui node) {
        if (node instanceof Label) {
            Label label = (Label) node;
            TextView text = (TextView) view;
            setText(text, label.text);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.size);
            text.setTextColor(r.argb(label.color));
            int style = (label.bold ? Typeface.BOLD : 0) | (label.italic ? Typeface.ITALIC : 0);
            text.setTypeface(Typeface.defaultFromStyle(style));
            text.setMaxLines((int) Math.min(Integer.MAX_VALUE, label.maxLines));
            text.setEllipsize(TextUtils.TruncateAt.END);
        } else if (node instanceof TopBar) {
            setText((TextView) view, ((TopBar) node).title);
        } else if (node instanceof BackBar) {
            setText((TextView) ((ViewGroup) view).getChildAt(1), ((BackBar) node).title);
        } else if (node instanceof Tabs) {
            bindSegments(r, (LinearLayout) view, ((Tabs) node).labels, ((Tabs) node).selected, 12);
        } else if (node instanceof BottomNav) {
            bindSegments(r, (LinearLayout) view, ((BottomNav) node).labels, ((BottomNav) node).selected, 18);
        } else if (node instanceof Fab) {
            setText((TextView) view, ((Fab) node).label);
        } else if (node instanceof Chip) {
            Chip chip = (Chip) node;
            setText((TextView) view, chip.label);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(r.px(16));
            if (chip.selected) {
                pill.setColor(0xFFB2DFDB);
            } else {
                pill.setColor(0x00000000);
                pill.setStroke(r.px(1), MUTED);
            }
            view.setBackground(pill);
        } else if (node instanceof Badge) {
            long count = ((Badge) node).count;
            setText((TextView) view, count > 99 ? "99+" : Long.toString(count));
        } else if (node instanceof Alert) {
            ((AlertAnchor) view).sync();
        } else if (node instanceof Modal || node instanceof BottomSheet) {
            ((SheetAnchor) view).sync();
        } else if (node instanceof Snackbar) {
            ((SnackbarAnchor) view).sync();
        } else if (node instanceof LazyList) {
            ((RowAdapter) ((ListView) view).getAdapter()).notifyDataSetChanged();
        } else {
            return false;
        }
        return true;
    }

    /** Where the children of a component's view actually live: a sheet keeps them in its dialog. */
    static ViewGroup childContainer(View parent) {
        return parent instanceof SheetAnchor ? ((SheetAnchor) parent).content : (ViewGroup) parent;
    }

    private static void styleBar(Renderer r, View bar) {
        bar.setBackgroundColor(SURFACE);
        bar.setMinimumHeight(r.px(56));
        bar.setElevation(r.px(4));
    }

    private static void setText(TextView view, String text) {
        if (!text.contentEquals(view.getText())) {
            view.setText(text);
        }
    }

    /** Equal-width labels of a Tabs or BottomNav; rebuilt only when the labels change. */
    private static void bindSegments(final Renderer r, final LinearLayout bar, KList labels, long selected,
            int verticalPadding) {
        int count = (int) labels.size();
        boolean same = bar.getChildCount() == count;
        for (int i = 0; same && i < count; i++) {
            same = ((String) labels.get(i)).contentEquals(((TextView) bar.getChildAt(i)).getText());
        }
        if (!same) {
            bar.removeAllViews();
            for (int i = 0; i < count; i++) {
                final long index = i;
                TextView segment = new TextView(r.activity);
                segment.setText((String) labels.get(i));
                segment.setGravity(Gravity.CENTER);
                segment.setPadding(r.px(4), r.px(verticalPadding), r.px(4), r.px(verticalPadding));
                segment.setOnClickListener(view -> {
                    Object node = bar.getTag();
                    long current = node instanceof Tabs ? ((Tabs) node).selected : ((BottomNav) node).selected;
                    if (index != current) {
                        Handler handler = node instanceof Tabs ? ((Tabs) node).onChange : ((BottomNav) node).onChange;
                        handler.run(Long.valueOf(index));
                        r.activity.rerender();
                    }
                });
                bar.addView(segment, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
        }
        for (int i = 0; i < count; i++) {
            TextView segment = (TextView) bar.getChildAt(i);
            boolean active = i == selected;
            segment.setTextColor(active ? ACCENT : MUTED);
            segment.setTypeface(Typeface.defaultFromStyle(active ? Typeface.BOLD : Typeface.NORMAL));
            segment.setSelected(active);
        }
    }

    /** An invisible view whose presence in the window keeps an AlertDialog open. */
    private static final class AlertAnchor extends View {
        private final Renderer r;
        private AlertDialog dialog;
        private boolean attached;

        AlertAnchor(Renderer r) {
            super(r.activity);
            this.r = r;
        }

        void sync() {
            if (!attached) {
                return;
            }
            Alert alert = (Alert) getTag();
            if (dialog != null && dialog.isShowing()) {
                dialog.setTitle(alert.title);
                dialog.setMessage(alert.message);
                return;
            }
            dialog = new AlertDialog.Builder(r.activity)
                    .setTitle(alert.title)
                    .setMessage(alert.message)
                    .setPositiveButton(alert.confirmLabel, (d, which) -> {
                        ((Alert) getTag()).onConfirm.run();
                        r.activity.rerender();
                    })
                    .setNegativeButton(alert.cancelLabel, (d, which) -> {
                        ((Alert) getTag()).onCancel.run();
                        r.activity.rerender();
                    })
                    .setOnCancelListener(d -> {
                        ((Alert) getTag()).onCancel.run();
                        r.activity.rerender();
                    })
                    .show();
            // Labels are shown as written, like every other kata button.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            sync();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            if (dialog != null) {
                dialog.setOnCancelListener(null);
                dialog.dismiss();
                dialog = null;
            }
            super.onDetachedFromWindow();
        }
    }

    /** An invisible view whose presence keeps a dialog open; the component's child lives in `content`. */
    static final class SheetAnchor extends View {
        final FrameLayout content;
        private final Renderer r;
        private final boolean bottom;
        private Dialog dialog;
        private boolean attached;

        SheetAnchor(Renderer r, boolean bottom) {
            super(r.activity);
            this.r = r;
            this.bottom = bottom;
            this.content = new FrameLayout(r.activity);
        }

        void sync() {
            if (!attached || (dialog != null && dialog.isShowing())) {
                return;
            }
            if (dialog == null) {
                dialog = new Dialog(r.activity);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(content);
                Window window = dialog.getWindow();
                GradientDrawable surface = new GradientDrawable();
                surface.setColor(0xFFFFFFFF);
                float radius = r.px(16);
                surface.setCornerRadii(bottom
                        ? new float[] {radius, radius, radius, radius, 0, 0, 0, 0}
                        : new float[] {radius, radius, radius, radius, radius, radius, radius, radius});
                window.setBackgroundDrawable(surface);
                if (bottom) {
                    window.setGravity(Gravity.BOTTOM);
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                dialog.setOnCancelListener(d -> {
                    Object node = getTag();
                    (node instanceof Modal ? ((Modal) node).onDismiss : ((BottomSheet) node).onDismiss).run();
                    r.activity.rerender();
                });
            }
            dialog.show();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            sync();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            if (dialog != null) {
                dialog.setOnCancelListener(null);
                dialog.dismiss();
                dialog = null;
            }
            super.onDetachedFromWindow();
        }
    }

    /** An invisible view whose presence keeps a message bar at the bottom of the screen. */
    private static final class SnackbarAnchor extends View {
        private static final long VISIBLE_MILLIS = 4000;

        private final Renderer r;
        private LinearLayout bar;
        private final Runnable timeout = () -> {
            ((Snackbar) getTag()).onDismiss.run();
            SnackbarAnchor.this.r.activity.rerender();
        };

        SnackbarAnchor(Renderer r) {
            super(r.activity);
            this.r = r;
        }

        void sync() {
            if (bar == null) {
                return;
            }
            Snackbar snackbar = (Snackbar) getTag();
            setText((TextView) bar.getChildAt(0), snackbar.message);
            setText((TextView) bar.getChildAt(1), snackbar.actionLabel);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            bar = new LinearLayout(r.activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(r.px(16), r.px(12), r.px(8), r.px(12));
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(0xFF323232);
            surface.setCornerRadius(r.px(4));
            bar.setBackground(surface);
            bar.setElevation(r.px(6));
            TextView message = new TextView(r.activity);
            message.setTextColor(0xFFFFFFFF);
            TextView action = new TextView(r.activity);
            action.setTextColor(0xFF80CBC4);
            action.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            action.setPadding(r.px(12), r.px(4), r.px(12), r.px(4));
            action.setOnClickListener(view -> {
                ((Snackbar) getTag()).onAction.run();
                r.activity.rerender();
            });
            bar.addView(message, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bar.addView(action);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            params.setMargins(r.px(8), r.px(8), r.px(8), r.px(8));
            r.container.addView(bar, params);
            sync();
            postDelayed(timeout, VISIBLE_MILLIS);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(timeout);
            if (bar != null) {
                r.container.removeView(bar);
                bar = null;
            }
            super.onDetachedFromWindow();
        }
    }

    /** Builds LazyList rows on demand, reusing the views of rows that scrolled away. */
    private static final class RowAdapter extends BaseAdapter {
        private final Renderer r;
        private final ListView list;

        RowAdapter(Renderer r, ListView list) {
            this.r = r;
            this.list = list;
        }

        private Items items() {
            Object node = list.getTag();
            return node == null ? null : ((LazyList) node).items;
        }

        @Override
        public int getCount() {
            Items items = items();
            return items == null ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, items.size()));
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Ui node = items().item(position);
            UiValidator.validate(node);
            FrameLayout holder = (FrameLayout) convertView;
            View row;
            if (holder == null) {
                holder = new FrameLayout(r.activity);
                row = r.mountRow(node);
                holder.addView(row);
            } else {
                View existing = holder.getChildAt(0);
                row = r.patchRow(existing, (Ui) holder.getTag(), node);
                if (row != existing) {
                    holder.removeAllViews();
                    holder.addView(row);
                }
            }
            holder.setTag(node);
            r.layoutRow(row, node);
            return holder;
        }
    }
}
