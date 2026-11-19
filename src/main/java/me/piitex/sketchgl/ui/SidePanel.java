package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.Element;
import me.piitex.engine.ui.color.Color;
import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.overlays.ColorPickerOverlay;
import me.piitex.engine.ui.overlays.SliderOverlay;
import me.piitex.engine.ui.overlays.TextOverlay;
import me.piitex.sketchgl.canvas.BrushSettings;
import me.piitex.sketchgl.canvas.Pen;
import me.piitex.sketchgl.canvas.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * The panel down the right edge: the color, and the settings of the tool in hand.
 */
class SidePanel extends Container {
    static final double WIDTH = 272;
    private static final double PAD = 16, INNER = WIDTH - 2 * PAD;
    private static final int PER_ROW = 8;
    private static final int[] PALETTE = {
            0x000000, 0x3a3a3a, 0x6b6b6b, 0x9c9c9c, 0xc8c8c8, 0xe6e6e6, 0xffffff, 0x7b4a2a,
            0xe53935, 0xfb8c00, 0xfdd835, 0x7cb342, 0x00897b, 0x039be5, 0x3949ab, 0x8e24aa,
            0xef9a9a, 0xffcc80, 0xfff59d, 0xc5e1a5, 0x80cbc4, 0x81d4fa, 0x9fa8da, 0xce93d8,
            0x8e0000, 0xbf5a00, 0xa68b00, 0x33691e, 0x004d40, 0x01579b, 0x1a237e, 0x4a148c};

    private final Pen pen;
    private final ColorPickerOverlay picker;
    private final List<Swatch> swatches = new ArrayList<>();
    private final Container recentRow = new Container(0, 0, INNER, 0);
    private final TextOverlay recentHeading = heading("RECENT");
    private final TextOverlay toolHeading = heading("BRUSH");
    private final BrushPreview preview;
    private final Setting size, opacity, hardness, smoothing;
    private boolean syncing;

    /** One slider with its name and the number beside it. */
    private final class Setting extends Container {
        final TextOverlay name = Style.text("", FONT, LABEL), value = Style.text("", FONT, TEXT);
        final SliderOverlay slider = new SliderOverlay(INNER, 0, 100, 0);
        final ToDoubleFunction<BrushSettings> read;
        final Function<Double, String> show;
        final double lowest, highest;
        final boolean curved;

        Setting(ToDoubleFunction<BrushSettings> read, BiConsumer<BrushSettings, Double> write,
                double lowest, double highest, boolean curved, Function<Double, String> show) {
            super(0, 0, INNER, 44);
            this.read = read;
            this.show = show;
            this.lowest = lowest;
            this.highest = highest;
            this.curved = curved;
            getStyling().setBackgroundColor(Color.TRANSPARENT);
            getStyling().setBorderThickness(0f);
            name.setPosition(0, 0);
            addElement(name);
            value.setPosition(INNER - 60, 0);
            addElement(value);
            slider.setPosition(0, 20);
            slider.setTrackColor(FIELD);
            slider.setFillColor(ACCENT);
            slider.setThumbColor(TEXT);
            slider.onValueChange(position -> {
                if (syncing) return;
                double amount = curved ? lowest * Math.pow(highest / lowest, position / 100.0) : lowest + (highest - lowest) * position / 100.0;
                write.accept(pen.settings(), amount);
                pen.changed();
            });
            addElement(slider);
        }

        void sync(String label) {
            double amount = read.applyAsDouble(pen.settings());
            double position = curved ? 100.0 * Math.log(amount / lowest) / Math.log(highest / lowest) : 100.0 * (amount - lowest) / (highest - lowest);
            name.setText(label);
            value.setText(show.apply(amount));
            value.setX(INNER - value.getWidth());
            slider.setValue(position);
        }
    }

