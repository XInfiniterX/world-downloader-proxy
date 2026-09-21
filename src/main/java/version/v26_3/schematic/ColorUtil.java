package version.v26_3.schematic;

/**
 * Small color utility for the particle renderer — converts HSB to packed RGB24 integers
 * the way the Minecraft dust particle expects (R&lt;&lt;16 | G&lt;&lt;8 | B).
 */
final class ColorUtil {
    private ColorUtil() { }

    /**
     * @return packed RGB integer (R&lt;&lt;16 | G&lt;&lt;8 | B) from HSB components,
     *         matching {@link java.awt.Color#getRGB()} without the alpha bits.
     */
    static int hsbToRgb(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return rgb & 0x00FFFFFF;
    }
}
