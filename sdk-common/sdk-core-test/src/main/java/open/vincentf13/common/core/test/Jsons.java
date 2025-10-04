package open.vincentf13.common.core.test;

import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/** Utility wrapper around JSONAssert with concise error propagation. */
public final class Jsons {
  private Jsons() {}

  public static void assertEquals(String expected, String actual) {
    try {
      JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
    } catch (AssertionError e) {
      throw e;
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
