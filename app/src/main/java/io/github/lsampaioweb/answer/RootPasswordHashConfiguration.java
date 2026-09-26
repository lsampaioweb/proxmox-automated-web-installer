package io.github.lsampaioweb.answer;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;

import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the runtime root-password hash once during application startup.
 */
@Configuration
class RootPasswordHashConfiguration {

  private static final String YESCRYPT_PREFIX = "$y$";
  private static final int RANDOM_BYTES_LENGTH = 16;
  private static final int HASH_SETTING_BUFFER_LENGTH = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final CryptLibrary CRYPT_LIBRARY = Native.load("crypt", CryptLibrary.class, Map.of(
      Library.OPTION_FUNCTION_MAPPER,
      (FunctionMapper) (library, method) -> "cryptGensaltRn".equals(method.getName())
          ? "crypt_gensalt_rn"
          : method.getName()));

  @Bean
  RootPasswordHash rootPasswordHash(@Value("${proxmox.root-password}") String password) {
    if (password.isBlank()) {
      throw new IllegalStateException("PROXMOX_ROOT_PASSWORD must not be blank");
    }

    byte[] randomBytes = new byte[RANDOM_BYTES_LENGTH];
    byte[] settingBuffer = new byte[HASH_SETTING_BUFFER_LENGTH];
    SECURE_RANDOM.nextBytes(randomBytes);

    try {
      Pointer settingPointer = CRYPT_LIBRARY.cryptGensaltRn(
          YESCRYPT_PREFIX,
          new NativeLong(0),
          randomBytes,
          randomBytes.length,
          settingBuffer,
          settingBuffer.length);
      if (settingPointer == null) {
        throw new IllegalStateException("libcrypt could not generate a yescrypt salt");
      }

      String setting = Native.toString(settingBuffer);
      String hash = CRYPT_LIBRARY.crypt(password, setting);
      if (hash == null || !hash.startsWith(YESCRYPT_PREFIX)) {
        throw new IllegalStateException("libcrypt could not generate a yescrypt hash");
      }

      return new RootPasswordHash(hash);
    } finally {
      Arrays.fill(randomBytes, (byte) 0);
      Arrays.fill(settingBuffer, (byte) 0);
    }
  }

  private interface CryptLibrary extends Library {

    Pointer cryptGensaltRn(String prefix, NativeLong count, byte[] randomBytes, int randomBytesLength,
        byte[] output, int outputLength);

    String crypt(String phrase, String setting);

  }

}