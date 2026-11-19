package me.piitex.sketchgl.ui;

import me.piitex.engine.Window;
import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.containers.ModalContainer;
import me.piitex.engine.ui.overlays.ButtonOverlay;
import me.piitex.engine.ui.overlays.CheckBoxOverlay;
import me.piitex.engine.ui.overlays.TextFieldOverlay;
import me.piitex.engine.ui.overlays.TextFlowOverlay;
import me.piitex.engine.ui.overlays.TextOverlay;
import me.piitex.sketchgl.canvas.Drawing;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * Boxes over the window: a message, a question, the new picture's size. The window behind cannot be used until the
 * box is closed.
 */
public final class Dialogs {
    private static final double WIDTH = 460, PADDING = 20, TEXT_TOP = 54, BUTTON_WIDTH = 112, BUTTON_HEIGHT = 32;
    private static int open;

    private Dialogs() {
    }

    public static boolean isOpen() {
        return open > 0;
    }

    private static ModalContainer box(Window window, double width, double height, String title) {
        ModalContainer modal = new ModalContainer(width, height);
        modal.setDismissOnBackdropClick(false);
        open++;
        modal.onClose(() -> open--);
        TextOverlay heading = new TextOverlay(title, FONT_DIALOG_TITLE, TEXT);
        heading.setTextColor(TEXT);
        heading.setPosition(PADDING, PADDING);
        modal.getContent().addElement(heading);
        return modal;
    }

