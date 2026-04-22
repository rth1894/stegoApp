package utils;

import java.awt.image.BufferedImage;

/**
 * CapacityCalculator
 * Responsibility: Calculate how many characters can be hidden in a given image.
 * - Called immediately after image upload, before message input
 * - Result displayed in UI as "Max characters: X"
 */
public class CapacityCalculator {

    // 4 bytes for the length header embedded by LSBEncoder
    private static final int HEADER_BYTES = 4;

    // 32 bytes buffer for AES encryption overhead (IV + padding)
    private static final int AES_OVERHEAD_BYTES = 32;

    /**
     * Calculates the maximum number of characters that can be hidden.
     * Formula: ((width x height x 3 channels) / 8 bits) - header - aes overhead
     * @param image the cover image
     * @return maximum number of characters that can safely be embedded
     */
    public int calculateMaxChars(BufferedImage image) {
        if (image == null) {
            System.err.println("CapacityCalculator: null image provided.");
            return 0;
        }

        int totalBits  = calculateTotalBits(image);
        int totalBytes = totalBits / 8;
        int maxChars   = totalBytes - HEADER_BYTES - AES_OVERHEAD_BYTES;

        // Safety — never return negative capacity
        return Math.max(0, maxChars);
    }

    /**
     * Returns total usable bits in the image.
     * Each pixel has 3 channels (RGB), each channel contributes 1 bit.
     * @param image the cover image
     * @return total bits available for embedding
     */
    public int calculateTotalBits(BufferedImage image) {
        if (image == null) return 0;
        return image.getWidth() * image.getHeight() * 3;
    }

    /**
     * Compares original vs stego-image and counts how many pixels changed.
     * Used in the Analysis tab to show minimal visual distortion.
     * @param original   the original cover image
     * @param stegoImage the image after LSB embedding
     * @return number of pixels that were modified
     */
    public int countPixelDifferences(BufferedImage original, BufferedImage stegoImage) {
        if (original == null || stegoImage == null) {
            System.err.println("CapacityCalculator: null image provided for comparison.");
            return -1;
        }

        // Images must be same dimensions to compare
        if (original.getWidth()  != stegoImage.getWidth() ||
            original.getHeight() != stegoImage.getHeight()) {
            System.err.println("CapacityCalculator: image dimensions do not match.");
            return -1;
        }

        int differences = 0;
        int width  = original.getWidth();
        int height = original.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (original.getRGB(x, y) != stegoImage.getRGB(x, y)) {
                    differences++;
                }
            }
        }

        return differences;
    }

    /**
     * Returns a human-readable capacity summary string for logging.
     * Example: "Image capacity: 800000 bits | 100000 bytes | 99964 max chars"
     * @param image the cover image
     * @return formatted summary string
     */
    public String getCapacitySummary(BufferedImage image) {
        if (image == null) return "No image provided.";

        int totalBits  = calculateTotalBits(image);
        int totalBytes = totalBits / 8;
        int maxChars   = calculateMaxChars(image);

        return "Image capacity: "
            + totalBits  + " bits | "
            + totalBytes + " bytes | "
            + maxChars   + " max chars";
    }
}
