package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.theme.BaseTheme;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * The engine's buttons, fields, menus and dialogs in SketchGL's colors, from {@link Style}.
 */
public class SketchTheme extends BaseTheme {
    public SketchTheme() {
        setText(TEXT);
        setFieldBackground(FIELD);
        setFieldHoverBackground(FIELD_HOVER);
        setFieldBorder(BORDER);
        setPlaceholderText(DIM);
        setCaret(ACCENT);
        setSelection(SELECTION);
        setButtonBackground(RAISED);
        setButtonHoverBackground(RAISED_HOVER);
        setButtonBorder(BORDER);
        setContainerBackground(PANEL);
        setContainerBorder(BORDER);
        setWindowBackground(BACKGROUND);
        setScrollThumb(RAISED_HOVER);
        setScrollTrack(FIELD);
        setTooltipBackground(RAISED.withAlpha(0.97f));
        setTooltipBorder(BORDER);
        setTooltipText(TEXT);
        setCornerRadius(CORNER);
        setBorderThickness(1f);
    }
}
