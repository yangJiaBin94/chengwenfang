package com.workbench.infra.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link SecretCipher} AES-GCM 往返与错误密文。 */
class SecretCipherTest {

  /**
   * 中文、空串、长串与 null 明文往返一致；同明文两次密文不同（随机 IV）。
   */
  @Test
  void encrypt_decrypt_roundTrip() {
    SecretCipher cipher = new SecretCipher("unit-test-secret-key-please-rotate");
    assertRoundTrip(cipher, "hello");
    assertRoundTrip(cipher, "密钥：测试中文与符号 !@#");
    assertRoundTrip(cipher, "");
    assertThat(cipher.decrypt(cipher.encrypt(null))).isEqualTo("");
    String longText = "密".repeat(10_000);
    assertRoundTrip(cipher, longText);

    String first = cipher.encrypt("same");
    String second = cipher.encrypt("same");
    assertThat(first).isNotEqualTo(second);
    assertThat(cipher.decrypt(first)).isEqualTo("same");
    assertThat(cipher.decrypt(second)).isEqualTo("same");
  }

  /**
   * 错误主密钥或篡改密文时解密失败。
   */
  @Test
  void decrypt_rejectsTamperedOrWrongKey() {
    SecretCipher cipher = new SecretCipher("unit-test-secret-key-please-rotate");
    String packed = cipher.encrypt("secret-value");
    SecretCipher other = new SecretCipher("another-secret-key-please-rotate");
    assertThatThrownBy(() -> other.decrypt(packed))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt("not-base64!!!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(cipher.decrypt("")).isEqualTo("");
    assertThat(cipher.decrypt(null)).isEqualTo("");
  }

  /**
   * 空白主密钥拒绝构造。
   */
  @Test
  void constructor_rejectsBlankSecret() {
    assertThatThrownBy(() -> new SecretCipher("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * 断言加解密往返。
   *
   * @param cipher 密码器
   * @param plain  明文
   */
  private static void assertRoundTrip(SecretCipher cipher, String plain) {
    String encrypted = cipher.encrypt(plain);
    assertThat(encrypted).isNotBlank();
    assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
  }
}
