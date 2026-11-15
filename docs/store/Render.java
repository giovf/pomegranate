import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the Pomegranate store graphics from the same geometry as the launcher icon. */
public class Render {
    static final Color CREAM = new Color(0xF4E9E1), RED = new Color(0xB3213A), DEEP = new Color(0x7A1326);
    // Geometry in the 108-unit launcher viewport.
    static final double CX = 54, CY = 58, R = 23, SEED_R = 3.6, SEED_D = 9.5;
    static final double[][] CROWN = {{46, 37}, {44, 25}, {50, 30}, {54, 24}, {58, 30}, {64, 25}, {62, 37}};

    static Graphics2D g(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static Shape circle(double x, double y, double r) { return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r); }

    /** Draws the fruit in viewport units; caller sets the transform. */
    static void fruit(Graphics2D g, Color body, Color crown, Color seeds) {
        Path2D c = new Path2D.Double();
        c.moveTo(CROWN[0][0], CROWN[0][1]);
        for (int i = 1; i < CROWN.length; i++) c.lineTo(CROWN[i][0], CROWN[i][1]);
        c.closePath();
        g.setColor(crown); g.fill(c);
        g.setColor(body); g.fill(circle(CX, CY, R));
        g.setColor(seeds);
        g.fill(circle(CX, CY + 1, SEED_R));
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            g.fill(circle(CX + SEED_D * Math.cos(a), CY + 1 + SEED_D * Math.sin(a), SEED_R));
        }
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        // 512 x 512 store icon: the 72-unit centre of the adaptive icon, full bleed (Play adds the mask).
        BufferedImage icon = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(icon);
        g.setColor(CREAM); g.fillRect(0, 0, 512, 512);
        g.scale(512 / 72.0, 512 / 72.0); g.translate(-18, -18);
        fruit(g, RED, DEEP, CREAM);
        g.dispose();
        ImageIO.write(icon, "png", new File(out, "icon-512.png"));

        // Launcher preview: full 108 viewport with a circle and a squircle-ish mask, for eyeballing the safe zone.
        BufferedImage prev = new BufferedImage(864, 432, BufferedImage.TYPE_INT_RGB);
        g = g(prev);
        g.setColor(new Color(0x303030)); g.fillRect(0, 0, 864, 432);
        for (int k = 0; k < 2; k++) {
            Graphics2D h = (Graphics2D) g.create();
            h.translate(k * 432, 0); h.scale(4, 4);
            Shape mask = k == 0 ? circle(54, 54, 36) : new RoundRectangle2D.Double(18, 18, 72, 72, 32, 32);
            h.clip(mask);
            h.setColor(CREAM); h.fillRect(0, 0, 108, 108);
            fruit(h, RED, DEEP, CREAM);
            h.dispose();
        }
        g.dispose();
        ImageIO.write(prev, "png", new File(out, "launcher-preview.png"));

        // 1024 x 500 feature graphic, no text (Play shows the title next to it).
        BufferedImage f = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        g = g(f);
        g.setPaint(new GradientPaint(0, 0, new Color(0x9A1C31), 1024, 500, new Color(0x5A0E1C)));
        g.fillRect(0, 0, 1024, 500);
        double s = 150 / R;
        Graphics2D h = (Graphics2D) g.create();
        h.translate(300 - CX * s, 268 - CY * s); h.scale(s, s);
        fruit(h, CREAM, new Color(0xE2C9BA), RED);
        h.dispose();
        double[][] trail = {{512, 262, 23}, {585, 222, 21}, {652, 280, 19}, {716, 232, 17}, {776, 292, 15.5},
                {832, 246, 14}, {884, 298, 12.5}, {932, 256, 11}, {974, 296, 9.5}};
        for (int i = 0; i < trail.length; i++) {
            float alpha = 1f - 0.6f * i / (trail.length - 1);
            g.setColor(new Color(CREAM.getRed() / 255f, CREAM.getGreen() / 255f, CREAM.getBlue() / 255f, alpha));
            g.fill(circle(trail[i][0], trail[i][1], trail[i][2]));
        }
        g.dispose();
        ImageIO.write(f, "png", new File(out, "feature-1024x500.png"));
    }
}
