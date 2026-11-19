package me.piitex.sketchgl.canvas;

import org.jetbrains.skia.*;
import org.jetbrains.skia.impl.Managed;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * The picture being drawn: its pixels, the stroke in progress, and the undo history.
 * <p>
 * Three raster surfaces of the picture's size do the work. {@code base} holds what is drawn, on a transparent
 * background. {@code stroke} holds the stroke that is still being drawn, at full strength, so that a stroke crossing
 * itself does not get darker where it overlaps; the stroke's opacity is applied only when it is put on {@code base}
 * (and, so that it can be seen meanwhile, when {@code view} is built). {@code view} is {@code base} over the paper
 * with the stroke on top, and is what the screen shows.
 * <p>
 * Everything runs on the render thread.
 */
public final class Drawing implements AutoCloseable {
    public static final int MIN_SIDE = 16, MAX_SIDE = 16384;
    public static final long MAX_PIXELS = 100_000_000L;
    private static final long UNDO_BUDGET = 256L << 20;
    private static final int CHUNK = 40;
    private static final int PAPER_WHITE = 0xFFFFFFFF;

    private int width, height;
    private Surface base, strokeLayer, view;
    private Surface committed;
    private final int paper;
    private final ArrayDeque<Step> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long undoBytes;
    private Image shown;
    private long version;
    private boolean modified;
    private Live live;

    private sealed interface Step {
        long bytes();

        void close();
    }

    /**
     * A step that painted: the pixels of a rectangle before and after.
     */
    private record Edit(Box box, Bitmap before, Bitmap after) implements Step {
        @Override
        public long bytes() {
            return (long) box.width() * box.height() * 8;
        }

        @Override
        public void close() {
            before.close();
            after.close();
        }
    }

    /**
     * A step that changed the size of the picture. {@code area} is the new picture as seen from the old one, so it
     * reaches past the old edges when the picture grew. {@code lost} holds the old pixels, but only when the step
     * cut some off; growing loses nothing, so undoing it is just cutting the picture back.
     */
    private record Resize(Box area, int oldWidth, int oldHeight, Bitmap lost) implements Step {
        Box undoArea() {
            return new Box(-area.left(), -area.top(), -area.left() + oldWidth, -area.top() + oldHeight);
        }

        @Override
        public long bytes() {
            return lost == null ? 0 : (long) oldWidth * oldHeight * 4;
        }

        @Override
        public void close() {
            if (lost != null) lost.close();
        }
    }

    /**
     * A rectangle of whole pixels, right and bottom exclusive.
     */
    public record Box(int left, int top, int right, int bottom) {
        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        Box union(Box other) {
            if (isEmpty()) return other;
            if (other.isEmpty()) return this;
            return new Box(Math.min(left, other.left), Math.min(top, other.top),
                    Math.max(right, other.right), Math.max(bottom, other.bottom));
        }

        Box clamp(int width, int height) {
            return new Box(Math.max(0, left), Math.max(0, top), Math.min(width, right), Math.min(height, bottom));
        }

        static Box around(float x, float y, float reach) {
            return new Box((int) Math.floor(x - reach), (int) Math.floor(y - reach),
                    (int) Math.ceil(x + reach), (int) Math.ceil(y + reach));
        }
    }

    /**
     * A blank picture. {@code transparent} leaves the paper see-through; otherwise it is white.
     */
    public Drawing(int width, int height, boolean transparent) {
        this.width = Math.clamp(width, MIN_SIDE, MAX_SIDE);
        this.height = Math.clamp(height, MIN_SIDE, MAX_SIDE);
        if (!fits(this.width, this.height)) {
            throw new IllegalArgumentException("A picture of " + this.width + "x" + this.height + " has too many pixels");
        }
        this.paper = transparent ? 0 : PAPER_WHITE;
        base = layer(this.width, this.height);
        strokeLayer = layer(this.width, this.height);
        view = layer(this.width, this.height);
        refresh(whole());
    }

    public static boolean fits(int width, int height) {
        return width >= MIN_SIDE && height >= MIN_SIDE && width <= MAX_SIDE && height <= MAX_SIDE
                && (long) width * height <= MAX_PIXELS;
    }

    private static Surface layer(int width, int height) {
        Surface surface = Surface.Companion.makeRasterN32Premul(width, height);
        if (surface == null) throw new IllegalStateException("No memory for a " + width + "x" + height + " layer");
        return surface;
    }

