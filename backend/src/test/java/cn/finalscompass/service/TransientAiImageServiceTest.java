package cn.finalscompass.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TransientAiImageServiceTest {
    private final TransientAiImageService service = new TransientAiImageService();

    @Test void decodesAndClearsRequestScopedJpeg() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 1, 2, 3};
        var image = service.decode("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg));
        assertEquals("image/jpeg", image.mediaType());
        assertArrayEquals(jpeg, image.bytes());
        image.close();
        assertArrayEquals(new byte[jpeg.length], image.bytes());
    }

    @Test void rejectsDeclaredTypeThatDoesNotMatchMagicBytes() {
        String value = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{1,2,3,4,5,6,7,8});
        assertThrows(IllegalArgumentException.class, () -> service.decode(value));
    }

    @Test void acceptsNoImageWithoutAllocatingStorage() {
        assertNull(service.decode(null));
        assertNull(service.decode(""));
    }
}
