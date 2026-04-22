package steganography;

import java.awt.image.BufferedImage;

/**
 * LSBEncoder
 * Responsibility: Embed encrypted bytes into image pixels using LSB technique.
 * - Embeds a 32-bit length header first so decoder knows how many bytes to read
 * - Modifies only the LSB of R, G, B channels — visually imperceptible
 * - Returns a NEW BufferedImage — never modifies the original
 * - Works only with PNG-safe BufferedImages (from ImageHandler)
 */
public class LSBEncoder {

    /**
     * Embeds encrypted byte data into the image using LSB steganography.
     *
     * Pixel traversal: left to right, top to bottom.
     * Bit mapping: bitIndex / 3 = pixelIndex, bitIndex % 3 = channel (0=R,1=G,2=B)
     *
     * Output format embedded in image:
     *   [32 bits = data length as int] [data.length * 8 bits = actual data]
     *
     * @param image         the cover image (PNG-safe BufferedImage)
     * @param encryptedData the encrypted bytes to hide
     * @return a new BufferedImage with data embedded, or null if capacity exceeded
     */
    public BufferedImage embedData(BufferedImage image, byte[] encryptedData) {
        if (image == null || encryptedData == null) {
            System.err.println("LSBEncoder: null input provided.");
            return null;
        }

        if (!hasCapacity(image, encryptedData)) {
            System.err.println("LSBEncoder: Image too small to hold the data.");
            return null;
        }

        int width  = image.getWidth();
        int height = image.getHeight();

        // Copy the original image — never modify the original
        BufferedImage stegoImage = new BufferedImage(width, height,
                                                     BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                stegoImage.setRGB(x, y, image.getRGB(x, y));
            }
        }

        // Build the full bit stream: [32-bit header] + [data bits]
        int dataLength  = encryptedData.length;
        int totalBits   = 32 + (dataLength * 8);
        int[] bitStream = new int[totalBits];

        // Fill header: 32 bits representing the data length as an integer
        for (int i = 0; i < 32; i++) {
            bitStream[i] = (dataLength >> (31 - i)) & 1;
        }

        // Fill data bits: each byte → 8 bits, MSB first
        for (int i = 0; i < dataLength; i++) {
            for (int b = 0; b < 8; b++) {
                bitStream[32 + (i * 8) + b] = (encryptedData[i] >> (7 - b)) & 1;
            }
        }

        // Embed bit stream into image pixels
        for (int bitIndex = 0; bitIndex < totalBits; bitIndex++) {
            int pixelIndex = bitIndex / 3;
            int channel    = bitIndex % 3;   // 0=R, 1=G, 2=B

            int x = pixelIndex % width;
            int y = pixelIndex / width;

            int pixel = stegoImage.getRGB(x, y);

            // Extract R, G, B from pixel integer
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8)  & 0xFF;
            int b =  pixel        & 0xFF;

            // Modify LSB of the appropriate channel
            if      (channel == 0) r = embedBit(r, bitStream[bitIndex]);
            else if (channel == 1) g = embedBit(g, bitStream[bitIndex]);
            else                   b = embedBit(b, bitStream[bitIndex]);

            // Pack R, G, B back into pixel integer and write
            int newPixel = (0xFF << 24) | (r << 16) | (g << 8) | b;
            stegoImage.setRGB(x, y, newPixel);
        }

        System.out.println("LSBEncoder: Embedded " + dataLength
            + " bytes into image successfully.");
        return stegoImage;
    }

    /**
     * Checks whether the image has enough capacity to hold the data + header.
     * Total bits needed = 32 (header) + data.length * 8
     * Total bits available = width * height * 3
     * @param image         the cover image
     * @param encryptedData the data to embed
     * @return true if data fits, false if image is too small
     */
    public boolean hasCapacity(BufferedImage image, byte[] encryptedData) {
        if (image == null || encryptedData == null) return false;
        int availableBits = image.getWidth() * image.getHeight() * 3;
        int requiredBits  = 32 + (encryptedData.length * 8);
        return availableBits >= requiredBits;
    }

    /**
     * Embeds a single bit into the LSB of a color channel value.
     * Operation: (colorValue & 0xFE) | bit
     * - 0xFE = 11111110 → clears the LSB
     * - | bit → sets LSB to our bit
     * Max value change: ±1 (e.g. 200 → 201) — visually imperceptible
     * @param colorValue original R, G, or B channel value (0-255)
     * @param bit        the bit to embed (0 or 1)
     * @return modified color value with LSB set to bit
     */
    private int embedBit(int colorValue, int bit) {
        return (colorValue & 0xFE) | bit;
    }
}