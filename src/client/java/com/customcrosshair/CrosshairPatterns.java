package com.customcrosshair;

import java.util.Base64;
import java.util.List;

public final class CrosshairPatterns {
    public static final int MAX_SIZE = 63;
    public static final int EDITOR_SIZE = 16;
    public static final int MIN_LENGTH = 1, MAX_LENGTH = 12;
    public static final int MIN_GAP = 0, MAX_GAP = 10;
    public static final int MIN_THICKNESS = 1, MAX_THICKNESS = 7;

    public static final List<String> PRESET_NAMES = List.of(
        "Classic", "Bracket Cross", "Chevron", "Solid Circle", "Ring Dot", "Square Dot",
        "Tipped Cross", "Open Tipped Cross", "Bracket Dot", "Open Brackets",
        "Side Ring", "Broad X", "Open X", "Diamond"
    );

    private CrosshairPatterns() {}

    public static boolean[][] simple(int length, int gap, int thickness, boolean dot) {
        length = Math.clamp(length, MIN_LENGTH, MAX_LENGTH);
        gap = Math.clamp(gap, MIN_GAP, MAX_GAP);
        thickness = Math.clamp(thickness, MIN_THICKNESS, MAX_THICKNESS);
        int mid = Math.max(gap + length, thickness / 2);
        boolean[][] grid = new boolean[mid * 2 + 1][mid * 2 + 1];
        for (int arm = gap + 1; arm <= gap + length; arm++) {
            for (int t = -((thickness - 1) / 2); t <= thickness / 2; t++) {
                grid[mid + t][mid - arm] = true;
                grid[mid + t][mid + arm] = true;
                grid[mid - arm][mid + t] = true;
                grid[mid + arm][mid + t] = true;
            }
        }
        grid[mid][mid] = dot;
        return grid;
    }

    public static boolean[][] preset(int index) {
        return switch (Math.clamp(index, 0, PRESET_NAMES.size() - 1)) {
            case 1 -> rows(
                "....#....",
                ".##.#.##.",
                ".#..#..#.",
                "....#....",
                "#########",
                "....#....",
                ".#..#..#.",
                ".##.#.##.",
                "....#....");
            case 2 -> rows(
                ".......",
                ".......",
                ".......",
                "...#...",
                "..###..",
                ".##.##.",
                "##...##");
            case 3 -> rows(
                ".###.",
                "#####",
                "#####",
                "#####",
                ".###.");
            case 4 -> rows(
                "..###..",
                ".#...#.",
                "#.....#",
                "#..#..#",
                "#.....#",
                ".#...#.",
                "..###..");
            case 5 -> rows(
                "#####",
                "#...#",
                "#.#.#",
                "#...#",
                "#####");
            case 6 -> rows(
                "..###..",
                "...#...",
                "#..#..#",
                "#######",
                "#..#..#",
                "...#...",
                "..###..");
            case 7 -> rows(
                "..###..",
                "...#...",
                "#.....#",
                "##...##",
                "#.....#",
                "...#...",
                "..###..");
            case 8 -> rows(
                "....#....",
                ".##.#.##.",
                ".#..#..#.",
                ".........",
                "###.#.###",
                ".........",
                ".#..#..#.",
                ".##.#.##.",
                "....#....");
            case 9 -> rows(
                "....#....",
                ".##.#.##.",
                ".#..#..#.",
                ".........",
                "###...###",
                ".........",
                ".#..#..#.",
                ".##.#.##.",
                "....#....");
            case 10 -> rows(
                ".........",
                ".........",
                "...###...",
                "..#...#..",
                "###.#.###",
                "..#...#..",
                "...###...",
                ".........",
                ".........");
            case 11 -> rows(
                "##...##",
                "###.###",
                ".#####.",
                "..###..",
                ".#####.",
                "###.###",
                "##...##");
            case 12 -> rows(
                "##...##",
                "###.###",
                ".##.##.",
                ".......",
                ".##.##.",
                "###.###",
                "##...##");
            case 13 -> rows(
                "...#...",
                "..###..",
                ".##.##.",
                "##...##",
                ".##.##.",
                "..###..",
                "...#...");
            default -> rows(
                "....#....",
                "....#....",
                "....#....",
                "....#....",
                "#########",
                "....#....",
                "....#....",
                "....#....",
                "....#....");
        };
    }

    public static boolean[][] blank() {
        return new boolean[EDITOR_SIZE][EDITOR_SIZE];
    }

    /** Pads a smaller square to the editor canvas while keeping the reticle anchor fixed. */
    public static boolean[][] centerOnCanvas(boolean[][] source, int minimumSide) {
        if (source == null || source.length < 1 || source.length > MAX_SIZE) return null;
        int side = source.length;
        for (boolean[] row : source) if (row == null || row.length != side) return null;
        int canvasSide = Math.clamp(minimumSide, 1, MAX_SIZE);
        if (side >= canvasSide) return source;
        int offset = canvasSide / 2 - side / 2;
        boolean[][] canvas = new boolean[canvasSide][canvasSide];
        for (int y = 0; y < side; y++) {
            System.arraycopy(source[y], 0, canvas[y + offset], offset, side);
        }
        return canvas;
    }

    private static boolean[][] rows(String... lines) {
        int width = 0;
        for (String line : lines) width = Math.max(width, line.length());
        int side = Math.max(width, lines.length) | 1;
        int left = (side - width) / 2, top = (side - lines.length) / 2;
        boolean[][] result = new boolean[side][side];
        for (int y = 0; y < lines.length; y++) for (int x = 0; x < lines[y].length(); x++) {
            result[y + top][x + left] = lines[y].charAt(x) == '#';
        }
        return result;
    }

    /**
     * Share-code encoding inferred from the four examples published in Lunar Client's
     * "How to make a custom crosshair on Lunar Client". No game/client code is used.
     */
    public static String exportCode(boolean[][] grid) {
        if (grid == null || grid.length < 1 || grid.length > MAX_SIZE) return null;
        int n = grid.length;
        byte[] bytes = new byte[(n * n + 7) / 8];
        for (int y = 0; y < n; y++) {
            if (grid[y] == null || grid[y].length != n) return null;
            for (int x = 0; x < n; x++) {
                int bit = y * n + x;
                if (grid[y][x]) bytes[bit >>> 3] |= (byte) (1 << (bit & 7));
            }
        }
        return "LCCH-" + n + "-" + Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean[][] importCode(String text) {
        if (text == null) return null;
        String code = text.strip();
        int maxEncoded = ((MAX_SIZE * MAX_SIZE + 7) / 8 + 2) / 3 * 4;
        if (code.length() > 8 + maxEncoded || !code.startsWith("LCCH-")) return null;
        int separator = code.indexOf('-', 5);
        if (separator < 6 || separator > 7) return null;
        int n = 0;
        for (int i = 5; i < separator; i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9' || i == 5 && c == '0') return null;
            n = n * 10 + c - '0';
        }
        if (n < 1 || n > MAX_SIZE) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(code.substring(separator + 1));
        } catch (IllegalArgumentException badBase64) {
            return null;
        }
        if (bytes.length != (n * n + 7) / 8) return null;
        // Disallow nonzero padding in the final byte so a code always represents a full square.
        int used = n * n & 7;
        if (used != 0 && ((bytes[bytes.length - 1] & 0xFF) >>> used) != 0) return null;
        boolean[][] result = new boolean[n][n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int bit = y * n + x;
            result[y][x] = ((bytes[bit >>> 3] >>> (bit & 7)) & 1) != 0;
        }
        return result;
    }
}
