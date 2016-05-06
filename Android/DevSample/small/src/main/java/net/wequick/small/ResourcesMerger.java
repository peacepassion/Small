package net.wequick.small;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import net.wequick.small.util.ReflectAccelerator;

public class ResourcesMerger {

  public static Resources merge(Context context, ApkBundleLauncher apkBundleLauncher) {
    AssetManager assets = ReflectAccelerator.newAssetManager();

    // Add plugin asset paths
    for (LoadedApk apk : apkBundleLauncher.loadedApks().values()){
      ReflectAccelerator.addAssetPath(assets, apk.assetPath);
    }
    // Add host asset path
    ReflectAccelerator.addAssetPath(assets, context.getPackageResourcePath());

    Resources base = context.getResources();
    DisplayMetrics metrics = base.getDisplayMetrics();
    Configuration configuration = base.getConfiguration();
    Class baseClass = base.getClass();
    if (baseClass == Resources.class) {
      return new Resources(assets, metrics, configuration);
    } else {
      // Some crazy manufacturers will modify the application resources class.
      // As Nubia, it use `NubiaResources'. So we had to create a related instance. #135
      return ReflectAccelerator.newResources(baseClass, assets, metrics, configuration);
    }
  }
}
