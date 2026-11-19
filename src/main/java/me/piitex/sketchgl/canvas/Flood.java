package me.piitex.sketchgl.canvas;

/**
 * Flood fill over a block of 4-byte pixels.
 */
final class Flood {
    private Flood() {
    }

    /**
     * Marks in {@code inside} every pixel that can be reached from (x, y) over pixels whose channels each differ from
     * the one at (x, y) by at most {@code tolerance} (0 to 255). Returns the rectangle holding them.
     */
    static Drawing.Box fill(byte[] pixels, int width, int height, int x, int y, int tolerance, boolean[] inside) {
        int start = (y * width + x) * 4;
        int red = pixels[start] & 0xFF, green = pixels[start + 1] & 0xFF, blue = pixels[start + 2] & 0xFF, alpha = pixels[start + 3] & 0xFF;
        int[] stack = new int[1024];
        int top = 0;
        stack[top++] = y * width + x;
        int minX = x, maxX = x, minY = y, maxY = y;
        while (top > 0) {
            int index = stack[--top];
            int py = index / width, px = index % width;
            if (inside[index] || !similar(pixels, index, red, green, blue, alpha, tolerance)) continue;
            // Walk out to both ends of this row, then look above and below the whole run.
            int left = px, right = px;
            while (left > 0 && !inside[py * width + left - 1] && similar(pixels, py * width + left - 1, red, green, blue, alpha, tolerance)) left--;
            while (right < width - 1 && !inside[py * width + right + 1] && similar(pixels, py * width + right + 1, red, green, blue, alpha, tolerance)) right++;
            for (int i = left; i <= right; i++) inside[py * width + i] = true;
            minX = Math.min(minX, left);
            maxX = Math.max(maxX, right);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
            for (int row = py - 1; row <= py + 1; row += 2) {
                if (row < 0 || row >= height) continue;
                boolean open = false;
                for (int i = left; i <= right; i++) {
                    int next = row * width + i;
                    boolean fillable = !inside[next] && similar(pixels, next, red, green, blue, alpha, tolerance);
                    if (fillable && !open) {
                        if (top == stack.length) stack = java.util.Arrays.copyOf(stack, top * 2);
                        stack[top++] = next;
                    }
                    open = fillable;
                }
            }
        }
        return new Drawing.Box(minX, minY, maxX + 1, maxY + 1);
    }

    private static boolean similar(byte[] pixels, int index, int red, int green, int blue, int alpha, int tolerance) {
        int offset = index * 4;
        return Math.abs((pixels[offset] & 0xFF) - red) <= tolerance && Math.abs((pixels[offset + 1] & 0xFF) - green) <= tolerance
                && Math.abs((pixels[offset + 2] & 0xFF) - blue) <= tolerance && Math.abs((pixels[offset + 3] & 0xFF) - alpha) <= tolerance;
    }

    static boolean touches(boolean[] inside, int width, int height, int x, int y) {
        return x > 0 && inside[y * width + x - 1] || x < width - 1 && inside[y * width + x + 1]
                || y > 0 && inside[(y - 1) * width + x] || y < height - 1 && inside[(y + 1) * width + x];
    }
}
