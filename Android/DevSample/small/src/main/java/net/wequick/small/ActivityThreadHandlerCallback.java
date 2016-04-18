package net.wequick.small;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.wequick.small.util.ReflectAccelerator;

/**
 * Class for restore activity info from Stub to Real
 */
public class ActivityThreadHandlerCallback implements Handler.Callback {

  private static final int LAUNCH_ACTIVITY = 100;

  private ApkBundleLauncher apkBundleLauncher;

  public ActivityThreadHandlerCallback(ApkBundleLauncher apkBundleLauncher) {
    this.apkBundleLauncher = apkBundleLauncher;
  }

  @Override
  public boolean handleMessage(Message msg) {
    if (msg.what != LAUNCH_ACTIVITY) return false;

    Object/*ActivityClientRecord*/ r = msg.obj;
    Intent intent = ReflectAccelerator.getIntent(r);
    String targetClass = unwrapIntent(intent);
    if (targetClass == null) return false;

    // Replace with the REAL activityInfo
    Map<String, ActivityInfo> loadedActivities = apkBundleLauncher.loadedActivities();
    ActivityInfo targetInfo = loadedActivities.get(targetClass);
    ReflectAccelerator.setActivityInfo(r, targetInfo);
    return false;
  }

  private static String unwrapIntent(Intent intent) {
    Set<String> categories = intent.getCategories();
    if (categories == null) return null;

    // Get plugin activity class name from categories
    Iterator<String> it = categories.iterator();
    while (it.hasNext()) {
      String category = it.next();
      if (category.charAt(0) == ApkBundleLauncher.REDIRECT_FLAG) {
        return category.substring(1);
      }
    }
    return null;
  }
}
