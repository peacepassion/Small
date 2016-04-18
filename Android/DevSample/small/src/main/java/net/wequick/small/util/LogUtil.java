package net.wequick.small.util;

import android.util.Log;

public final class LogUtil {

  public static void warn(Object host, String content) {
    Log.w(host.getClass().getSimpleName(), content);
  }
}
