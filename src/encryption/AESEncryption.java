package encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AESEncryption
 * Responsibility: Pure encryption and decryption logic only.
 * - Derives a 256-bit AES key from password using PBKDF2
 * - Encrypts plain text using AES/CBC/PKCS5Padding
 * - Prepends IV to cipher output so decoder can extract it
 * - Decrypts by reading IV from first 16 bytes, then decrypting the rest
 * - Password is never stored — used only during key derivation then discarded
 */
public class AESEncryption {

    // AES configuration
    private static final String ALGORITHM       = "AES";
    private static final String TRANSFORMATION  = "AES/CBC/PKCS5Padding";
    private static final int    KEY_SIZE_BITS   = 256;
    private static final int    IV_SIZE_BYTES   = 16;
    private static final int    ITERATION_COUNT = 65536;

    // Fixed salt — acceptable for single-user local app
    // In a multi-user system, this would be random and stored per user
    private static final byte[] FIXED_SALT = {
        0x4A, 0x3F, 0x2E, 0x1D, 0x5C, 0x6B, 0x7A, 0x09,
        0x18, 0x27, 0x36, 0x45, 0x54, 0x63, 0x72, 0x11
    };

    /**
     * Encrypts a plain text message using AES/CBC/PKCS5Padding.
     * Output format: [16 bytes IV] + [N bytes ciphertext]
     * @param message  the plain text to encrypt
     * @param password the user-provided password
     * @return encrypted byte array (IV prepended), or null on failure
     */
    public byte[] encrypt(String message, String password) {
        try {
            // Derive AES key from password
            SecretKeySpec secretKey = deriveKey(password);

            // Generate a random IV for this encryption
            byte[] iv = new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Encrypt the message
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] cipherText = cipher.doFinal(message.getBytes("UTF-8"));

            // Prepend IV to ciphertext: [IV (16 bytes)] + [ciphertext]
            byte[] combined = new byte[IV_SIZE_BYTES + cipherText.length];
            System.arraycopy(iv,         0, combined, 0,              IV_SIZE_BYTES);
            System.arraycopy(cipherText, 0, combined, IV_SIZE_BYTES,  cipherText.length);

            System.out.println("AESEncryption: Encrypted successfully. Output size: "
                + combined.length + " bytes.");
            return combined;

        } catch (Exception e) {
            System.err.println("AESEncryption: Encryption failed — " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts AES-encrypted bytes back into plain text.
     * Reads first 16 bytes as IV, decrypts the remainder.
     * Returns null if password is wrong or data is corrupted.
     * @param encryptedData the byte array from LSBDecoder (IV + ciphertext)
     * @param password      the user-provided password
     * @return decrypted plain text string, or null on failure
     */
    public String decrypt(byte[] encryptedData, String password) {
        try {
            // Minimum valid size: 16 bytes IV + at least 16 bytes ciphertext
            if (encryptedData == null || encryptedData.length < IV_SIZE_BYTES + 16) {
                System.err.println("AESEncryption: Data too short to decrypt.");
                return null;
            }

            // Extract IV from first 16 bytes
            byte[] iv         = Arrays.copyOfRange(encryptedData, 0, IV_SIZE_BYTES);
            byte[] cipherText = Arrays.copyOfRange(encryptedData, IV_SIZE_BYTES,
                                                   encryptedData.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Derive the same key from password
            SecretKeySpec secretKey = deriveKey(password);

            // Decrypt
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] plainBytes = cipher.doFinal(cipherText);

            String result = new String(plainBytes, "UTF-8");
            System.out.println("AESEncryption: Decrypted successfully.");
            return result;

        } catch (javax.crypto.BadPaddingException e) {
            // This is the "wrong password" case
            System.err.println("AESEncryption: Wrong password or corrupted data.");
            return null;

        } catch (Exception e) {
            System.err.println("AESEncryption: Decryption failed — " + e.getMessage());
            return null;
        }
    }

    /**
     * Derives a 256-bit AES key from the user password using PBKDF2WithHmacSHA256.
     * Uses a fixed salt and 65,536 iterations for security.
     * @param password the raw password string
     * @return SecretKeySpec ready for use with AES Cipher
     */
    private SecretKeySpec deriveKey(String password) throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(
            password.toCharArray(),
            FIXED_SALT,
            ITERATION_COUNT,
            KEY_SIZE_BITS
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        SecretKey        secret  = factory.generateSecret(keySpec);

        // Clear the key spec from memory after use
        keySpec.clearPassword();

        return new SecretKeySpec(secret.getEncoded(), ALGORITHM);
    }
}