    private static ButtonOverlay button(Container box, String label, double fromRight, double height, Runnable action) {
        ButtonOverlay button = new ButtonOverlay(label, FONT_HEADING, TEXT, BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setTextColor(TEXT);
        button.setPosition(WIDTH - PADDING - BUTTON_WIDTH - fromRight, height - PADDING - BUTTON_HEIGHT);
        button.onAction(action);
        box.addElement(button);
        return button;
    }

    public static void tell(Window window, String title, String message) {
        TextFlowOverlay text = new TextFlowOverlay(message, WIDTH - 2 * PADDING, FONT_HEADING, TEXT);
        double height = TEXT_TOP + text.getHeight() + 24 + BUTTON_HEIGHT + PADDING;
        ModalContainer modal = box(window, WIDTH, height, title);
        text.setPosition(PADDING, TEXT_TOP);
        modal.getContent().addElement(text);
        button(modal.getContent(), "OK", 0, height, modal::closeImmediately);
        modal.show(window);
    }

    public static void unsaved(Window window, String name, Runnable save, Runnable discard) {
        TextFlowOverlay text = new TextFlowOverlay("“" + name + "” has changes that have not been saved.", WIDTH - 2 * PADDING, FONT_HEADING, TEXT);
        double height = TEXT_TOP + text.getHeight() + 24 + BUTTON_HEIGHT + PADDING;
        ModalContainer modal = box(window, WIDTH, height, "Save changes?");
        text.setPosition(PADDING, TEXT_TOP);
        Container content = modal.getContent();
        content.addElement(text);
        button(content, "Cancel", 2 * (BUTTON_WIDTH + 8), height, modal::closeImmediately);
        ButtonOverlay dont = button(content, "Don't Save", BUTTON_WIDTH + 8, height, () -> {
            modal.closeImmediately();
            discard.run();
        });
        dont.setTextColor(WARNING);
        button(content, "Save", 0, height, () -> {
            modal.closeImmediately();
            save.run();
        });
        modal.show(window);
    }

    public record NewPicture(int width, int height, boolean transparent) {
    }

    private interface Chosen {
        void accept(int width, int height, boolean transparent);
    }

    public static void newPicture(Window window, int width, int height, Consumer<NewPicture> created) {
        sizeBox(window, "New picture", "Create", width, height, true,
                (newWidth, newHeight, transparent) -> created.accept(new NewPicture(newWidth, newHeight, transparent)));
    }

    public static void canvasSize(Window window, int width, int height, BiConsumer<Integer, Integer> resized) {
        sizeBox(window, "Canvas size", "Resize", width, height, false, (newWidth, newHeight, _) -> resized.accept(newWidth, newHeight));
    }

    private static void sizeBox(Window window, String title, String action, int width, int height, boolean asksTransparent, Chosen chosen) {
        double boxHeight = TEXT_TOP + 200 + 20 + 16 + BUTTON_HEIGHT + PADDING;
        ModalContainer modal = box(window, WIDTH, boxHeight, title);
        Container content = modal.getContent();

        TextFieldOverlay widthField = new TextFieldOverlay(110, 34);
        widthField.setText(String.valueOf(width));
        TextFieldOverlay heightField = new TextFieldOverlay(110, 34);
        heightField.setText(String.valueOf(height));
        content.addElement(label("Width", PADDING, TEXT_TOP));
        widthField.setPosition(PADDING, TEXT_TOP + 22);
        content.addElement(widthField);
        content.addElement(label("Height", PADDING + 140, TEXT_TOP));
        heightField.setPosition(PADDING + 140, TEXT_TOP + 22);
        content.addElement(heightField);
        content.addElement(label("pixels", PADDING + 262, TEXT_TOP + 31));

        TextOverlay readout = Style.text("", FONT, LABEL);
        readout.setPosition(PADDING, TEXT_TOP + 64);
        content.addElement(readout);

        CheckBoxOverlay keep = new CheckBoxOverlay(20, false);
        keep.setPosition(PADDING, TEXT_TOP + 170);
        content.addElement(keep);
        content.addElement(label("Keep the proportions", PADDING + 30, TEXT_TOP + 172));

        boolean[] typing = {false}; // set while the fields are changed from here, so that they do not answer
        double[] ratio = {(double) width / height};

        Runnable check = () -> {
            Integer typedWidth = number(widthField.getText()), typedHeight = number(heightField.getText());
            String problem = problem(typedWidth, typedHeight);
            readout.setText(problem != null ? problem : String.format("%.1f megapixels", (double) typedWidth * typedHeight / 1_000_000));
            readout.setTextColor(problem != null ? WARNING : LABEL);
        };
        Consumer<int[]> set = size -> {
            typing[0] = true;
            widthField.setText(String.valueOf(size[0]));
            heightField.setText(String.valueOf(size[1]));
            typing[0] = false;
            check.run();
        };
        widthField.onTextChanged(text -> {
            Integer typedWidth = number(text);
            if (!typing[0] && keep.isChecked() && typedWidth != null && typedWidth > 0) {
                typing[0] = true;
                heightField.setText(String.valueOf(Math.max(1, Math.round(typedWidth / ratio[0]))));
                typing[0] = false;
            }
            check.run();
        });
        heightField.onTextChanged(text -> {
            Integer typedHeight = number(text);
            if (!typing[0] && keep.isChecked() && typedHeight != null && typedHeight > 0) {
                typing[0] = true;
                widthField.setText(String.valueOf(Math.max(1, Math.round(typedHeight * ratio[0]))));
                typing[0] = false;
            }
            check.run();
        });
        keep.onToggle(checked -> {
            Integer typedWidth = number(widthField.getText()), typedHeight = number(heightField.getText());
            if (checked && typedWidth != null && typedHeight != null && typedWidth > 0 && typedHeight > 0) ratio[0] = (double) typedWidth / typedHeight;
        });

        String[] names = {"800×600", "1280×720", "1920×1080", "1024×1024", "2560×1440", "3840×2160", "4096×4096", "8192×8192"};
        int[][] sizes = {{800, 600}, {1280, 720}, {1920, 1080}, {1024, 1024}, {2560, 1440}, {3840, 2160}, {4096, 4096}, {8192, 8192}};
        double presetWidth = (WIDTH - 2 * PADDING - 3 * 8) / 4;
        for (int i = 0; i < names.length; i++) {
            int[] size = sizes[i];
            ButtonOverlay preset = new ButtonOverlay(names[i], FONT_SMALL, TEXT, presetWidth, 28);
            preset.setTextColor(TEXT);
            preset.setPosition(PADDING + i % 4 * (presetWidth + 8), TEXT_TOP + 94 + i / 4 * 34);
            preset.onAction(() -> {
                ratio[0] = (double) size[0] / size[1];
                set.accept(size);
            });
            content.addElement(preset);
        }

        CheckBoxOverlay transparent = new CheckBoxOverlay(20, false);
        if (asksTransparent) {
            transparent.setPosition(PADDING, TEXT_TOP + 200);
            content.addElement(transparent);
            content.addElement(label("Transparent background", PADDING + 30, TEXT_TOP + 202));
        } else {
            TextOverlay hint = Style.text("Keeps the top left. You can also drag the picture's corners.", FONT_SMALL, DIM);
            hint.setPosition(PADDING, TEXT_TOP + 204);
            content.addElement(hint);
        }

        Runnable go = () -> {
            Integer typedWidth = number(widthField.getText()), typedHeight = number(heightField.getText());
            if (problem(typedWidth, typedHeight) != null) return;
            modal.closeImmediately();
            chosen.accept(typedWidth, typedHeight, transparent.isChecked());
        };
        button(content, "Cancel", BUTTON_WIDTH + 8, boxHeight, modal::closeImmediately);
        button(content, action, 0, boxHeight, go);
        widthField.onSubmit(_ -> go.run());
        heightField.onSubmit(_ -> go.run());
        check.run();
        modal.show(window);
        window.requestFocus(widthField);
        widthField.selectAll();
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static String problem(Integer width, Integer height) {
        if (width == null || height == null) return "Type a width and a height in pixels.";
        if (width < Drawing.MIN_SIDE || height < Drawing.MIN_SIDE || width > Drawing.MAX_SIDE || height > Drawing.MAX_SIDE) {
            return "Each side must be between " + Drawing.MIN_SIDE + " and " + Drawing.MAX_SIDE + " pixels.";
        }
        long pixels = (long) width * height;
        if (pixels > Drawing.MAX_PIXELS) {
            return String.format("That is %.0f megapixels; the most is %d.", pixels / 1e6, Drawing.MAX_PIXELS / 1_000_000);
        }
        return null;
    }

    private static TextOverlay label(String text, double x, double y) {
        TextOverlay overlay = Style.text(text, FONT, LABEL);
        overlay.setPosition(x, y);
        return overlay;
    }
}
