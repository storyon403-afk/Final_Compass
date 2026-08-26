package cn.finalscompass.circle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResourceFilePolicyTest {
  @Test
  void derivesMimeFromExtensionAndSignature() {
    assertThat(ResourceFilePolicy.validateAndMime("jpg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
        .isEqualTo("image/jpeg");
    assertThat(ResourceFilePolicy.validateAndMime("docx", new byte[] {0x50, 0x4b, 0x03, 0x04}))
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  }

  @Test
  void rejectsHtmlDisguisedAsImage() {
    assertThatThrownBy(() -> ResourceFilePolicy.validateAndMime("jpg", "<script>".getBytes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不匹配");
  }

  @Test
  void onlySafePreviewTypesMayRenderInline() {
    assertThat(ResourceFilePolicy.mayRenderInline("image/png")).isTrue();
    assertThat(ResourceFilePolicy.mayRenderInline("application/pdf")).isTrue();
    assertThat(ResourceFilePolicy.mayRenderInline("application/zip")).isFalse();
  }
}
