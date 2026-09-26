package inventario.servicio;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria por usuario (solo la librería estándar de Java).
 * Formato guardado: {@code pbkdf2-sha256$<iteraciones>$<sal base64>$<hash base64>}; incluir las iteraciones
 * permite subirlas en el futuro sin invalidar las contraseñas existentes.
 */
public final class Contrasenas {

    public static final int LONGITUD_MINIMA = 8;

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2-sha256";
    private static final int ITERACIONES = 210_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom AZAR = new SecureRandom();

    private Contrasenas() {
    }

    public static String hashear(char[] contrasena) {
        byte[] sal = new byte[BYTES_SAL];
        AZAR.nextBytes(sal);
        byte[] hash = derivar(contrasena, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    /** Compara en tiempo constante, para no dar pistas por lo que tarda la comparación. */
    public static boolean verificar(char[] contrasena, String almacenado) {
        String[] partes = almacenado.split("\\$");
        if (partes.length != 4 || !partes[0].equals(PREFIJO)) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(contrasena, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Reglas mínimas; devuelve el problema o {@code null} si la contraseña es aceptable. */
    public static String problema(char[] contrasena) {
        if (contrasena == null || contrasena.length < LONGITUD_MINIMA) {
            return "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.";
        }
        boolean letra = false;
        boolean digito = false;
        for (char c : contrasena) {
            letra |= Character.isLetter(c);
            digito |= Character.isDigit(c);
        }
        return letra && digito ? null : "La contraseña debe combinar letras y números.";
    }

    private static byte[] derivar(char[] contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena, sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible en esta JVM", e);
        } finally {
            especificacion.clearPassword();
        }
    }

    /** Borra la contraseña de la memoria en cuanto ya no se necesita. */
    public static void borrar(char[] contrasena) {
        if (contrasena != null) {
            Arrays.fill(contrasena, '\0');
        }
    }
}
