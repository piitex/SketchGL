package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.containers.Container;
import me.piitex.sketchgl.canvas.Pen;
import me.piitex.sketchgl.canvas.Tool;

import java.util.EnumMap;
import java.util.Map;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * The column of tools down the left edge.
 */
class Toolbox extends Container {
    static final double WIDTH = 56, BUTTON = 40, GAP = 6;

    private final Map<Tool, ToolButton> buttons = new EnumMap<>(Tool.class);

    Toolbox(Pen pen) {
        super(0, 0, WIDTH, 100);
        getStyling().setBackgroundColor(BACKGROUND);
        getStyling().setBorderThickness(0f);
        getStyling().setCornerRadius(0f);
        double y = 10;
        for (Tool tool : Tool.values()) {
            if (tool == Tool.FILL || tool == Tool.HAND) y += GAP * 2; // groups: draw, fill and look, move around
            ToolButton button = new ToolButton(Icons.of(tool), BUTTON, 22, tool.label() + "  (" + tool.key() + ")", () -> pen.setTool(tool));
            button.setPosition((WIDTH - BUTTON) / 2, y);
            addElement(button);
            buttons.put(tool, button);
            y += BUTTON + GAP;
        }
        pen.onChange(() -> buttons.forEach((tool, button) -> button.setOn(tool == pen.getTool())));
        buttons.get(pen.getTool()).setOn(true);
    }
}
