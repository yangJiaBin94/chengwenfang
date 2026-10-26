package com.chengwenfang.infra.support;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 密钥列 AES-GCM 加解密：主密钥来自 {@code chengwenfang.secret-key}（D-04）。
 *
 * <p>密文格式：Base64({@code IV(12)} || {@code ciphertext+tag})；使用 Hutool {@link AES}。
 */
@Component
public class SecretCipher {
  /** GCM 推荐 IV 长度（字节）。 */
  private static final int IV_LENGTH = 12;
  /** GCM 认证标签位数。 */
  private static final int GCM_TAG_BITS = 128;
  /** AES-256 密钥长度（字节）；由主密钥 SHA-256 派生。 */
  private static final int KEY_LENGTH = 32;

  /** 派生后的 AES 密钥。 */
  private final byte[] aesKey;
  /** 安全随机数（生成 IV）。 */
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * 使用全局配置中的主密钥构造。
   *
   * @param properties 成文坊配置
   */
  @Autowired
  public SecretCipher(ChengwenfangProperties properties) {
    this(properties.getSecretKey());
  }

  /**
   * 使用显式主密钥构造（单测或非 Spring 场景）。
   *
   * @param secretKey 主密钥字符串；空白则抛错
   */
  public SecretCipher(String secretKey) {
    if (StrUtil.isBlank(secretKey)) {
      throw new IllegalArgumentException("chengwenfang.secret-key 不能为空");
    }
    // 任意长度主密钥派生为 32 字节 AES-256 密钥
    this.aesKey = SecureUtil.sha256().digest(secretKey.getBytes(StandardCharsets.UTF_8));
    if (aesKey.length != KEY_LENGTH) {
      throw new IllegalStateException("派生密钥长度异常：" + aesKey.length);
    }
  }

  /**
   * 加密明文；null 按空串。
   *
   * @param plain 明文
   * @return Base64 密文（含 IV）
   */
  public String encrypt(String plain) {
    String text = plain == null ? "" : plain;
    byte[] iv = new byte[IV_LENGTH];
    secureRandom.nextBytes(iv);
    AES aes = newAes(iv);
    byte[] cipherBytes = aes.encrypt(text.getBytes(StandardCharsets.UTF_8));
    ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
    buffer.put(iv);
    buffer.put(cipherBytes);
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  /**
   * 解密密文；空白密文返回空串。
   *
   * @param ciphertext Base64 密文（含 IV）
   * @return 明文
   * @throws IllegalArgumentException 密文格式非法或认证失败
   */
  public String decrypt(String ciphertext) {
    if (StrUtil.isBlank(ciphertext)) {
      return "";
    }
    byte[] packed;
    try {
      packed = Base64.getDecoder().decode(ciphertext.trim());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("密文不是合法 Base64", exception);
    }
    if (packed.length <= IV_LENGTH) {
      throw new IllegalArgumentException("密文过短");
    }
    byte[] iv = new byte[IV_LENGTH];
    byte[] cipherBytes = new byte[packed.length - IV_LENGTH];
    System.arraycopy(packed, 0, iv, 0, IV_LENGTH);
    System.arraycopy(packed, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
    try {
      AES aes = newAes(iv);
      byte[] plain = aes.decrypt(cipherBytes);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "解密失败（密钥不匹配或密文被篡改），ivHex=" + HexUtil.encodeHexStr(iv),
          exception);
    }
  }

  /**
   * 构造带 {@link GCMParameterSpec} 的 Hutool AES。
   *
   * @param iv 12 字节 IV
   * @return AES 实例
   */
  private AES newAes(byte[] iv) {
    SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
    return new AES("GCM", "NoPadding", keySpec, gcmSpec);
  }
}
