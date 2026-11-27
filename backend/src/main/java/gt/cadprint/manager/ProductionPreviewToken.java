package gt.cadprint.manager;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class ProductionPreviewToken {
    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;
    private final ObjectMapper json;

    ProductionPreviewToken(ObjectMapper json) {
        this.json = json;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        key = new SecretKeySpec(bytes, ALGORITHM);
    }

    String sign(Object snapshot) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(json.writeValueAsBytes(snapshot)));
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo firmar la vista previa", exception);
        }
    }

    boolean matches(String provided, String expected) {
        try {
            return provided != null && provided.length() == 64 && MessageDigest.isEqual(
                    HexFormat.of().parseHex(provided), HexFormat.of().parseHex(expected));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
