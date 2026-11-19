package me.piitex.sketchgl.ui;

import me.piitex.engine.gl.renderer.Renderer;
import me.piitex.engine.input.CursorManager;
import me.piitex.engine.input.Input;
import me.piitex.engine.input.MouseTracker;
import me.piitex.engine.ui.CursorMode;
import me.piitex.engine.ui.Draggable;
import me.piitex.engine.ui.color.Color;
import me.piitex.engine.ui.overlays.Overlay;
import me.piitex.engine.ui.text.Font;
import me.piitex.engine.ui.scroll.Scrollable;
import me.piitex.sketchgl.canvas.BrushSettings;
import me.piitex.sketchgl.canvas.Drawing;
import me.piitex.sketchgl.canvas.Pen;
import me.piitex.sketchgl.canvas.Tool;
import org.jetbrains.skia.Image;
import org.lwjgl.glfw.GLFW;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * Shows the {@link Drawing} on the workspace and turns the mouse into strokes. It holds the view of the picture: how
 * far it is zoomed and where it sits. The wheel zooms around the pointer; the Hand tool, or Space held down, drags
 * the picture around; a sideways swipe or Alt with the wheel moves it too. Dragging a corner of the picture makes the
 * canvas bigger or smaller (Shift keeps its proportions); dragging a top or left corner adds or cuts there, and what
 * is drawn stays where it was.
 */
public class CanvasView extends Overlay implements Draggable, Scrollable {
    public static final float MIN_ZOOM = 0.05f, MAX_ZOOM = 32f;
    private static final float MARGIN = 48f;
    private static final float HANDLE = 5f, GRAB = 10f;
    private static final Color SHADOW = new Color(0f, 0f, 0f, 0.35f);
    private static final Color CHECK_LIGHT = new Color(0.82f, 0.82f, 0.84f, 1f), CHECK_DARK = new Color(0.68f, 0.68f, 0.7f, 1f);
    private static final Color RING_DARK = new Color(0f, 0f, 0f, 0.75f), RING_LIGHT = new Color(1f, 1f, 1f, 0.95f);

    private final Pen pen;
    private Drawing drawing;
    private float zoom = 1f, panX, panY;
    private boolean panning, picking, moved, fitted;
    private double lastX, lastY;
    private int corner = -1, hot = -1;
    private double pressX, pressY;
    private Drawing.Box proposed;
    private CursorMode toolCursor = CursorMode.CROSSHAIR;
    private org.jetbrains.skia.Font labelFont;
    private Runnable onChange = () -> {
    };