    SidePanel(Pen pen) {
        super(0, 0, WIDTH, 100);
        this.pen = pen;
        getStyling().setBackgroundColor(PANEL);
        getStyling().setBorderThickness(0f);
        getStyling().setCornerRadius(0f);

        addElement(heading("COLOR"));

        picker = new ColorPickerOverlay(INNER, 36, fromArgb(pen.getColor()));
        picker.setAlphaEnabled(false);
        picker.setPresets();
        picker.onChange(color -> pen.setColor(argb(color)));
        addElement(picker);

        for (int hex : PALETTE) {
            Swatch swatch = new Swatch(hex, pen::setColor);
            swatches.add(swatch);
            addElement(swatch);
        }
        recentRow.getStyling().setBackgroundColor(Color.TRANSPARENT);
        recentRow.getStyling().setBorderThickness(0f);
        addElement(recentHeading);
        addElement(recentRow);

        addElement(toolHeading);
        preview = new BrushPreview(pen, INNER, 72);
        addElement(preview);
        size = new Setting(BrushSettings::getSize, (brush, value) -> brush.setSize(value.floatValue()),
                BrushSettings.MIN_SIZE, BrushSettings.MAX_SIZE, true, value -> Math.round(value) + " px");
        opacity = new Setting(BrushSettings::getOpacity, (brush, value) -> brush.setOpacity(value.floatValue()),
                0, 1, false, value -> Math.round(value * 100) + "%");
        hardness = new Setting(BrushSettings::getHardness, (brush, value) -> brush.setHardness(value.floatValue()),
                0, 1, false, value -> Math.round(value * 100) + "%");
        smoothing = new Setting(BrushSettings::getSmoothing, (brush, value) -> brush.setSmoothing(value.floatValue()),
                0, 1, false, value -> Math.round(value * 100) + "%");
        addElement(size);
        addElement(opacity);
        addElement(hardness);
        addElement(smoothing);

        pen.onChange(this::refresh);
        refresh();
    }

    private static TextOverlay heading(String text) {
        return Style.text(text, FONT_SMALL, DIM);
    }

    private void refresh() {
        syncing = true;
        try {
            Tool tool = pen.getTool();
            if (argb(picker.getColor()) != pen.getColor()) picker.setColor(fromArgb(pen.getColor()));
            for (Swatch swatch : swatches) swatch.setSelected(swatch.argb() == pen.getColor());
            rebuildRecent();

            boolean hasSettings = tool.strokes() || tool.kind() == Tool.Kind.FILL;
            toolHeading.setText(tool.label().toUpperCase());
            toolHeading.setEnabled(hasSettings);
            size.sync("Size");
            opacity.sync("Opacity");
            hardness.sync(tool.hardnessLabel() == null ? "" : tool.hardnessLabel());
            smoothing.sync("Smoothing");
            size.setEnabled(tool.hasSize());
            opacity.setEnabled(tool.hasOpacity());
            hardness.setEnabled(tool.hardnessLabel() != null);
            smoothing.setEnabled(tool.hasSmoothing());
            preview.setEnabled(tool.strokes());
            preview.redraw();
            layoutPanel();
        } finally {
            syncing = false;
        }
    }

    private void rebuildRecent() {
        List<Integer> recent = pen.recent();
        for (Element child : List.copyOf(recentRow.getElements().values())) recentRow.removeElement(child);
        for (int i = 0; i < recent.size(); i++) {
            Swatch swatch = new Swatch(recent.get(i), pen::setColor);
            swatch.setSelected(recent.get(i) == pen.getColor());
            swatch.setPosition(i % PER_ROW * (Swatch.SIZE + 4), i / PER_ROW * (Swatch.SIZE + 4));
            recentRow.addElement(swatch);
        }
        int rows = (recent.size() + PER_ROW - 1) / PER_ROW;
        recentRow.setHeight(Math.max(0, rows * (Swatch.SIZE + 4) - 4));
        recentHeading.setEnabled(!recent.isEmpty());
        recentRow.setEnabled(!recent.isEmpty());
    }

    private void layoutPanel() {
        double y = 18;
        findHeading("COLOR").setPosition(PAD, y);
        y += 22;
        picker.setPosition(PAD, y);
        y += 36 + 12;
        for (int i = 0; i < swatches.size(); i++) {
            swatches.get(i).setPosition(PAD + i % PER_ROW * (Swatch.SIZE + 4), y + i / PER_ROW * (Swatch.SIZE + 4));
        }
        y += swatches.size() / PER_ROW * (Swatch.SIZE + 4) + 8;
        if (recentRow.isEnabled()) {
            recentHeading.setPosition(PAD, y);
            y += 20;
            recentRow.setPosition(PAD, y);
            y += recentRow.getHeight() + 14;
        }
        y += 10;
        if (toolHeading.isEnabled()) {
            toolHeading.setPosition(PAD, y);
            y += 22;
        }
        if (preview.isEnabled()) {
            preview.setPosition(PAD, y);
            y += preview.getHeight() + 14;
        }
        for (Setting setting : new Setting[]{size, opacity, hardness, smoothing}) {
            if (!setting.isEnabled()) continue;
            setting.setPosition(PAD, y);
            y += 50;
        }
    }

    private TextOverlay findHeading(String text) {
        for (Element element : getElements().values()) {
            if (element instanceof TextOverlay overlay && overlay.getText().equals(text)) return overlay;
        }
        throw new IllegalStateException(text);
    }

    private static int argb(Color color) {
        return Style.argb(color);
    }

    private static Color fromArgb(int argb) {
        return Style.fromArgb(argb);
    }
}
