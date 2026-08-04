package cn.finalscompass.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSecretCipherTest {
    private static final String TEST_MASTER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        var cipher = new AiSecretCipher(TEST_MASTER_KEY);
        char[] secret = "sk-test-value-not-real".toCharArray();

        var first = cipher.encrypt(secret);
        var second = cipher.encrypt(secret);

        assertThat(first.ciphertext()).doesNotContain("sk-test-value");
        assertThat(second.ciphertext()).isNotEqualTo(first.ciphertext());
        assertThat(second.iv()).isNotEqualTo(first.iv());
        assertThat(cipher.decrypt(first.ciphertext(), first.iv())).containsExactly(secret);
        assertThat(first.fingerprint()).hasSize(12).isEqualTo(second.fingerprint());
    }

    @Test
    void refusesPersistentEncryptionWithoutMasterKey() {
        var cipher = new AiSecretCipher("");
        assertThat(cipher.available()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("sk-test-value-not-real".toCharArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_SECRET_ENCRYPTION_KEY");
    }
}
