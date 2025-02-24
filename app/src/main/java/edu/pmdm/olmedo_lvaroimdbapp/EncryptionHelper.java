package edu.pmdm.olmedo_lvaroimdbapp;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionHelper {

    private static final String TAG = "EncryptionHelper";
    // Usamos AES con modo ECB y padding PKCS5 de forma explícita.
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String ALGORITHM = "AES";
    // Clave estática de 16 bytes (128 bits). ¡Solo para pruebas!
    private static final String SECRET_KEY_STRING = "1234567890123456";
    private static final SecretKey secretKey = new SecretKeySpec(
            SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8), ALGORITHM);

    /**
     * Encripta la dirección usando AES/ECB/PKCS5Padding.
     * @param address La dirección en texto plano.
     * @return La cadena encriptada en Base64 o null en caso de error.
     */
    public static String encryptAddress(String address) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(address.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encriptando address", e);
            return null;
        }
    }

    /**
     * Encripta el teléfono usando AES/ECB/PKCS5Padding.
     * @param phone El teléfono en texto plano.
     * @return La cadena encriptada en Base64 o null en caso de error.
     */
    public static String encryptPhone(String phone) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(phone.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encriptando phone", e);
            return null;
        }
    }

    /**
     * Desencripta la dirección encriptada usando AES/ECB/PKCS5Padding.
     * @param encryptedAddress La cadena encriptada en Base64.
     * @return La dirección en texto plano o null en caso de error.
     */
    public static String decryptAddress(String encryptedAddress) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.decode(encryptedAddress, Base64.DEFAULT);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error desencriptando address", e);
            return null;
        }
    }

    /**
     * Desencripta el teléfono encriptado usando AES/ECB/PKCS5Padding.
     * @param encryptedPhone La cadena encriptada en Base64.
     * @return El teléfono en texto plano o null en caso de error.
     */
    public static String decryptPhone(String encryptedPhone) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.decode(encryptedPhone, Base64.DEFAULT);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error desencriptando phone", e);
            return null;
        }
    }
}