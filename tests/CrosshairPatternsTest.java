import com.customcrosshair.CrosshairPatterns;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** No Fabric runtime required. Run with the command in tests/README.md. */
public final class CrosshairPatternsTest {
    // Published sample share codes:
    // https://www.lunarclient.com/news/how-to-make-a-custom-crosshair-on-lunar-client
    private static final List<String> OFFICIAL = List.of(
        "LCCH-11-APCfgAS0aEUJSED+QwgAAA",
        "LCCH-15-8AcEBOGDCGIEKgIVQQofBYgCRAHCfEGSIElgGAA",
        "LCCH-23-AOA/APg/AOwfAP4PAP8HgP8DwAcA4B8C+AABf4Dh/8D5X+D/D/D/B/D/A/D/APB/APAfAPAHAHADABgBAIQAAMYAAA",
        "LCCH-12-AAAAGMN7/u///u///Ic/8AEO"
    );

    public static void main(String[] args) {
        int[] sizes = {11, 15, 23, 12};
        for (int i = 0; i < OFFICIAL.size(); i++) {
            String code = OFFICIAL.get(i);
            boolean[][] pixels = CrosshairPatterns.importCode(code);
            check(pixels != null && pixels.length == sizes[i], "sample dimension " + i);
            check(code.equals(CrosshairPatterns.exportCode(pixels)), "sample byte-for-byte round trip " + i);
        }
        // The heart has two lobes at the top and a single point at the bottom, not a transpose.
        boolean[][] heart = CrosshairPatterns.importCode(OFFICIAL.get(3));
        check(row(heart[2]).equals("...##...##.."), "heart lobes");
        check(row(heart[11]).equals(".....###...."), "heart tip");
        boolean[][] dinosaur = CrosshairPatterns.importCode(OFFICIAL.get(2));
        check(row(dinosaur[0]).equals(".............#########."), "dinosaur head orientation");
        check(row(dinosaur[22]).equals(".......##...##........."), "dinosaur feet orientation");

        check("LCCH-3-AQE".equals(CrosshairPatterns.exportCode(new boolean[][] {
            {true, false, false}, {false, false, false}, {false, false, true}
        })), "explicit packed-bit layout");
        check(CrosshairPatterns.importCode("  LCCH-1-AQ==  ")[0][0], "paste whitespace and explicit padding");
        for (String invalid : List.of("", "1-AQ", "LCCH-0-AQ", "LCCH-09-AA", "LCCH-99-AQ", "LCCH-1-A!",
            "LCCH-2-////", "LCCH-1-Ag", "LCCH-15-AQ", "LCCH-1-AQ-oops", "LCCH-1-", "LCCH--AQ")) {
            check(CrosshairPatterns.importCode(invalid) == null, "reject " + invalid);
        }
        check(CrosshairPatterns.importCode(null) == null, "null import");
        check(CrosshairPatterns.exportCode(new boolean[][]{{true}, {false, true}}) == null, "ragged input");

        for (int n = 1; n <= CrosshairPatterns.MAX_SIZE; n++) {
            Random random = new Random(n);
            boolean[][] grid = new boolean[n][n];
            for (boolean[] r : grid) for (int x = 0; x < n; x++) r[x] = random.nextBoolean();
            check(Arrays.deepEquals(grid, CrosshairPatterns.importCode(CrosshairPatterns.exportCode(grid))), "size " + n);
        }
        boolean[][] blank = CrosshairPatterns.blank();
        check(blank.length == 16 && blank[0].length == 16, "new editor starts at 16×16");
        blank[15][15] = true;
        String canvasCode = CrosshairPatterns.exportCode(blank);
        check(canvasCode.startsWith("LCCH-16-")
            && Arrays.deepEquals(blank, CrosshairPatterns.importCode(canvasCode)), "16×16 editor code");
        boolean[][] paddedHeart = CrosshairPatterns.centerOnCanvas(heart, 16);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            check(heart[y][x] == paddedHeart[y + 2][x + 2], "12×12 heart remains centered");
        }
        check(OFFICIAL.get(3).equals(CrosshairPatterns.exportCode(heart)), "padding did not mutate the heart");
        boolean[][] dot = CrosshairPatterns.centerOnCanvas(new boolean[][]{{true}}, 16);
        check(dot[8][8] && !dot[7][7], "odd-to-even padding preserves the reticle anchor");
        check(CrosshairPatterns.centerOnCanvas(dinosaur, 16) == dinosaur, "larger imports keep native dimensions");
        check(CrosshairPatterns.centerOnCanvas(new boolean[][]{{true}, {false, true}}, 16) == null, "invalid canvas input");

        boolean[][] plus = CrosshairPatterns.simple(4, 2, 1, false);
        check(row(plus[6]).equals("####.....####"), "simple center row and gap");
        check(!plus[6][6] && CrosshairPatterns.simple(4, 2, 1, true)[6][6], "simple dot toggle");
        check(CrosshairPatterns.simple(1, 0, 7, false).length == 7, "thick tiny crosshair is safe");
        check(CrosshairPatterns.simple(99, 99, 99, true).length == 45, "bounds");
        int[] referenceCounts = {17, 29, 12, 21, 17, 17, 21, 16, 25, 24, 17, 33, 28, 20};
        check(CrosshairPatterns.PRESET_NAMES.size() == referenceCounts.length, "all fourteen presets");
        for (int i = 0; i < referenceCounts.length; i++) {
            var preset = CrosshairPatterns.preset(i);
            String code = CrosshairPatterns.exportCode(preset);
            check(code != null && CrosshairPatterns.importCode(code) != null, "valid preset " + i);
            int count = 0;
            for (boolean[] line : preset) for (boolean pixel : line) if (pixel) count++;
            check(count == referenceCounts[i], "reference area for preset " + i);
        }
        check(row(CrosshairPatterns.preset(2)[3]).equals("...#..."), "chevron apex sits at reticle center");
        System.out.println("CrosshairPatterns: 4 official samples, invalid input, all supported sizes, simple and presets passed.");
    }

    private static String row(boolean[] pixels) {
        StringBuilder result = new StringBuilder(pixels.length);
        for (boolean pixel : pixels) result.append(pixel ? '#' : '.');
        return result.toString();
    }

    private static void check(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