    public CanvasView(Pen pen) {
        super();
        this.pen = pen;
        setCursorMode(CursorMode.CROSSHAIR);
        onMouseClick(event -> {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && drawing != null && !drawing.isStroking()) {
                pickAt(event.getMouseX(), event.getMouseY());
            }
        });
        pen.onChange(() -> {
            toolCursor = pen.getTool().kind() == Tool.Kind.PAN ? CursorMode.RESIZE_ALL : CursorMode.CROSSHAIR;
            setCursorMode(toolCursor);
        });
    }

    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public Drawing getDrawing() {
        return drawing;
    }

    /**
     * Shows {@code next}, fitted to the view. The old picture is not closed.
     */
    public void setDrawing(Drawing next) {
        drawing = next;
        fit();
    }

    public float getZoom() {
        return zoom;
    }

    public void fit() {
        if (drawing == null) return;
        float across = (float) (getWidth() - MARGIN) / drawing.getWidth(), down = (float) (getHeight() - MARGIN) / drawing.getHeight();
        zoom = Math.clamp(Math.min(1f, Math.min(across, down)), MIN_ZOOM, MAX_ZOOM);
        center();
        fitted = true;
    }

    public void center() {
        if (drawing == null) return;
        panX = (float) (getWidth() - drawing.getWidth() * zoom) / 2f;
        panY = (float) (getHeight() - drawing.getHeight() * zoom) / 2f;
    }

    /**
     * Cuts the picture to {@code area}, or makes it bigger; see {@link Drawing#resize}. What is drawn stays where it
     * is on the screen, so growing on the left or top does not make the picture jump.
     */
    public boolean resizePicture(Drawing.Box area) {
        if (drawing == null || area.equals(new Drawing.Box(0, 0, drawing.getWidth(), drawing.getHeight()))) return true;
        if (!drawing.resize(area)) {
            Dialogs.tell(getWindow(), "Could not resize the picture", "There is not enough memory for a picture that size.");
            return false;
        }
        panX += area.left() * zoom;
        panY += area.top() * zoom;
        keepInReach();
        fitted = false;
        onChange.run();
        return true;
    }

    public void actualSize() {
        zoomTo(1f, (float) getWidth() / 2f, (float) getHeight() / 2f);
    }

    public void zoomBy(float factor) {
        zoomTo(zoom * factor, (float) getWidth() / 2f, (float) getHeight() / 2f);
    }

    private void zoomTo(float next, float anchorX, float anchorY) {
        next = Math.clamp(next, MIN_ZOOM, MAX_ZOOM);
        float docX = (anchorX - panX) / zoom, docY = (anchorY - panY) / zoom;
        zoom = next;
        panX = anchorX - docX * zoom;
        panY = anchorY - docY * zoom;
        keepInReach();
        fitted = false;
    }

    private void pan(float dx, float dy) {
        panX += dx;
        panY += dy;
        keepInReach();
        fitted = false;
    }

    private void keepInReach() {
        if (drawing == null) return;
        float keep = 64f;
        panX = Math.clamp(panX, keep - drawing.getWidth() * zoom, (float) getWidth() - keep);
        panY = Math.clamp(panY, keep - drawing.getHeight() * zoom, (float) getHeight() - keep);
    }

    public void layoutIn(double x, double y, double width, double height) {
        double oldWidth = getWidth(), oldHeight = getHeight();
        setBounds(x, y, width, height);
        if (drawing == null) return;
        if (fitted || oldWidth <= 0) {
            fit();
        } else {
            panX += (float) (width - oldWidth) / 2f;
            panY += (float) (height - oldHeight) / 2f;
        }
    }

    private float localX(double windowX) {
        return (float) (windowX - getAbsoluteX());
    }

    private float localY(double windowY) {
        return (float) (windowY - getAbsoluteY());
    }

    private float docX(double windowX) {
        return (localX(windowX) - panX) / zoom;
    }

    private float docY(double windowY) {
        return (localY(windowY) - panY) / zoom;
    }

    public float hoverX() {
        return isHovered() ? docX(MouseTracker.getX()) : Float.NaN;
    }

    public float hoverY() {
        return isHovered() ? docY(MouseTracker.getY()) : Float.NaN;
    }

    private int cornerAt(float x, float y) {
        if (drawing == null || drawing.isStroking()) return -1;
        int found = -1;
        float nearest = GRAB * GRAB;
        for (int i = 0; i < 4; i++) {
            float dx = x - (panX + ((i & 1) != 0 ? drawing.getWidth() : 0) * zoom);
            float dy = y - (panY + ((i & 2) != 0 ? drawing.getHeight() : 0) * zoom);
            if (dx * dx + dy * dy <= nearest) {
                nearest = dx * dx + dy * dy;
                found = i;
            }
        }
        return found;
    }

    /**
     * The shape the picture would take if the corner being dragged were let go at (x, y), in window coordinates.
     */
    private Drawing.Box propose(double x, double y) {
        int width = drawing.getWidth(), height = drawing.getHeight();
        boolean right = (corner & 1) != 0, bottom = (corner & 2) != 0;
        int dx = Math.round((float) (x - pressX) / zoom), dy = Math.round((float) (y - pressY) / zoom);
        int newWidth = Math.clamp(width + (right ? dx : -dx), Drawing.MIN_SIDE, Drawing.MAX_SIDE);
        int newHeight = Math.clamp(height + (bottom ? dy : -dy), Drawing.MIN_SIDE, Drawing.MAX_SIDE);
        if ((getWindow().getInput().getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            float scale = Math.max((float) newWidth / width, (float) newHeight / height);
            newWidth = Math.clamp(Math.round(width * scale), Drawing.MIN_SIDE, Drawing.MAX_SIDE);
            newHeight = Math.clamp(Math.round(height * scale), Drawing.MIN_SIDE, Drawing.MAX_SIDE);
        }
        if ((long) newWidth * newHeight > Drawing.MAX_PIXELS) {
            double shrink = Math.sqrt((double) Drawing.MAX_PIXELS / ((double) newWidth * newHeight));
            newWidth = Math.max(Drawing.MIN_SIDE, (int) (newWidth * shrink));
            newHeight = Math.max(Drawing.MIN_SIDE, (int) (newHeight * shrink));
        }
        return new Drawing.Box(right ? 0 : width - newWidth, bottom ? 0 : height - newHeight, right ? newWidth : width, bottom ? newHeight : height);
    }

    private void pickAt(double windowX, double windowY) {
        Integer color = drawing.colorAt((int) Math.floor(docX(windowX)), (int) Math.floor(docY(windowY)));
        if (color != null && (color >>> 24) != 0) pen.setColor(color);
    }

    @Override
    public void onDragStart(double x, double y) {
        if (drawing == null) return;
        Input input = getWindow().getInput();
        Tool tool = pen.getTool();
        moved = false;
        lastX = x;
        lastY = y;
        if (!input.isKeyDown(GLFW.GLFW_KEY_SPACE)) {
            int grabbed = cornerAt(localX(x), localY(y));
            if (grabbed >= 0) {
                corner = grabbed;
                pressX = x;
                pressY = y;
                proposed = new Drawing.Box(0, 0, drawing.getWidth(), drawing.getHeight());
                return;
            }
        }
        if (tool.kind() == Tool.Kind.PAN || input.isKeyDown(GLFW.GLFW_KEY_SPACE)) {
            panning = true;
            return;
        }
        if ((input.getModifiers() & GLFW.GLFW_MOD_ALT) != 0 || tool.kind() == Tool.Kind.PICK) {
            picking = true;
            pickAt(x, y);
            return;
        }
        switch (tool.kind()) {
            case STROKE -> {
                pen.used();
                drawing.beginStroke(tool, pen.settings(), pen.getColor(), docX(x), docY(y));
            }
            case FILL -> {
                pen.used();
                BrushSettings settings = pen.settings();
                drawing.fill((int) Math.floor(docX(x)), (int) Math.floor(docY(y)), pen.getColor(),
                        settings.getOpacity(), settings.getHardness());
                onChange.run();
            }
            default -> {
            }
        }
    }

    @Override
    public void onDragUpdate(double x, double y) {
        if (drawing == null) return;
        if (corner >= 0) {
            proposed = propose(x, y);
            return;
        }
        if (panning) {
            pan((float) (x - lastX), (float) (y - lastY));
        } else if (picking) {
            pickAt(x, y);
        } else if (drawing.isStroking()) {
            drawing.strokeTo(docX(x), docY(y));
            moved = true;
        }
        lastX = x;
        lastY = y;
    }

    @Override
    public void onDragEnd(double x, double y) {
        if (corner >= 0 && drawing != null) {
            Drawing.Box area = propose(x, y);
            corner = -1;
            proposed = null;
            resizePicture(area);
            return;
        }
        corner = -1;
        proposed = null;
        if (drawing != null && drawing.isStroking()) {
            drawing.strokeTo(docX(x), docY(y));
            drawing.endStroke();
            onChange.run();
        }
        panning = false;
        picking = false;
    }

    @Override
    public void onScroll(double deltaX, double deltaY) {
        Input input = getWindow().getInput();
        boolean sideways = (input.getModifiers() & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT)) != 0;
        if (deltaX != 0 || sideways) {
            pan((float) deltaX * 40f, sideways ? (float) deltaY * 40f : 0f);
            return;
        }
        float cx = localX(MouseTracker.getX()), cy = localY(MouseTracker.getY());
        zoomTo(zoom * (float) Math.pow(1.12, Math.clamp(deltaY, -4.0, 4.0)), cx, cy);
    }

    /**
     * Notes the corner under the pointer and shows the cursor that goes with it. The engine only picks a cursor when
     * the pointer enters or leaves the view, so this has to ask for its own.
     */
    private void followCorners() {
        boolean space = getWindow() != null && getWindow().getInput().isKeyDown(GLFW.GLFW_KEY_SPACE);
        int before = hot;
        hot = corner >= 0 ? corner
                : isHovered() && !space ? cornerAt(localX(MouseTracker.getX()), localY(MouseTracker.getY())) : -1;
        if (hot >= 0) {
            CursorManager.request(hot == 0 || hot == 3 ? CursorMode.RESIZE_NWSE : CursorMode.RESIZE_NESW);
        } else if (before >= 0 && isHovered()) {
            CursorManager.request(toolCursor);
        }
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        followCorners();
        if (drawing != null) drawing.flush();
        if (drawing != null && drawing.isStroking() && !moved) drawing.hold(delta);
        moved = false;
    }

    @Override
    public void render(Renderer renderer) {
        if (!isEnabled()) return;
        super.render(renderer);
        float x = (float) getX(), y = (float) getY(), viewWidth = (float) getWidth(), viewHeight = (float) getHeight();
        renderer.pushClip(x, y, viewWidth, viewHeight);
        renderer.drawQuad(x, y, viewWidth, viewHeight, WORKSPACE, 0f);
        if (drawing != null) {
            drawPicture(renderer, x, y, viewWidth, viewHeight);
            drawCorners(renderer, x, y);
        }
        drawBrushRing(renderer, x, y);
        renderer.popClip();
    }

    private void drawPicture(Renderer renderer, float x, float y, float viewWidth, float viewHeight) {
        float left = x + panX, top = y + panY;
        float width = drawing.getWidth() * zoom, height = drawing.getHeight() * zoom;
        renderer.drawQuad(left + 3, top + 4, width, height, SHADOW, 0f);
        if (drawing.isTransparent()) drawChecker(renderer, left, top, width, height, x, y, viewWidth, viewHeight);
        Image image = drawing.image();
        if (zoom >= 2f) {
            // Zoomed in far, keep the pixels square instead of blurring them; only the part in view is drawn.
            float visLeft = Math.max(left, x), visTop = Math.max(top, y);
            float visRight = Math.min(left + width, x + viewWidth), visBottom = Math.min(top + height, y + viewHeight);
            if (visRight > visLeft && visBottom > visTop) {
                renderer.drawImageRegion(image, (visLeft - left) / zoom, (visTop - top) / zoom,
                        (visRight - visLeft) / zoom, (visBottom - visTop) / zoom,
                        visLeft, visTop, visRight - visLeft, visBottom - visTop, null, false);
            }
        } else {
            renderer.drawImage(image, left, top, width, height, 0f, 1f);
        }
        renderer.drawBorder(left - 0.5f, top - 0.5f, width + 1f, height + 1f, BORDER, 1f, 0f);
    }

    private void drawCorners(Renderer renderer, float viewX, float viewY) {
        if (drawing.isStroking()) return;
        Drawing.Box area = proposed != null ? proposed : new Drawing.Box(0, 0, drawing.getWidth(), drawing.getHeight());
        float left = viewX + panX + area.left() * zoom, top = viewY + panY + area.top() * zoom;
        float width = area.width() * zoom, height = area.height() * zoom;
        if (proposed != null) {
            renderer.drawQuad(left, top, width, height, ACCENT_SOFT, 0f);
            renderer.drawBorder(left, top, width, height, ACCENT, 2f, 0f);
        }
        for (int i = 0; i < 4; i++) {
            float cx = left + ((i & 1) != 0 ? width : 0), cy = top + ((i & 2) != 0 ? height : 0);
            renderer.drawQuad(cx - HANDLE, cy - HANDLE, 2 * HANDLE, 2 * HANDLE, i == hot ? ACCENT : TEXT, 1f);
            renderer.drawBorder(cx - HANDLE, cy - HANDLE, 2 * HANDLE, 2 * HANDLE, i == hot ? TEXT : ACCENT, 1.5f, 1f);
        }
        if (proposed != null) {
            if (labelFont == null) labelFont = Font.sized(FONT);
            String text = area.width() + " × " + area.height() + " px";
            float textWidth = labelFont.measureTextWidth(text);
            float lx = viewX + localX(MouseTracker.getX()) + 16, ly = viewY + localY(MouseTracker.getY()) + 22;
            renderer.drawQuad(lx - 6, ly - 15, textWidth + 12, 22, RAISED, 4f);
            renderer.drawText(text, lx, ly, labelFont, TEXT);
        }
    }

    private void drawChecker(Renderer renderer, float left, float top, float width, float height,
                             float viewX, float viewY, float viewW, float viewH) {
        float x0 = Math.max(left, viewX), y0 = Math.max(top, viewY);
        float x1 = Math.min(left + width, viewX + viewW), y1 = Math.min(top + height, viewY + viewH);
        if (x1 <= x0 || y1 <= y0) return;
        renderer.drawQuad(x0, y0, x1 - x0, y1 - y0, CHECK_LIGHT, 0f);
        float cell = 10f;
        int firstColumn = (int) Math.floor((x0 - left) / cell), firstRow = (int) Math.floor((y0 - top) / cell);
        for (int row = firstRow; top + row * cell < y1; row++) {
            for (int column = firstColumn + ((firstColumn + row) & 1); left + column * cell < x1; column += 2) {
                float cx = Math.max(x0, left + column * cell), cy = Math.max(y0, top + row * cell);
                float cw = Math.min(x1, left + (column + 1) * cell) - cx, ch = Math.min(y1, top + (row + 1) * cell) - cy;
                if (cw > 0 && ch > 0) renderer.drawQuad(cx, cy, cw, ch, CHECK_DARK, 0f);
            }
        }
    }

    private void drawBrushRing(Renderer renderer, float viewX, float viewY) {
        Tool tool = pen.getTool();
        if (!isHovered() || panning || hot >= 0 || drawing == null || tool.kind() != Tool.Kind.STROKE) return;
        float cx = viewX + localX(MouseTracker.getX()), cy = viewY + localY(MouseTracker.getY());
        float radius = pen.settings().getSize() / 2f * zoom;
        if (radius < 3f) {
            renderer.drawLine(cx - 6, cy, cx + 6, cy, RING_DARK, 3f);
            renderer.drawLine(cx, cy - 6, cx, cy + 6, RING_DARK, 3f);
            renderer.drawLine(cx - 5, cy, cx + 5, cy, RING_LIGHT, 1f);
            renderer.drawLine(cx, cy - 5, cx, cy + 5, RING_LIGHT, 1f);
            return;
        }
        int sides = (int) Math.clamp(radius * 1.5f, 16f, 120f);
        renderer.drawRegularPolygonOutline(cx, cy, radius + 0.5f, sides, 0f, RING_DARK, 2.5f);
        renderer.drawRegularPolygonOutline(cx, cy, radius + 0.5f, sides, 0f, RING_LIGHT, 1f);
    }
}
