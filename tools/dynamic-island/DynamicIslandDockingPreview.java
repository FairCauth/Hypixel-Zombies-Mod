import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.DynamicIslandHudRenderer;
import com.example.client.skia.render.LiquidGlassShader;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Actual Skia frames and behavioral checks for staged docking and the liquid neck. */
public final class DynamicIslandDockingPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/docking");
        Files.createDirectories(output.resolve("frames"));
        verifySequence(output);
        verifyMaterialContinuity();
        if (args.length > 0) renderAnimation(Path.of(args[0]), output);
        System.out.println("Docking checks passed: overlapping docking and expansion, stable outer rim at both transitions, continuous glass neck, collapse before separation, reversal, task ending during merge, frame rates, balanced canvas.");
    }

    private static void verifySequence(Path output) throws Exception {
        try (DynamicIsland island = new DynamicIsland(); Surface target = Surface.makeRasterN32Premul(900, 300)) {
            island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
            island.advance(1);
            draw(island, target);
            require(close(island.motionSnapshot().satelliteGap(), 8, 0.1), "resting pair remains detached");
            island.expandSecondary();
            island.advance(0.04);
            draw(island, target);
            var approach = island.motionSnapshot();
            require(approach.merging() && approach.reveal() == 0 && close(approach.height(), 36, 0.01), "details wait for docking");
            require(approach.satelliteGap() < 8 && approach.bridgeBlend() > 0, "nearby shells attract");
            save(target, output.resolve("01-attraction.png"));
            // No content at the gap's midpoint: its alpha comes from the joined glass surface.
            float right = 450 + approach.width() / 2;
            require(approach.satelliteGap() > 0 && approach.satelliteGap() < 4, "sample captures two nearby but nonoverlapping shells");
            BufferedImage pixels = decode(target);
            int alpha = pixels.getRGB(Math.round(right + approach.satelliteGap() / 2), 78) >>> 24;
            require(alpha > 40, "neck fills the gap without separate overlapping rims");
            island.advance(0.10);
            draw(island, target);
            var overlap = island.motionSnapshot();
            require(!overlap.merging() && overlap.docking() < 0.985f && overlap.reveal() > 0.05f
                    && overlap.height() > 36, "details start while docking is still in motion");
            save(target, output.resolve("02-overlap.png"));
            island.advance(0.35);
            draw(island, target);
            require(!island.motionSnapshot().merging() && island.motionSnapshot().reveal() > 0.15,
                    "overlapping animation continues into expanded details");
            save(target, output.resolve("03-expanding.png"));
            island.advance(1);
            island.setExpanded(false);
            island.advance(0.10);
            draw(island, target);
            require(island.motionSnapshot().docking() > 0.98, "collapse holds the pair together");
            island.advance(1);
            draw(island, target);
            require(close(island.motionSnapshot().satelliteGap(), 8, 0.1), "compact pair separates again");
            island.expandSecondary();
            island.advance(0.08);
            draw(island, target);
            island.setExpanded(false);
            island.advance(1);
            draw(island, target);
            require(!island.motionSnapshot().merging() && close(island.motionSnapshot().satelliteGap(), 8, 0.1),
                    "interrupted docking returns to detached state");
            island.expandSecondary();
            island.advance(0.08);
            island.endTask("timer");
            island.advance(1);
            draw(island, target);
            require(!island.isExpanded() && !island.motionSnapshot().merging()
                    && close(island.motionSnapshot().width(), 154, 0.1), "task ending cancels pending expansion");
        }
        for (int fps : new int[]{30, 144}) {
            try (DynamicIsland island = new DynamicIsland()) {
                island.startTask("timer", DynamicIsland.Activity.TIMER, 0);
                island.advance(1);
                island.expandSecondary();
                for (int frame = 0; frame < fps; frame++) island.advance(1.0 / fps);
                require(close(island.motionSnapshot().height(), 116, 0.2), "staged animation settles at " + fps + " FPS");
            }
        }
    }

    private static void verifyMaterialContinuity() throws Exception {
        try (Surface wallpaper = Surface.makeRasterN32Premul(360, 160);
             Surface single = Surface.makeRasterN32Premul(360, 160);
             Surface pair = Surface.makeRasterN32Premul(360, 160);
             Paint stripe = new Paint(); LiquidGlassShader glass = new LiquidGlassShader()) {
            wallpaper.getCanvas().clear(0xFF606870);
            for (int x = 0; x < 360; x += 20) {
                stripe.setColor(x % 40 == 0 ? 0xFFB5C2CF : 0xFF354651);
                wallpaper.getCanvas().drawRect(Rect.makeXYWH(x, 0, 10, 160), stripe);
            }
            try (Image source = wallpaper.makeImageSnapshot()) {
                for (boolean withBackground : new boolean[]{false, true}) {
                    Image background = withBackground ? source : null;
                    single.getCanvas().clear(0);
                    glass.drawJoined(single.getCanvas(), background, background, 100, 60, 180, 36, 18,
                            null, 0, 1, 1, 0, 0, null);
                    BufferedImage baseline = decode(single);
                    for (Rect satellite : new Rect[]{Rect.makeXYWH(288, 60, 60, 36), Rect.makeXYWH(190, 60, 60, 36)}) {
                        pair.getCanvas().clear(0);
                        glass.drawJoined(pair.getCanvas(), background, background, 100, 60, 180, 36, 18,
                                satellite, 0, 1, 1, 0, 0, null);
                        BufferedImage result = decode(pair);
                        for (int y = 59; y <= 96; y++) for (int x = 99; x <= 280; x++) {
                            int before = baseline.getRGB(x, y), after = result.getRGB(x, y);
                            for (int channel = 0; channel < 4; channel++) {
                                int shift = channel * 8;
                                require(Math.abs((before >>> shift & 255) - (after >>> shift & 255)) <= 2,
                                        "same border and glaze for detached/docked shells, background=" + withBackground);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void renderAnimation(Path screenshot, Path output) throws Exception {
        BufferedImage source = ImageIO.read(screenshot.toFile());
        BufferedImage crop = source.getSubimage((source.getWidth() - 900) / 2, 31, 900, 400);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(crop, "png", png);
        try (Image sharp = Image.makeFromEncoded(png.toByteArray());
             Surface blurTarget = Surface.makeRasterN32Premul(900, 400);
             Surface target = Surface.makeRasterN32Premul(900, 400);
             ImageFilter blur = ImageFilter.makeBlur(10, 10, FilterTileMode.CLAMP);
             Paint blurPaint = new Paint().setImageFilter(blur);
             Paint textPaint = new Paint().setColor(0xFFFFFFFF).setAntiAlias(true);
             Paint labelPaint = new Paint().setColor(0xBB111827).setAntiAlias(true);
             Typeface typeface = Typeface.makeFromName("Segoe UI", FontStyle.NORMAL);
             Font font = new Font(typeface, 16);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            blurTarget.getCanvas().drawImage(sharp, 0, 0, blurPaint);
            try (Image blurred = blurTarget.makeImageSnapshot()) {
                hud.configure(DynamicIsland.Activity.MUSIC, DynamicIsland.Activity.TIMER, false, false, false);
                hud.advance(1);
                for (int frame = 0; frame < 210; frame++) {
                    if (frame == 30 || frame == 140) hud.island().expandSecondary();
                    if (frame == 100) hud.island().setExpanded(false);
                    if (frame == 195) hud.island().endTask("demo-secondary");
                    hud.advance(1.0 / 60);
                    Canvas c = target.getCanvas();
                    c.clear(0);
                    c.drawImage(sharp, 0, 0);
                    int saved = c.save();
                    c.scale(2, 2);
                    hud.draw(c, 450, 200, 0.9f, 10, sharp, blurred, 2, 2);
                    c.restoreToCount(saved);
                    var motion = hud.island().motionSnapshot();
                    String label = motion.merging() ? "Attract" : hud.island().isExpanded()
                            ? motion.docking() < 0.98 ? "Dock and expand together" : "Expanded activity"
                            : motion.docking() > 0.04 ? "Collapse, then separate" : "Two independent activities";
                    c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(280, 350, 340, 32, 16), labelPaint);
                    c.drawString(label, (900 - font.measureTextWidth(label)) / 2, 372, font, textPaint);
                    require(c.getSaveCount() == 1, "joined shader restores canvas state");
                    save(target, output.resolve("frames").resolve(String.format("%04d.png", frame)));
                    if (frame == 32) save(target, output.resolve("game-neck.png"));
                    if (frame == 39) save(target, output.resolve("game-merged.png"));
                    if (frame == 57) save(target, output.resolve("game-expanding.png"));
                }
                require(!sharp.isClosed() && !blurred.isClosed(), "joined optics borrow source frames");
            }
        }
    }

    private static void draw(DynamicIsland island, Surface surface) {
        surface.getCanvas().clear(0);
        island.draw(surface.getCanvas(), 450, 60);
        require(surface.getCanvas().getSaveCount() == 1, "balanced canvas");
    }

    private static BufferedImage decode(Surface surface) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
            return ImageIO.read(new ByteArrayInputStream(data.getBytes()));
        }
    }

    private static void save(Surface surface, Path path) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) { Files.write(path, data.getBytes()); }
    }

    private static boolean close(float a, float b, double tolerance) { return Math.abs(a - b) < tolerance; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
