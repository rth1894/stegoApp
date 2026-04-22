package image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;

/**
 * ImageHandler
 * Responsibility: All file-level image operations.
 * - Load image from disk
 * - Convert JPG / JPEG → PNG-safe BufferedImage
 * - Save stego-image to disk
 * - Generate output file path
 */
public class ImageHandler {

    /**
     * Loads an image from the given file path.
     * Automatically converts JPG/JPEG to PNG-safe format.
     * @param filePath absolute path to the image file
     * @return BufferedImage ready for LSB processing, or null on failure
     */
    public BufferedImage loadImage(String filePath) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                System.err.println("ImageHandler: File not found — " + filePath);
                return null;
            }

            String ext = getFileExtension(filePath);

            if (!isSupportedFormat(ext)) {
                System.err.println("ImageHandler: Unsupported format — " + ext);
                return null;
            }

            BufferedImage image = ImageIO.read(file);

            if (image == null) {
                System.err.println("ImageHandler: ImageIO could not read file — " + filePath);
                return null;
            }

            // JPG/JPEG don't support alpha channel — convert to RGB PNG-safe format
            if (ext.equals("jpg") || ext.equals("jpeg")) {
                image = convertToPNG(image);
            }

            return image;

        } catch (IOException e) {
            System.err.println("ImageHandler: IOException loading image — " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts a BufferedImage to PNG-safe RGB format.
     * Strips alpha channel issues from JPG sources.
     * @param source the original loaded BufferedImage
     * @return new BufferedImage safe for LSB processing
     */
    public BufferedImage convertToPNG(BufferedImage source) {
        // Create a fresh RGB BufferedImage — avoids black background issues
        BufferedImage converted = new BufferedImage(
            source.getWidth(),
            source.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );

        // Draw source onto new image to normalize pixel format
        Graphics2D g2d = converted.createGraphics();
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();

        return converted;
    }

    /**
     * Saves a BufferedImage to disk as PNG.
     * @param image      the BufferedImage to save
     * @param outputPath absolute path for the output file
     * @return true if saved successfully, false otherwise
     */
    public boolean saveImage(BufferedImage image, String outputPath) {
        try {
            File outputFile = new File(outputPath);

            // Create parent directories if they don't exist
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            boolean saved = ImageIO.write(image, "PNG", outputFile);

            if (!saved) {
                System.err.println("ImageHandler: ImageIO could not write PNG — " + outputPath);
                return false;
            }

            System.out.println("ImageHandler: Image saved — " + outputPath);
            return true;

        } catch (IOException e) {
            System.err.println("ImageHandler: IOException saving image — " + e.getMessage());
            return false;
        }
    }

    /**
     * Generates the output path for the stego-image.
     * Appends _stego to the filename and forces .png extension.
     * Example: C:/images/photo.jpg → C:/images/photo_stego.png
     * @param inputPath original image file path
     * @return output path string for the stego-image
     */
    public String generateOutputPath(String inputPath) {
        int dotIndex = inputPath.lastIndexOf('.');
        if (dotIndex == -1) {
            return inputPath + "_stego.png";
        }
        return inputPath.substring(0, dotIndex) + "_stego.png";
    }

    /**
     * Returns the lowercase file extension of a given path.
     * Example: "photo.JPG" → "jpg"
     * @param filePath path to the file
     * @return lowercase extension string, or empty string if none found
     */
    public String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filePath.length() - 1) {
            return "";
        }
        return filePath.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * Checks if the file extension is a supported image format.
     * @param extension lowercase file extension
     * @return true if supported, false otherwise
     */
    public boolean isSupportedFormat(String extension) {
        return extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");
    }
}
