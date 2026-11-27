package gt.cadprint.manager;

import java.io.IOException;

interface ProductImageStorage {
    String store(byte[] image) throws IOException;

    byte[] read(String key) throws IOException;

    void delete(String key) throws IOException;
}
