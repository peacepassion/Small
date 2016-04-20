package net.wequick.small;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public final class SharedPreferenceManager {

  private static Application app;

  private static final String KEY_SP_SMALL = "small";
  private static final String KEY_SP_SMALL_BUNDLE = "small.bundle";
  private static final String KEY_UPGRADE = "upgrade";
  private static final String KEY_HOST_VERSION = "host_version";
  private static final String KEY_BUNDLE_MODIFIES = "small.app-modifies";
  private static final String KEY_BUNDLE_UPGRADES = "small.app-upgrades";
  private static final String KEY_NEED_UPGRADE = "need_upgrade";

  public static void init(Application app) {
    SharedPreferenceManager.app = app;
  }

  public static int getHostVersionCode() {
    return app.getSharedPreferences(KEY_SP_SMALL, Context.MODE_PRIVATE).getInt(KEY_HOST_VERSION, 0);
  }

  public static void setHostVersionCode(int versionCode) {
    SharedPreferences small = app.getSharedPreferences(KEY_SP_SMALL, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = small.edit();
    editor.putInt(KEY_HOST_VERSION, versionCode);
    editor.apply();
  }

  public static void setBundleVersionCode(String packageName, int versionCode) {
    SharedPreferences bundlesInfo = app.getSharedPreferences(KEY_SP_SMALL_BUNDLE, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = bundlesInfo.edit();
    editor.putInt(packageName, versionCode);
    editor.apply();
  }
  //
  //public static void setBundleUpgraded(boolean val) {
  //  SharedPreferences upgradeInfo = app.getSharedPreferences(KEY_UPGRADE, Context.MODE_PRIVATE);
  //  SharedPreferences.Editor editor = upgradeInfo.edit();
  //  editor.putBoolean(KEY_NEED_UPGRADE, val);
  //  editor.apply();
  //}
  //
  //public static boolean getBundleUpgraded() {
  //  return app.getSharedPreferences(KEY_UPGRADE, Context.MODE_PRIVATE).getBoolean(KEY_NEED_UPGRADE, false);
  //}
}
