package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.containers.Container;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * A small square of one color. Clicking it picks the color.
 */
class Swatch extends Container {
    static final double SIZE = 24;

    private final int argb;

    Swatch(int argb, IntConsumer picked) {
        super(0, 0, SIZE, SIZE);
        this.argb = argb | 0xFF000000;
        getStyling().setBackgroundColor(fromArgb(argb));
        getStyling().setHoverColor(fromArgb(argb));
        getStyling().setCornerRadius(4f);
        setSelected(false);
        setClickOnRelease(true);
        setTooltip(String.format("#%06X", argb & 0xFFFFFF));
        onMouseClick(event -> {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) picked.accept(this.argb);
        });
    }

    int argb() {
        return argb;
    }

    void setSelected(boolean selected) {
        getStyling().setBorderColor(selected ? ACCENT : BORDER);
        getStyling().setBorderThickness(selected ? 2f : 1f);
    }
}