    public static Drawing of(Image image) {
        Drawing drawing = new Drawing(image.getWidth(), image.getHeight(), false);
        drawing.base.getCanvas().drawImage(image, 0, 0);
        drawing.refresh(drawing.whole());
        return drawing;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isTransparent() {
        return paper == 0;
    }

    public boolean isModified() {
        return modified;
    }

    public void markSaved() {
        modified = false;
    }

    private Box whole() {
        return new Box(0, 0, width, height);
    }

    /**
     * What to draw on screen. The image stays valid until the picture next changes.
     */
    public Image image() {
        if (shown == null) shown = view.makeImageSnapshot();
        return shown;
    }

    public long getVersion() {
        return version;
    }

    private void refresh(Box box) {
        box = box.clamp(width, height);
        if (box.isEmpty()) return;
        // The old snapshot is let go first: while it exists, the next write to the view would copy the whole surface.
        if (shown != null) {
            shown.close();
            shown = null;
        }
        Canvas canvas = view.getCanvas();
        canvas.save();
        canvas.clipRect(box.left(), box.top(), box.right(), box.bottom(), false);
        canvas.clear(paper);
        boolean erasing = live != null && live.erase;
        if (erasing) {
            // The stroke removes from the picture, not from the paper below it.
            canvas.saveLayer(Rect.makeLTRB(box.left(), box.top(), box.right(), box.bottom()), null);
        }
        base.draw(canvas, 0, 0, null);
        if (live != null) strokeLayer.draw(canvas, 0, 0, live.composite);
        if (erasing) canvas.restore();
        canvas.restore();
        version++;
    }

    public boolean isStroking() {
        return live != null;
    }

    public void beginStroke(Tool tool, BrushSettings brush, int argb, float x, float y) {
        if (live != null) endStroke();
        live = new Live(tool, brush.copy(), argb, x, y);
        live.begin();
        refresh(live.takeFresh());
    }

    public void strokeTo(float x, float y) {
        if (live == null) return;
        live.rawX = x;
        live.rawY = y;
        float follow = Math.max(0.07f, 1f - 0.93f * live.brush.getSmoothing());
        live.smoothX += (x - live.smoothX) * follow;
        live.smoothY += (y - live.smoothY) * follow;
        live.extend(live.smoothX, live.smoothY);
    }

    /**
     * Shows what the pen has drawn since the last call. Many mouse moves can arrive between two frames, so
     * {@link #strokeTo} only adds them to the stroke and the picture is rebuilt here, once, by the screen.
     */
    public void flush() {
        if (live == null) return;
        live.redraw();
        refresh(live.takeFresh());
    }

    public void hold(float seconds) {
        if (live == null || live.tool != Tool.SPRAY) return;
        live.sprayStill(seconds);
        refresh(live.takeFresh());
    }

    public void endStroke() {
        if (live == null) return;
        Live finished = live;
        finished.finish();
        finished.redraw();
        Box box = finished.dirty.clamp(width, height);
        if (!box.isEmpty()) {
            Bitmap before = read(base, box);
            base.getCanvas().save();
            base.getCanvas().clipRect(box.left(), box.top(), box.right(), box.bottom(), false);
            strokeLayer.draw(base.getCanvas(), 0, 0, finished.composite);
            base.getCanvas().restore();
            push(new Edit(box, before, read(base, box)));
            clearStroke(box);
        }
        live = null;
        finished.close();
        refresh(box);
    }

    public void cancelStroke() {
        if (live == null) return;
        Live dropped = live;
        live = null;
        clearStroke(dropped.dirty.clamp(width, height));
        refresh(dropped.dirty);
        dropped.close();
    }

    private void clearStroke(Box box) {
        Canvas canvas = strokeLayer.getCanvas();
        canvas.save();
        canvas.clipRect(box.left(), box.top(), box.right(), box.bottom(), false);
        canvas.clear(0);
        canvas.restore();
        if (committed != null) {
            Canvas sealed = committed.getCanvas();
            sealed.save();
            sealed.clipRect(box.left(), box.top(), box.right(), box.bottom(), false);
            sealed.clear(0);
            sealed.restore();
        }
    }

    /**
     * The stroke being drawn: its tool, its settings, and the state of its path.
     */
    private final class Live implements AutoCloseable {
        private enum Mode {STAMP, PATH, SPRAY}

        final Tool tool;
        final BrushSettings brush;
        final boolean erase;
        final int argb;
        final Mode mode;
        final Paint composite = new Paint();
        final Random random = new Random();
        Paint pen;
        PathBuilder tail;
        Paint dot;
        float lineWidth, startX, startY, startDx = 1f, startDy = 0f, endDx = 1f, endDy = 0f, tailX, tailY;
        boolean square, startKnown, firstChunk = true, sealed, tailDirty;
        int tailCurves;
        Image tip;
        float rawX, rawY, smoothX, smoothY;
        float prevX, prevY, midX, midY;
        Box dirty = new Box(0, 0, 0, 0);
        Box fresh = new Box(0, 0, 0, 0);

        Live(Tool tool, BrushSettings brush, int argb, float x, float y) {
            this.tool = tool;
            this.brush = brush;
            this.erase = tool == Tool.ERASER;
            this.argb = erase ? 0xFF000000 : argb | 0xFF000000;
            this.mode = tool == Tool.SPRAY ? Mode.SPRAY
                    : (tool == Tool.BRUSH || tool == Tool.ERASER) && brush.getHardness() < 0.98f ? Mode.STAMP
                    : Mode.PATH;
            composite.setAlphaf(brush.getOpacity());
            composite.setBlendMode(erase ? BlendMode.DST_OUT : BlendMode.SRC_OVER);
            rawX = smoothX = prevX = midX = x;
            rawY = smoothY = prevY = midY = y;
        }

        float reach() {
            return brush.getSize() * 0.75f + 2f; // far enough for the corner of a square marker end
        }

        void grow(float x, float y) {
            Drawing.Box around = Box.around(x, y, reach());
            dirty = dirty.union(around);
            fresh = fresh.union(around);
        }

        Box takeFresh() {
            Drawing.Box changed = fresh;
            fresh = new Box(0, 0, 0, 0);
            return changed;
        }

        void begin() {
            grow(rawX, rawY);
            Canvas canvas = strokeLayer.getCanvas();
            switch (mode) {
                case STAMP -> {
                    tip = BrushTip.soft(brush.getSize(), brush.getHardness(), argb);
                    stamp(canvas, rawX, rawY);
                }
                case PATH -> {
                    lineWidth = Math.max(1f, brush.getSize());
                    square = tool == Tool.MARKER;
                    pen = new Paint();
                    pen.setColor(argb);
                    pen.setMode(PaintMode.STROKE);
                    pen.setStrokeWidth(lineWidth);
                    pen.setAntiAlias(tool != Tool.PENCIL);
                    pen.setStrokeCap(square ? PaintStrokeCap.BUTT : PaintStrokeCap.ROUND); // a square end is drawn by hand, see startCap
                    pen.setStrokeJoin(PaintStrokeJoin.ROUND);
                    dot = new Paint();
                    dot.setColor(argb);
                    dot.setMode(PaintMode.STROKE);
                    dot.setStrokeWidth(lineWidth);
                    dot.setAntiAlias(tool != Tool.PENCIL);
                    dot.setStrokeCap(PaintStrokeCap.ROUND);
                    startX = tailX = rawX;
                    startY = tailY = rawY;
                    tail = new PathBuilder();
                    tail.moveTo(rawX, rawY);
                    if (!square) tail.lineTo(rawX, rawY); // a click leaves a dot
                    restroke();
                }
                case SPRAY -> {
                    pen = new Paint();
                    pen.setColor(argb);
                    pen.setStrokeWidth(1.4f);
                    pen.setStrokeCap(PaintStrokeCap.ROUND);
                    pen.setAntiAlias(true);
                    spray(rawX, rawY, burst());
                }
            }
        }

        /**
         * Adds the point (x, y); the stroke curves through the middles between the points it is given.
         */
        void extend(float x, float y) {
            if (Math.hypot(x - prevX, y - prevY) < 0.05) return;
            float mx = (prevX + x) / 2f, my = (prevY + y) / 2f;
            curve(midX, midY, prevX, prevY, mx, my);
            midX = mx;
            midY = my;
            prevX = x;
            prevY = y;
        }

        void finish() {
            extend(rawX, rawY);
            curve(midX, midY, prevX, prevY, prevX, prevY);
        }

        private void curve(float ax, float ay, float cx, float cy, float bx, float by) {
            grow(ax, ay);
            grow(cx, cy);
            grow(bx, by);
            Canvas canvas = strokeLayer.getCanvas();
            switch (mode) {
                case PATH -> {
                    if (!startKnown && aim(cx - ax, cy - ay, true)) startKnown = true;
                    if (!aim(bx - cx, by - cy, false)) aim(cx - ax, cy - ay, false);
                    tail.quadTo(cx, cy, bx, by);
                    tailX = bx;
                    tailY = by;
                    tailCurves++;
                    tailDirty = true;
                }
                case STAMP -> {
                    float spacing = Math.max(0.6f, brush.getSize() * 0.07f);
                    float length = (float) (Math.hypot(cx - ax, cy - ay) + Math.hypot(bx - cx, by - cy));
                    int steps = Math.max(1, (int) Math.ceil(length / spacing));
                    for (int i = 1; i <= steps; i++) {
                        float along = (float) i / steps, remaining = 1f - along;
                        stamp(canvas, remaining * remaining * ax + 2 * remaining * along * cx + along * along * bx, remaining * remaining * ay + 2 * remaining * along * cy + along * along * by);
                    }
                }
                case SPRAY -> {
                    float spacing = Math.max(1.5f, brush.getSize() * 0.15f);
                    float length = (float) (Math.hypot(cx - ax, cy - ay) + Math.hypot(bx - cx, by - cy));
                    int steps = Math.max(1, (int) Math.ceil(length / spacing));
                    for (int i = 1; i <= steps; i++) {
                        float along = (float) i / steps, remaining = 1f - along;
                        spray(remaining * remaining * ax + 2 * remaining * along * cx + along * along * bx, remaining * remaining * ay + 2 * remaining * along * cy + along * along * by, burst());
                    }
                }
            }
        }

        private void stamp(Canvas canvas, float x, float y) {
            float half = BrushTip.side(brush.getSize()) / 2f;
            canvas.drawImageRect(tip, Rect.makeWH(tip.getWidth(), tip.getHeight()),
                    Rect.makeXYWH(x - half, y - half, tip.getWidth(), tip.getHeight()),
                    SamplingMode.Companion.getLINEAR(), null, false);
        }

        void redraw() {
            if (mode != Mode.PATH || !tailDirty) return;
            tailDirty = false;
            restroke();
            if (tailCurves >= CHUNK) seal();
        }

        /**
         * Turns the start or end of the path towards (dx, dy); false if that is no direction at all.
         */
        private boolean aim(float dx, float dy, boolean start) {
            float length = (float) Math.hypot(dx, dy);
            if (length < 1e-3f) return false;
            if (start) {
                startDx = dx / length;
                startDy = dy / length;
            } else {
                endDx = dx / length;
                endDy = dy / length;
            }
            return true;
        }

        /**
         * The square end of the marker. It reaches a little into the stroke, so no faint line shows where they meet.
         */
        private void startCap(Canvas canvas) {
            float back = lineWidth / 2f, into = 1.5f;
            canvas.drawLine(startX - startDx * back, startY - startDy * back, startX + startDx * into, startY + startDy * into, pen);
        }

        private void endCap(Canvas canvas) {
            float out = lineWidth / 2f, into = 1.5f;
            canvas.drawLine(tailX - endDx * into, tailY - endDy * into, tailX + endDx * out, tailY + endDy * out, pen);
        }

        private void drawTail(Canvas canvas) {
            // Closed through Managed, not Path itself: javac would need kotlin-stdlib to look inside Path.
            Path shape = tail.snapshot();
            Managed handle = shape;
            try {
                canvas.drawPath(shape, pen);
            } finally {
                handle.close();
            }
        }

        /**
         * Draws the newest part of the stroke again, over the sealed part. Only that part is redone, so a long stroke
         * costs no more per update than a short one, and it is redone rather than added to so that the round joins do
         * not pile up on the soft edge.
         */
        private void restroke() {
            Canvas canvas = strokeLayer.getCanvas();
            Box box = fresh.clamp(width, height);
            canvas.save();
            canvas.clipRect(box.left(), box.top(), box.right(), box.bottom(), false);
            canvas.clear(0);
            if (sealed) committed.draw(canvas, 0, 0, null);
            if (square && firstChunk) startCap(canvas);
            drawTail(canvas);
            if (square) endCap(canvas);
            canvas.restore();
        }

        private void seal() {
            if (committed == null) committed = Surface.Companion.makeRasterN32Premul(width, height);
            Canvas canvas = committed.getCanvas();
            if (square && firstChunk) startCap(canvas);
            drawTail(canvas);
            if (square) canvas.drawLine(tailX, tailY, tailX, tailY, dot); // the round join to the next part
            tail.close();
            tail = new PathBuilder();
            tail.moveTo(tailX, tailY);
            tailCurves = 0;
            firstChunk = false;
            sealed = true;
        }

        private int burst() {
            return 2 + (int) (brush.getSize() * brush.getSize() * 0.006f);
        }

        void sprayStill(float seconds) {
            int dots = Math.max(1, Math.round(brush.getSize() * brush.getSize() * 0.9f * seconds));
            spray(smoothX, smoothY, dots);
        }

        private void spray(float x, float y, int dots) {
            float radius = brush.getSize() / 2f;
            float[] points = new float[dots * 2];
            for (int i = 0; i < dots; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = radius * Math.sqrt(random.nextDouble());
                points[i * 2] = (float) (x + Math.cos(angle) * distance);
                points[i * 2 + 1] = (float) (y + Math.sin(angle) * distance);
            }
            strokeLayer.getCanvas().drawPoints(points, pen);
            grow(x, y);
        }

        @Override
        public void close() {
            composite.close();
            if (pen != null) pen.close();
            if (dot != null) dot.close();
            if (tail != null) tail.close();
        }
    }

    /**
     * Fills the area around (x, y) that has nearly the color of that pixel. {@code tolerance} (0 to 1) says how
     * different a pixel may be and still count.
     */
    public void fill(int x, int y, int argb, float opacity, float tolerance) {
        if (live != null || x < 0 || y < 0 || x >= width || y >= height) return;
        ImageInfo info = new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        byte[] pixels;
        try (Bitmap all = read(base, whole())) {
            pixels = all.readPixels(info, width * 4, 0, 0);
        }
        if (pixels == null) return;
        boolean[] inside = new boolean[width * height];
        Box box = Flood.fill(pixels, width, height, x, y, Math.round(tolerance * 255f), inside);
        if (box.isEmpty()) return;
        // One pixel wider all round, so the anti-aliased fringe of the outline is covered too.
        box = new Box(box.left() - 1, box.top() - 1, box.right() + 1, box.bottom() + 1).clamp(width, height);
        int boxWidth = box.width(), boxHeight = box.height();
        byte[] mask = new byte[boxWidth * boxHeight * 4];
        for (int j = 0; j < boxHeight; j++) {
            for (int i = 0; i < boxWidth; i++) {
                int px = box.left() + i, py = box.top() + j;
                if (inside[py * width + px] || Flood.touches(inside, width, height, px, py)) {
                    mask[(j * boxWidth + i) * 4 + 3] = (byte) 0xFF;
                }
            }
        }
        ImageInfo maskInfo = new ImageInfo(boxWidth, boxHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        Image shape = Image.Companion.makeRaster(maskInfo, mask, boxWidth * 4);
        Bitmap before = read(base, box);
        try (Paint paint = new Paint(); ColorFilter tint = ColorFilter.Companion.makeBlend(argb | 0xFF000000, BlendMode.SRC_IN)) {
            paint.setColorFilter(tint);
            paint.setAlphaf(opacity);
            base.getCanvas().drawImage(shape, box.left(), box.top(), paint);
        }
        shape.close();
        push(new Edit(box, before, read(base, box)));
        refresh(box);
    }

    public void clear() {
        if (live != null) return;
        Box box = whole();
        Bitmap before = read(base, box);
        base.getCanvas().clear(0);
        push(new Edit(box, before, read(base, box)));
        refresh(box);
    }

    /**
     * The color the screen shows at (x, y), with its alpha, or null outside the picture.
     */
    public Integer colorAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        try (Bitmap one = read(view, new Box(x, y, x + 1, y + 1))) {
            byte[] rgba = one.readPixels(new ImageInfo(1, 1, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), 4, 0, 0);
            if (rgba == null) return null;
            return (rgba[3] & 0xFF) << 24 | (rgba[0] & 0xFF) << 16 | (rgba[1] & 0xFF) << 8 | rgba[2] & 0xFF;
        }
    }

    public boolean canUndo() {
        return live == null && !undo.isEmpty();
    }

    public boolean canRedo() {
        return live == null && !redo.isEmpty();
    }

    public void undo() {
        if (!canUndo()) return;
        Step step = undo.peekLast();
        switch (step) {
            case Edit edit -> {
                base.writePixels(edit.before(), edit.box().left(), edit.box().top());
                refresh(edit.box());
            }
            case Resize resize -> {
                if (!reshape(resize.undoArea())) return;
                if (resize.lost() != null) base.writePixels(resize.lost(), 0, 0);
                refresh(whole());
            }
        }
        undo.pollLast();
        undoBytes -= step.bytes();
        redo.addLast(step);
        modified = true;
    }

    public void redo() {
        if (!canRedo()) return;
        Step step = redo.peekLast();
        switch (step) {
            case Edit edit -> {
                base.writePixels(edit.after(), edit.box().left(), edit.box().top());
                refresh(edit.box());
            }
            case Resize resize -> {
                if (!reshape(resize.area())) return;
            }
        }
        redo.pollLast();
        undo.addLast(step);
        undoBytes += step.bytes();
        modified = true;
    }

    private void push(Step step) {
        for (Step gone : redo) gone.close();
        redo.clear();
        undo.addLast(step);
        undoBytes += step.bytes();
        while (undoBytes > UNDO_BUDGET && undo.size() > 1) {
            Step oldest = undo.pollFirst();
            undoBytes -= oldest.bytes();
            oldest.close();
        }
        modified = true;
    }

    /**
     * Gives the picture the shape of {@code area}, a rectangle in the picture's present pixels that may reach past its
     * edges: what lies inside stays where it is relative to the area, and what the area adds is empty. This is how the
     * picture is cropped and made bigger, from any side. False, with nothing changed, if there is no memory for it.
     */
    private boolean reshape(Box area) {
        int newWidth = area.width(), newHeight = area.height();
        Surface nextBase = null, nextStroke = null, nextView = null;
        try {
            nextBase = layer(newWidth, newHeight);
            nextStroke = layer(newWidth, newHeight);
            nextView = layer(newWidth, newHeight);
        } catch (RuntimeException | OutOfMemoryError failure) {
            for (Surface made : new Surface[]{nextBase, nextStroke, nextView}) {
                if (made != null) made.close();
            }
            return false;
        }
        base.draw(nextBase.getCanvas(), -area.left(), -area.top(), null);
        if (shown != null) {
            shown.close();
            shown = null;
        }
        base.close();
        strokeLayer.close();
        view.close();
        if (committed != null) {
            committed.close();
            committed = null;
        }
        base = nextBase;
        strokeLayer = nextStroke;
        view = nextView;
        width = newWidth;
        height = newHeight;
        refresh(whole());
        return true;
    }

    /**
     * Cuts the picture to {@code area}, or makes it bigger, depending on whether the area is smaller or larger than
     * the picture; {@code area} is in the picture's present pixels, so (0, 0, 100, 100) keeps the top left corner and
     * (-50, 0, width, height) adds 50 pixels on the left. It is one step of undo. False if the size is not allowed,
     * there is no memory for it, or a stroke is in progress; the picture is then as it was.
     */
    public boolean resize(Box area) {
        if (live != null || area.isEmpty() || !fits(area.width(), area.height())) return false;
        if (area.equals(whole())) return true;
        boolean cuts = area.left() > 0 || area.top() > 0 || area.right() < width || area.bottom() < height;
        Bitmap lost = cuts ? read(base, whole()) : null;
        int oldWidth = width, oldHeight = height;
        if (!reshape(area)) {
            if (lost != null) lost.close();
            return false;
        }
        push(new Resize(area, oldWidth, oldHeight, lost));
        return true;
    }

    private static Bitmap read(Surface surface, Box box) {
        Bitmap bitmap = new Bitmap();
        bitmap.allocN32Pixels(box.width(), box.height(), false);
        surface.readPixels(bitmap, box.left(), box.top());
        return bitmap;
    }

    public byte[] toPng() {
        try (Surface flat = Surface.Companion.makeRasterN32Premul(width, height)) {
            flat.getCanvas().clear(paper);
            base.draw(flat.getCanvas(), 0, 0, null);
            try (Image image = flat.makeImageSnapshot();
                 Data data = image.encodeToData(EncodedImageFormat.PNG, 100, 6)) {
                return data.getBytes();
            }
        }
    }

    @Override
    public void close() {
        if (live != null) {
            live.close();
            live = null;
        }
        if (shown != null) shown.close();
        for (Step step : undo) step.close();
        for (Step step : redo) step.close();
        undo.clear();
        redo.clear();
        base.close();
        strokeLayer.close();
        if (committed != null) committed.close();
        view.close();
    }
}
