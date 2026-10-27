import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Builds the macOS app icon master from the full-bleed brand square: Apple's icon grid puts the artwork in an
 * 824 px rounded square (corner radius ~185 px) centred on a transparent 1024 px canvas, with a soft shadow below.
 * Usage: java scripts/MacIconMaster.java <source.png> <output.png>
 */
public final class MacIconMaster {
    private static final int CANVAS = 1024;
    private static final int BODY = 824;
    private static final double RADIUS = 185.4;
    private static final int SHADOW_OFFSET = 12;
    private static final int SHADOW_BLUR = 13;

    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        int inset = (CANVAS - BODY) / 2;
        RoundRectangle2D body = new RoundRectangle2D.Double(inset, inset, BODY, BODY, RADIUS * 2, RADIUS * 2);

        BufferedImage shadow = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D s = shadow.createGraphics();
        s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        s.setColor(new java.awt.Color(0, 0, 0, 90));
        s.translate(0, SHADOW_OFFSET);
        s.fill(body);
        s.dispose();
        shadow = blur(blur(shadow));

        BufferedImage icon = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(shadow, 0, 0, null);
        // Anti-aliased mask: draw the artwork into its own layer, then keep only the rounded body.
        BufferedImage artwork = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D a = artwork.createGraphics();
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        a.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        a.fill(body);
        a.setComposite(AlphaComposite.SrcIn);
        a.drawImage(source, inset, inset, BODY, BODY, null);
        a.dispose();
        g.drawImage(artwork, 0, 0, null);
        g.dispose();
        ImageIO.write(icon, "png", new File(args[1]));
    }

    private static BufferedImage blur(BufferedImage image) {
        int size = SHADOW_BLUR;
        float[] weights = new float[size * size];
        java.util.Arrays.fill(weights, 1f / weights.length);
        return new ConvolveOp(new Kernel(size, size, weights), ConvolveOp.EDGE_NO_OP, null).filter(image, null);
    }
}
