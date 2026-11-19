package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.icons.Feather;
import me.piitex.engine.ui.overlays.ButtonOverlay;
import me.piitex.engine.ui.overlays.TextOverlay;
import me.piitex.sketchgl.canvas.Drawing;
import me.piitex.sketchgl.canvas.Pen;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * The strip along the bottom: what the tool does, where the mouse is on the picture, and the zoom.
 */
class StatusBar extends Container {
    static final double HEIGHT = 34;

    private final CanvasView canvas;
    private final Pen pen;
    private final TextOverlay hint = Style.text("", FONT, LABEL), position = Style.text("", FONT, LABEL),
            size = Style.text("", FONT, LABEL), zoom = Style.text("", FONT, TEXT);
    private final ToolButton zoomOut, zoomIn, fit;
    private final ButtonOverlay actual;
    private String last = "";

    StatusBar(CanvasView canvas, Pen pen) {
        super(0, 0, 100, HEIGHT);
        this.canvas = canvas;
        this.pen = pen;
        getStyling().setBackgroundColor(BACKGROUND);
        getStyling().setBorderThickness(0f);
        getStyling().setCornerRadius(0f);
        addElement(hint);
        addElement(position);
        addElement(size);
        addElement(zoom);
        zoomOut = new ToolButton(Feather.ZOOM_OUT, 26, 15, "Zoom out  (-)", () -> canvas.zoomBy(1 / 1.25f));
        zoomIn = new ToolButton(Feather.ZOOM_IN, 26, 15, "Zoom in  (+)", () -> canvas.zoomBy(1.25f));
        fit = new ToolButton(Feather.MAXIMIZE, 26, 15, "Fit the picture in the window", canvas::fit);
        actual = new ButtonOverlay("1:1", FONT_SMALL, TEXT, 40, 24);
        actual.setTooltip("Actual size");
        actual.onAction(canvas::actualSize);
        addElements(zoomOut, zoomIn, fit, actual);
        refresh();
    }

    void layout(double width) {
        setWidth(width);
        double middle = (HEIGHT - 16) / 2;
        hint.setPosition(14, middle);
        double x = width - 12;
        x -= actual.getWidth();
        actual.setPosition(x, (HEIGHT - actual.getHeight()) / 2);
        x -= 30;
        fit.setPosition(x, (HEIGHT - 26) / 2);
        x -= 30;
        zoomIn.setPosition(x, (HEIGHT - 26) / 2);
        zoom.setPosition(x - 8 - zoom.getWidth(), middle);
        x -= 8 + 56;
        zoomOut.setPosition(x, (HEIGHT - 26) / 2);
        size.setPosition(zoomOut.getX() - 24 - size.getWidth(), middle);
        position.setPosition(size.getX() - 24 - position.getWidth(), middle);
    }

    private void refresh() {
        Drawing drawing = canvas.getDrawing();
        float x = canvas.hoverX(), y = canvas.hoverY();
        String where = Float.isNaN(x) || drawing == null ? "" : (int) Math.floor(x) + ", " + (int) Math.floor(y) + " px";
        String text = pen.getTool().kind().name() + "|" + where + "|" + (drawing == null ? "" : drawing.getWidth() + "x" + drawing.getHeight())
                + "|" + Math.round(canvas.getZoom() * 100);
        if (text.equals(last)) return;
        last = text;
        hint.setText(switch (pen.getTool().kind()) {
            case STROKE -> pen.getTool() == me.piitex.sketchgl.canvas.Tool.ERASER
                    ? "Eraser: drag to rub out.  [ and ] change the size."
                    : pen.getTool() == me.piitex.sketchgl.canvas.Tool.SPRAY
                    ? "Spray: hold the button down, and move to spread it.  [ and ] change the size."
                    : pen.getTool().label() + ": drag to draw.  Right-click or Alt+click picks a color.  [ and ] change the size.";
            case FILL -> "Fill: click inside an area to fill it.  Tolerance sets how alike the colors must be.";
            case PICK -> "Color picker: click or drag over the picture to take a color from it.";
            case PAN -> "Hand: drag to move the picture.  Space does the same with any tool.";
        });
        position.setText(where);
        size.setText(drawing == null ? "" : drawing.getWidth() + " × " + drawing.getHeight() + " px");
        zoom.setText(Math.round(canvas.getZoom() * 100) + "%");
        layout(getWidth());
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        refresh();
    }
}
