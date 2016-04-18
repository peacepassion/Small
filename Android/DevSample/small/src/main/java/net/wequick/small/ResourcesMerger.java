package net.wequick.small;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import net.wequick.small.util.ReflectAccelerator;

public class ResourcesMerger extends Resources {
  public ResourcesMerger(AssetManager assets, DisplayMetrics metrics, Configuration config) {
    super(assets, metrics, config);
  }

  public static ResourcesMerger merge(Context context, ApkBundleLauncher apkBundleLauncher) {
    AssetManager assets = ReflectAccelerator.newAssetManager();

    // Add plugin asset paths
    for (LoadedApk apk : apkBundleLauncher.loadedApks().values()){
      ReflectAccelerator.addAssetPath(assets, apk.assetPath);
    }
    // Add host asset path
    ReflectAccelerator.addAssetPath(assets, context.getPackageResourcePath());

    Resources base = context.getResources();
    return new ResourcesMerger(assets,
        base.getDisplayMetrics(), base.getConfiguration());
  }
}
