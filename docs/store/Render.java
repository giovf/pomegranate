import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the Pomegranate store graphics (design direction J, the cartoon
 * character) from the same geometry as the launcher icon and the SVG sources
 * next to this file. The container has no SVG rasteriser, hence this.
 *
 * Run from the repository root: java docs/store/Render.java docs/store
 */
public class Render {
    static final Color GROUND = new Color(0xFFE3D3), INK = new Color(0x4A0A18), FACE = new Color(0x3A0812);
    static final Color BODY = new Color(0xD62645), BODY_LIGHT = new Color(0xEE4460), CROWN = new Color(0xC7203F);
    static final Color CHEEK = new Color(0xFF8FA3), SEED = new Color(0xFF5C7A);

    static Graphics2D g(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static Shape ellipse(double x, double y, double rx, double ry) { return new Ellipse2D.Double(x - rx, y - ry, 2 * rx, 2 * ry); }

    static Color alpha(Color c, double a) { return new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, (float) a); }

    static BasicStroke stroke(double w) { return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); }

    /** The seed outline, point up, in its own units (23 tall); callers scale it. */
    static Path2D teardrop() {
        Path2D p = new Path2D.Double();
        p.moveTo(0, -11);
        p.curveTo(5.5, -4, 7, 2, 7, 5);
        p.append(new Arc2D.Double(-7, -2, 14, 14, 0, -180, Arc2D.OPEN), true);
        p.curveTo(-7, 2, -5.5, -4, 0, -11);
        p.closePath();
        return p;
    }

    /** The character in the 108-unit launcher viewport; caller sets the transform. */
    static void character(Graphics2D g) {
        g.setColor(alpha(INK, 0.18));
        g.fill(ellipse(54, 84.5, 15, 2.6));

        Path2D crown = new Path2D.Double();
        double[][] pts = {{45.5, 39.5}, {43.5, 26.5}, {49.5, 31.5}, {54, 24.5}, {58.5, 31.5}, {64.5, 26.5}, {62.5, 39.5}};
        crown.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) crown.lineTo(pts[i][0], pts[i][1]);
        crown.closePath();
        g.setColor(CROWN); g.fill(crown);
        g.setColor(INK); g.setStroke(stroke(2.6)); g.draw(crown);

        Shape body = ellipse(54, 59, 22, 22);
        g.setColor(BODY); g.fill(body);
        Shape clip = g.getClip();
        g.clip(body);
        g.setColor(BODY_LIGHT); g.fill(ellipse(50, 55, 21, 21));
        g.setClip(clip);
        g.setColor(INK); g.setStroke(stroke(2.6)); g.draw(body);

        Path2D shine = new Path2D.Double();
        shine.moveTo(40.5, 51);
        shine.curveTo(41.5, 46.5, 44.5, 43.5, 48.5, 42);
        g.setColor(alpha(Color.WHITE, 0.9)); g.setStroke(stroke(2.4)); g.draw(shine);

        g.setColor(alpha(CHEEK, 0.85));
        g.fill(ellipse(42.5, 64, 2.7, 2.7));
        g.fill(ellipse(65.5, 64, 2.7, 2.7));

        g.setColor(FACE);
        g.fill(ellipse(47, 58, 2.1, 2.9));
        g.fill(ellipse(61, 58, 2.1, 2.9));
        g.setColor(Color.WHITE);
        g.fill(ellipse(47.7, 56.9, 0.75, 0.75));
        g.fill(ellipse(61.7, 56.9, 0.75, 0.75));
        g.setColor(FACE); g.setStroke(stroke(2));
        g.draw(new QuadCurve2D.Double(49.5, 65, 54, 70, 58.5, 65));
    }

    /** One seed at (x, y) in the current units, rotated, at the given size (1 = icon size). */
    static void seed(Graphics2D g, double x, double y, double degrees, double size) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(degrees));
        g.scale(0.36 * size, 0.36 * size);
        Path2D t = teardrop();
        g.setColor(SEED); g.fill(t);
        g.setColor(INK); g.setStroke(stroke(6.6)); g.draw(t);
        g.setColor(alpha(Color.WHITE, 0.85));
        g.fill(ellipse(-2.6, 5, 1.7, 2.6));
        g.setTransform(old);
    }

    /** Ground, character and the hopping seed: the whole launcher artwork. */
    static void icon(Graphics2D g) {
        g.setColor(GROUND); g.fillRect(0, 0, 108, 108);
        character(g);
        seed(g, 75.5, 37.5, 32, 1);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);

        // 512 x 512 store icon: the visible 72-unit centre, full bleed (Play adds its own mask).
        BufferedImage icon = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(icon);
        g.scale(512 / 72.0, 512 / 72.0); g.translate(-18, -18);
        icon(g);
        g.dispose();
        ImageIO.write(icon, "png", new File(out, "icon-512.png"));

        // Launcher preview: round and rounded-square masks, to eyeball the safe zone.
        BufferedImage prev = new BufferedImage(864, 432, BufferedImage.TYPE_INT_RGB);
        g = g(prev);
        g.setColor(new Color(0x303030)); g.fillRect(0, 0, 864, 432);
        for (int k = 0; k < 2; k++) {
            Graphics2D h = (Graphics2D) g.create();
            h.translate(k * 432, 0); h.scale(4, 4);
            h.clip(k == 0 ? ellipse(54, 54, 36, 36) : new RoundRectangle2D.Double(18, 18, 72, 72, 32, 32));
            icon(h);
            h.dispose();
        }
        g.dispose();
        ImageIO.write(prev, "png", new File(out, "launcher-preview.png"));

        // 1024 x 500 feature graphic, no text (Play shows the title beside it).
        BufferedImage f = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        g = g(f);
        g.setColor(GROUND); g.fillRect(0, 0, 1024, 500);
        Graphics2D h = (Graphics2D) g.create();
        h.translate(6, -92); h.scale(6, 6);
        character(h);
        h.dispose();
        seed(g, 566, 176, 32, 6.2);
        seed(g, 700, 262, -22, 5.2);
        seed(g, 828, 182, 48, 4.4);
        seed(g, 934, 268, -36, 3.7);
        g.dispose();
        ImageIO.write(f, "png", new File(out, "feature-1024x500.png"));
    }
}
