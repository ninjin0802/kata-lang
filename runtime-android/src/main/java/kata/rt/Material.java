package kata.rt;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.WeakHashMap;

/**
 * The look of kata components: Material Design 3, light scheme (spec 05 #material). Colors that depend on
 * the accent are derived from it, so a Theme recolors every component consistently.
 */
final class Material {
    private Material() {}

    static final int PRIMARY = 0xFF6750A4;
    static final int SURFACE = 0xFFFEF7FF;
    static final int SURFACE_CONTAINER_LOW = 0xFFF7F2FA;
    static final int SURFACE_CONTAINER = 0xFFF3EDF7;
    static final int SURFACE_CONTAINER_HIGH = 0xFFECE6F0;
    static final int ON_SURFACE = 0xFF1D1B20;
    static final int ON_SURFACE_VARIANT = 0xFF49454F;
    static final int OUTLINE = 0xFF79747E;
    static final int OUTLINE_VARIANT = 0xFFCAC4D0;
    static final int ERROR = 0xFFB3261E;
    static final int INVERSE_SURFACE = 0xFF322F35;
    static final int INVERSE_ON_SURFACE = 0xFFF5EFF7;

    /** What the view was last styled with, so that a re-render does not rebuild (and reset) its drawables. */
    private static final WeakHashMap<View, Long> STYLED = new WeakHashMap<View, Long>();

    private static boolean alreadyStyled(View view, long key) {
        Long previous = STYLED.get(view);
        if (previous != null && previous == key) {
            return true;
        }
        STYLED.put(view, key);
        return false;
    }

    static int mix(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Text color that stays readable on the given background. */
    static int on(int background) {
        double luminance = (0.299 * ((background >> 16) & 0xFF) + 0.587 * ((background >> 8) & 0xFF)
                + 0.114 * (background & 0xFF)) / 255.0;
        return luminance > 0.6 ? ON_SURFACE : 0xFFFFFFFF;
    }

    /** The tonal container of an accent: the accent, much lighter. */
    static int container(int accent) {
        return mix(accent, 0xFFFFFFFF, 0.80f);
    }

    static int onContainer(int accent) {
        return mix(accent, 0xFF000000, 0.55f);
    }

    static GradientDrawable shape(int color, float radiusPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        return shape;
    }

    /** Wraps a shape so that a press shows a ripple clipped to that shape. */
    static Drawable ripple(GradientDrawable shape, int contentColor) {
        int rippleColor = (contentColor & 0x00FFFFFF) | 0x29000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, shape);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** A filled button: a 40dp-high pill in the given colors with a medium 14sp label. */
    static void filledButton(Renderer r, TextView button, int background, int content) {
        if (alreadyStyled(button, ((long) background << 32) | (content & 0xFFFFFFFFL))) {
            return;
        }
        button.setBackground(ripple(shape(background, r.px(20)), content));
        button.setTextColor(content);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(medium());
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(r.px(40));
        button.setMinimumHeight(r.px(40));
        button.setMinWidth(r.px(48));
        button.setMinimumWidth(r.px(48));
        button.setPadding(r.px(24), 0, r.px(24), 0);
        button.setStateListAnimator(null);
        button.setElevation(0);
    }

    /** A button without fill: the label in the accent, with or without a 1dp outline. */
    static void outlinedButton(Renderer r, TextView button, int accent, boolean outline) {
        filledButton(r, button, 0x00000000, accent);
        if (alreadyStyled(button, 0x100000000L * (outline ? 3 : 5) + (accent & 0xFFFFFFFFL))) {
            return;
        }
        GradientDrawable shape = shape(0x00000000, r.px(20));
        if (outline) {
            shape.setStroke(r.px(1), OUTLINE);
        } else {
            button.setPadding(r.px(12), 0, r.px(12), 0);
        }
        // The ripple needs an opaque mask of the same shape, since the shape itself is see-through.
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((accent & 0x00FFFFFF) | 0x29000000), shape,
                shape(0xFFFFFFFF, r.px(20))));
    }

    /** An outlined text field: 56dp high, a 1dp outline that becomes a 2dp accent outline when focused. */
    static void outlinedField(Renderer r, EditText field, int accent) {
        if (alreadyStyled(field, accent)) {
            return;
        }
        GradientDrawable idle = shape(0x00000000, r.px(4));
        idle.setStroke(r.px(1), OUTLINE);
        GradientDrawable focused = shape(0x00000000, r.px(4));
        focused.setStroke(r.px(2), accent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {}, idle);
        field.setBackground(states);
        field.setPadding(r.px(16), r.px(16), r.px(16), r.px(16));
        field.setMinHeight(r.px(56));
        field.setMinimumHeight(r.px(56));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setTextColor(ON_SURFACE);
        field.setHintTextColor(ON_SURFACE_VARIANT);
    }
}
