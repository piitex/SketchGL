package me.piitex.sketchgl.ui;

import me.piitex.engine.gl.renderer.Renderer;
import me.piitex.engine.ui.overlays.Overlay;
import me.piitex.sketchgl.canvas.BrushSettings;
import me.piitex.sketchgl.canvas.Drawing;
import me.piitex.sketchgl.canvas.Pen;
import me.piitex.sketchgl.canvas.Tool;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * A short wavy stroke drawn with the tool in hand, so the effect of the sliders can be seen without drawing anything.
 */
class BrushPreview extends Overlay {
    private final Pen pen;
    private Drawing sample;

    BrushPreview(Pen pen, double width, double height) {
        this.pen = pen;
        setWidth(width);
        setHeight(height);
        getStyling().setCornerRadius(CORNER);
        redraw();
    }

    void redraw() {
        if (sample != null) sample.close();
        sample = null;
        Tool tool = pen.getTool();
        if (!tool.strokes()) return;
        int width = (int) getWidth(), height = (int) getHeight();
        sample = new Drawing(width, height, false);
        BrushSettings settings = pen.settings();
        if (tool == Tool.ERASER) {
            // Something to rub out: a band of the current color.
            BrushSettings band = Tool.PENCIL.defaults();
            band.setSize(height * 0.55f);
            wave(Tool.PENCIL, band, pen.getColor(), 10);
        }
        // The size of a stroke is shown as it is, unless it would not fit.
        BrushSettings shown = settings.copy();
        shown.setSize(Math.min(settings.getSize(), height - 10));
        wave(tool, shown, pen.getColor(), Math.max(14, shown.getSize() / 2f + 4));
        sample.endStroke();
    }

    private void wave(Tool tool, BrushSettings settings, int color, float margin) {
        float width = (float) getWidth(), height = (float) getHeight();
        float left = margin, right = width - margin;
        BrushSettings smooth = settings.copy();
        smooth.setSmoothing(0f);
        sample.beginStroke(tool, smooth, color, left, height / 2f);
        int steps = 48;
        for (int i = 1; i <= steps; i++) {
            float progress = (float) i / steps;
            sample.strokeTo(left + (right - left) * progress, height / 2f + (float) Math.sin(progress * Math.PI * 2) * height * 0.22f);
            if (tool == Tool.SPRAY) sample.hold(1f / 40f);
        }
        sample.endStroke();
    }

    @Override
    public void render(Renderer renderer) {
        if (!isEnabled()) return;
        float x = (float) getX(), y = (float) getY(), width = (float) getWidth(), height = (float) getHeight();
        renderer.drawQuad(x, y, width, height, BORDER, CORNER);
        renderer.drawQuad(x + 1, y + 1, width - 2, height - 2, Style.rgb(0xf4f4f6), CORNER - 1);
        if (sample != null) renderer.drawImage(sample.image(), x + 1, y + 1, width - 2, height - 2, CORNER - 1, 1f);
        super.render(renderer);
    }

    void dispose() {
        if (sample != null) sample.close();
        sample = null;
    }
}
