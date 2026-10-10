import com.example.client.skia.render.dynamicisland.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Check visible glyph alignment with actual Skia pixels, including Chinese and Latin titles. */
public final class DynamicIslandCompactPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/compact");
        Files.createDirectories(output);
        for (String title : new String[]{"在希望田", "晴天", "Night Drive", "音乐 Music", "一首特别长的歌曲标题测试省略"}) {
            try (DynamicIsland island = new DynamicIsland(); Surface surface = Surface.makeRasterN32Premul(600, 160)) {
                island.endTask("demo-main");
                island.updateLiveTask("music", new IslandLiveData.Music(title, "", "", true, 0, 0,
                        System.nanoTime(), false, "", null, true, true, true), 10);
                island.advance(1);
                Canvas canvas = surface.getCanvas();
                canvas.clear(0);
                canvas.scale(3, 3);
                island.draw(canvas, 100, 8);
                try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                    byte[] png = data.getBytes();
                    Files.write(output.resolve(title.replace(' ', '-') + ".png"), png);
                    BufferedImage pixels = ImageIO.read(new ByteArrayInputStream(png));
                    // Music capsule x=23; title x=38, waveform begins x=124. Exclude icons and glass rims.
                    int top = 160, bottom = -1;
                    for (int y = 45; y < 110; y++) for (int x = 183; x < 350; x++) {
                        int color = pixels.getRGB(x, y);
                        int alpha = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                        if (alpha > 180 && r > 220 && g > 220 && b > 220) {
                            top = Math.min(top, y); bottom = Math.max(bottom, y);
                        }
                    }
                    if (bottom < top) throw new AssertionError("Missing title pixels: " + title);
                    // Cover/icon center=(8+18)*3=78; hinted pixel coverage allows 2px at 3x scale.
                    if (Math.abs((top + bottom + 1) / 2f - 78) > 2)
                        throw new AssertionError("Off-center title " + title + ": " + top + ".." + bottom);
                    System.out.println("Aligned compact title: " + title + " (pixels " + top + ".." + bottom + ")");
                }
            }
        }
        System.out.println("Compact typography checks passed: Chinese, Latin, mixed and ellipsized titles match cover/icon center.");
    }
}
