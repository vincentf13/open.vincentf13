package open.vincentf13.sdk.infra.redis;

import java.nio.charset.StandardCharsets;

final class RedisClusterUtil {

  private RedisClusterUtil() {}

  static int slot(String key) {
    String hashtag = key;
    int start = key.indexOf('{');
    if (start >= 0) {
      int end = key.indexOf('}', start + 1);
      if (end > start + 1) {
        hashtag = key.substring(start + 1, end);
      }
    }
    return crc16(hashtag.getBytes(StandardCharsets.UTF_8)) % 16384;
  }

  static int crc16(byte[] bytes) {
    int crc = 0x0000;
    for (byte b : bytes) {
      crc ^= (b & 0xFF) << 8;
      for (int i = 0; i < 8; i++) {
        if ((crc & 0x8000) != 0) {
          crc = (crc << 1) ^ 0x1021;
        } else {
          crc <<= 1;
        }
        crc &= 0xFFFF;
      }
    }
    return crc & 0xFFFF;
  }
}
