package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.color.Color;
import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.image.IconAsset;
import me.piitex.engine.ui.overlays.IconOverlay;
import org.lwjgl.glfw.GLFW;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * A square button with only an icon on it. It can show itself as the one that is switched on, like the tool in hand.
 */
class ToolButton extends Container {
    private final IconOverlay icon;
    private final double size;
    private boolean on;
    private boolean enabled = true;

    ToolButton(IconAsset asset, double size, double iconSize, String tooltip, Runnable action) {
        super(0, 0, size, size);
        this.size = size;
        getStyling().setBorderThickness(0f);
        getStyling().setCornerRadius(CORNER);
        icon = new IconOverlay(asset, iconSize, iconSize);
        icon.setPosition((size - iconSize) / 2, (size - iconSize) / 2);
        addElement(icon);
        look();
        setTooltip(tooltip);
        setClickOnRelease(true);
        onMouseClick(event -> {
            if (enabled && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) action.run();
        });
    }

    void setOn(boolean on) {
        this.on = on;
        look();
    }

    void setAvailable(boolean available) {
        enabled = available;
        look();
    }

    private void look() {
        Color background = !enabled ? Color.TRANSPARENT : on ? ACCENT_SOFT : Color.TRANSPARENT;
        getStyling().setBackgroundColor(background);
        getStyling().setHoverColor(!enabled ? Color.TRANSPARENT : on ? ACCENT_SOFT_HOVER : RAISED_HOVER);
        icon.setTint(!enabled ? DIM.withAlpha(0.5f) : on ? ACCENT : TEXT);
    }

    double size() {
        return size;
    }
}
