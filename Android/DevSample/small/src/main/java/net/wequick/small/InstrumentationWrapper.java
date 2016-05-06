package net.wequick.small;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.wequick.small.util.ReflectAccelerator;

public class InstrumentationWrapper extends Instrumentation {

  private static final String TAG = InstrumentationWrapper.class.getSimpleName();

  private static final char REDIRECT_FLAG = '>';
  private static final int STUB_ACTIVITIES_COUNT = 4;
  private static final String PACKAGE_NAME = ApkBundleLauncher.class.getPackage().getName();
  private static final String STUB_ACTIVITY_PREFIX = PACKAGE_NAME + ".A";
  private static final String STUB_ACTIVITY_TRANSLUCENT = STUB_ACTIVITY_PREFIX + '1';

  private ApkBundleLauncher apkBundleLauncher;

  public InstrumentationWrapper(ApkBundleLauncher apkBundleLauncher) {
    this.apkBundleLauncher = apkBundleLauncher;
  }

  /** @Override V21+
   * Wrap activity from REAL to STUB */
  public ActivityResult execStartActivity(
      Context who, IBinder contextThread, IBinder token, Activity target,
      Intent intent, int requestCode, android.os.Bundle options) {
    wrapIntent(intent);
    return ReflectAccelerator.execStartActivity(apkBundleLauncher.hostInstrumentation(),
        who, contextThread, token, target, intent, requestCode, options);
  }

  /** @Override V20-
   * Wrap activity from REAL to STUB */
  public ActivityResult execStartActivity(
      Context who, IBinder contextThread, IBinder token, Activity target,
      Intent intent, int requestCode) {
    wrapIntent(intent);
    return ReflectAccelerator.execStartActivity(apkBundleLauncher.hostInstrumentation(),
        who, contextThread, token, target, intent, requestCode);
  }

  @Override
  /** Prepare resources for REAL */
  public void callActivityOnCreate(Activity activity, android.os.Bundle icicle) {
    do {
      Map<String, ActivityInfo> loadedActivities = apkBundleLauncher.loadedActivities();
      if (loadedActivities == null) break;
      ActivityInfo ai = loadedActivities.get(activity.getClass().getName());
      if (ai == null) break;

      applyActivityInfo(activity, ai);
    } while (false);

    apkBundleLauncher.hostInstrumentation().callActivityOnCreate(activity, icicle);
  }

  /**
   * Apply plugin activity info with plugin's AndroidManifest.xml
   * @param activity
   * @param ai
   */
  public void applyActivityInfo(Activity activity, ActivityInfo ai) {
    // Apply window attributes
    Window window = activity.getWindow();
    window.setSoftInputMode(ai.softInputMode);
    activity.setRequestedOrientation(ai.screenOrientation);
  }

  @Override
  public void callActivityOnDestroy(Activity activity) {
    Map<String, ActivityInfo> loadedActivities = apkBundleLauncher.loadedActivities();
    do {
      if (loadedActivities == null) break;
      String realClazz = activity.getClass().getName();
      ActivityInfo ai = loadedActivities.get(realClazz);
      if (ai == null) break;
      inqueueStubActivity(ai, realClazz);
    } while (false);
    apkBundleLauncher.hostInstrumentation().callActivityOnDestroy(activity);
  }

  private void wrapIntent(Intent intent) {
    ComponentName component = intent.getComponent();
    String realClazz;
    // todo handle implicit way to launch activity
    if (component == null) {
      // Implicit way to start an activity
      component = intent.resolveActivity(Small.hostApplication().getPackageManager());
      if (component != null) return; // ignore system or host action

      realClazz = resolveActivity(intent);
      if (realClazz == null) return;
    } else {
      realClazz = component.getClassName();
    }

    Map<String, ActivityInfo> loadedActivities = apkBundleLauncher.loadedActivities();
    if (loadedActivities == null) return;

    ActivityInfo ai = loadedActivities.get(realClazz);
    if (ai == null) return;

    // Carry the real(plugin) class for incoming `newActivity' method.
    intent.addCategory(REDIRECT_FLAG + realClazz);
    String stubClazz = dequeueStubActivity(ai, realClazz);
    intent.setComponent(new ComponentName(Small.hostApplication(), stubClazz));
  }

  private String resolveActivity(Intent intent) {
    Map<String, ActivityInfo> loadedActivities = apkBundleLauncher.loadedActivities();
    Map<String, List<IntentFilter>> loadedIntentFilters = apkBundleLauncher.loadedIntentFilters();
    if (loadedActivities == null) return null;

    Iterator<Map.Entry<String, List<IntentFilter>>> it = loadedIntentFilters.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, List<IntentFilter>> entry = it.next();
      List<IntentFilter> filters = entry.getValue();
      for (IntentFilter filter : filters) {
        if (filter.hasAction(Intent.ACTION_VIEW)) {
          // TODO: match uri
        }
        if (filter.hasCategory(Intent.CATEGORY_DEFAULT)) {
          // custom action
          if (filter.hasAction(intent.getAction())) {
            // hit
            return entry.getKey();
          }
        }
      }
    }
    return null;
  }

  private String[] mStubQueue;

  /** Get an usable stub activity clazz from real activity */
  private String dequeueStubActivity(ActivityInfo ai, String realActivityClazz) {
    if (ai.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
      // In standard mode, the stub activity is reusable.
      // Cause the `windowIsTranslucent' attribute cannot be dynamically set,
      // We should choose the STUB activity with translucent or not here.
      Resources.Theme theme = Small.hostApplication().getResources().newTheme();
      theme.applyStyle(ai.getThemeResource(), true);
      TypedArray sa = theme.obtainStyledAttributes(
          new int[] { android.R.attr.windowIsTranslucent });
      boolean translucent = sa.getBoolean(0, false);
      sa.recycle();
      return translucent ? STUB_ACTIVITY_TRANSLUCENT : STUB_ACTIVITY_PREFIX;
    }

    int availableId = -1;
    int stubId = -1;
    int countForMode = STUB_ACTIVITIES_COUNT;
    int countForAll = countForMode * 3; // 3=[singleTop, singleTask, singleInstance]
    if (mStubQueue == null) {
      // Lazy init
      mStubQueue = new String[countForAll];
    }
    int offset = (ai.launchMode - 1) * countForMode;
    for (int i = 0; i < countForMode; i++) {
      String usedActivityClazz = mStubQueue[i + offset];
      if (usedActivityClazz == null) {
        if (availableId == -1) availableId = i;
      } else if (usedActivityClazz.equals(realActivityClazz)) {
        stubId = i;
      }
    }
    if (stubId != -1) {
      availableId = stubId;
    } else if (availableId != -1) {
      mStubQueue[availableId + offset] = realActivityClazz;
    } else {
      // TODO:
      Log.e(TAG, "Launch mode " + ai.launchMode + " is full");
    }
    return STUB_ACTIVITY_PREFIX + ai.launchMode + availableId;
  }

  /** Unbind the stub activity from real activity */
  private void inqueueStubActivity(ActivityInfo ai, String realActivityClazz) {
    if (ai.launchMode == ActivityInfo.LAUNCH_MULTIPLE) return;
    if (mStubQueue == null) return;

    int countForMode = STUB_ACTIVITIES_COUNT;
    int offset = (ai.launchMode - 1) * countForMode;
    for (int i = 0; i < countForMode; i++) {
      String stubClazz = mStubQueue[i + offset];
      if (stubClazz != null && stubClazz.equals(realActivityClazz)) {
        mStubQueue[i + offset] = null;
        break;
      }
    }
  }
}
