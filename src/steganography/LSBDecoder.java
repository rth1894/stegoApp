package steganography;

import java.awt.image.BufferedImage;

/**
 * LSBDecoder
 * Responsibility: Extract hidden encrypted bytes from a stego-image.
 * - Reads LSBs from R, G, B channels in the same order as LSBEncoder
 * - Reads the 32-bit length header first to know how many bytes follow
 * - Returns raw encrypted bytes — AESEncryption handles decryption separately
 * - Works only with PNG-safe BufferedImages (from ImageHandler)
 */
public class LSBDecoder {

    /**
     * Extracts hidden encrypted bytes from a stego-image.
     *
     * Step 1: Read first 32 bits → reconstruct data length integer
     * Step 2: Read next (length * 8) bits → reconstruct encrypted bytes
     *
     * Pixel traversal matches LSBEncoder exactly:
     *   left to right, top to bottom
     *   bitIndex / 3 = pixelIndex, bitIndex % 3 = channel (0=R, 1=G, 2=B)
     *
     * @param stegoImage the image containing hidden data
     * @return extracted encrypted byte array, or null if extraction fails
     */
    public byte[] extractData(BufferedImage stegoImage) {
        if (stegoImage == null) {
            System.err.println("LSBDecoder: null image provided.");
            return null;
        }

        int width  = stegoImage.getWidth();
        int height = stegoImage.getHeight();
        int totalAvailableBits = width * height * 3;

        // ── Step 1: Read 32-bit header to get data length ────────────────
        if (totalAvailableBits < 32) {
            System.err.println("LSBDecoder: Image too small to contain header.");
            return null;
        }

        int dataLength = 0;
        for (int bitIndex = 0; bitIndex < 32; bitIndex++) {
            int bit = readBitAt(stegoImage, bitIndex, width);
            dataLength = (dataLength << 1) | bit;
        }

        // Sanity check — reject absurd lengths
        if (dataLength <= 0 || dataLength > (totalAvailableBits - 32) / 8) {
            System.err.println("LSBDecoder: Invalid data length in header: "
                + dataLength);
            return null;
        }

        System.out.println("LSBDecoder: Header read — expecting "
            + dataLength + " bytes.");

        // ── Step 2: Read data bits and reconstruct bytes ──────────────────
        byte[] extractedData = new byte[dataLength];

        for (int i = 0; i < dataLength; i++) {
            int byteValue = 0;
            for (int b = 0; b < 8; b++) {
                int bitIndex = 32 + (i * 8) + b;
                int bit      = readBitAt(stegoImage, bitIndex, width);
                byteValue    = (byteValue << 1) | bit;
            }
            extractedData[i] = (byte) byteValue;
        }

        System.out.println("LSBDecoder: Extracted " + dataLength
            + " bytes successfully.");
        return extractedData;
    }

    /**
     * Reads a single bit from the image at the given bit index.
     * Maps bitIndex to pixel coordinates and channel using the same
     * formula as LSBEncoder.
     * @param image    the stego-image
     * @param bitIndex the global bit position (0-based)
     * @param width    image width for x/y calculation
     * @return the bit value (0 or 1)
     */
    private int readBitAt(BufferedImage image, int bitIndex, int width) {
        int pixelIndex = bitIndex / 3;
        int channel    = bitIndex % 3;   // 0=R, 1=G, 2=B

        int x = pixelIndex % width;
        int y = pixelIndex / width;

        int pixel = image.getRGB(x, y);

        int channelValue;
        if      (channel == 0) channelValue = (pixel >> 16) & 0xFF;  // R
        else if (channel == 1) channelValue = (pixel >> 8)  & 0xFF;  // G
        else                   channelValue =  pixel         & 0xFF;  // B

        return extractBit(channelValue);
    }

    /**
     * Reads the LSB of a color channel value.
     * Operation: colorValue & 1
     * @param colorValue R, G, or B channel value (0-255)
     * @return the LSB (0 or 1)
     */
    private int extractBit(int colorValue) {
        return colorValue & 1;
    }
}
