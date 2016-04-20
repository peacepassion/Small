/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package net.wequick.small;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.wequick.small.util.FileUtils;

public final class Small {

    public static final String LOG_TAG = Small.class.getSimpleName();
    public static final String KEY_ACTIVITY_URI = "key_activity_uri";

    private static Application hostApplication;
    private static ApkBundleLauncher apkBundleLauncher;
    private static List<Bundle> loadedBundles = new ArrayList<>();
    private static boolean isNewHostApp; // first launched or upgraded
    private static boolean bundleUpdated;
    private static BundleManifest loadedBundleManifest;
    private static Gson gson = new Gson();

    public static Application hostApplication() {
        return hostApplication;
    }

    public static boolean isNewHostApp() {
        return isNewHostApp;
    }

    public static boolean bundleUpdated() {
        return bundleUpdated;
    }

    public static void setup(Application application) throws SmallSetupException {
        long setupStartTime = 0;
        if (BuildConfig.DEBUG) {
            setupStartTime = System.currentTimeMillis();
        }
        try {
            hostApplication = application;
            SharedPreferenceManager.init(hostApplication);
            handleVersionChange();
            apkBundleLauncher = ApkBundleLauncher.setup(hostApplication);
            parseBundleManifest();
            handleUpgrade();
            loadBundles();
        } catch (Exception e) {
            throw new SmallSetupException(e);
        }
        if (BuildConfig.DEBUG) {
            long setupEndTime = System.currentTimeMillis();
            Log.d(LOG_TAG, "Small setup consumes: " + (setupEndTime - setupStartTime) + " ms");
        }
    }

    private static void handleVersionChange() {
        // Check if host app is first-installed or upgraded
        PackageManager pm = hostApplication.getPackageManager();
        String packageName = hostApplication.getPackageName();
        int backupHostVersion = SharedPreferenceManager.getHostVersionCode();
        int currHostVersion = 0;
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            currHostVersion = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (backupHostVersion != currHostVersion) {
            isNewHostApp = true;
            SharedPreferenceManager.setHostVersionCode(currHostVersion);
            clearSmallCache();
        } else {
            isNewHostApp = false;
        }
    }

    private static void clearSmallCache() {
        File dir = FileManager.smallDir();
        FileUtils.deleteFile(dir);
    }

  private static void handleUpgrade() {
      File upgradeDir = FileManager.smallUpgradeDir();
      File upgradeJar = new File(upgradeDir, FileManager.SMALL_UPGRADE_JAR);
      if (!upgradeJar.exists()) {
          return;
      }
      // todo check security at first
      try {
          JarFile jarFile = new JarFile(upgradeJar);
          if (!checkUpgradeJarVersion(jarFile)) {
              upgradeJar.delete();
              return;
          }

          // extract the content to the working directory
          Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
              JarEntry entry = entries.nextElement();
              String name = entry.getName();
              File dest = null;
              if (name.equals(FileManager.BUNDLE_MANIFEST_NAME)) {
                  dest = new File(FileManager.smallWorkingDir(), FileManager.BUNDLE_MANIFEST_NAME);
              } else if (name.endsWith(".bundle")) {
                  dest = new File(FileManager.smallBundlesDir(), name);
              }
              if (dest != null) {
                  if (dest.exists()) {
                      dest.delete();
                  }
                  FileUtils.ensureFile(dest);
                  FileOutputStream fos = new FileOutputStream(dest);
                  InputStream is = jarFile.getInputStream(entry);
                  byte[] buffer = new byte[is.available()];
                  is.read(buffer);
                  fos.write(buffer);
                  is.close();
                  fos.close();
              }
          }

          bundleUpdated = true;

          // delete upgrade package
          FileUtils.deleteFile(upgradeJar);
      } catch (IOException e) {
          e.printStackTrace();
      }

      // parse bundle manifest again
      parseBundleManifest();
    }

    private static boolean checkUpgradeJarVersion(JarFile upgradeJar) throws IOException {
        Enumeration<JarEntry> entries = upgradeJar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.equals(FileManager.BUNDLE_MANIFEST_NAME)) {
                InputStream is = upgradeJar.getInputStream(entry);
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                String content = new String(buffer);
                BundleManifest bundleManifest = gson.fromJson(content, BundleManifest.class);
                if (bundleManifest.versionCode() > loadedBundleManifest.versionCode()) {
                    return true;
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, "version code in the upgraded bundle is small than working bundle");
        }

        return false;
    }

    private static void parseBundleManifest() {
        long parseStartTime = 0;
        if (BuildConfig.DEBUG) {
            parseStartTime = System.currentTimeMillis();
        }

        String jsonStr = null;
        File manifestFile = new File(FileManager.smallBundleManifestDir(), FileManager.BUNDLE_MANIFEST_NAME);
        try {
            InputStream is;
            if (manifestFile.exists()) {
                is = new FileInputStream(manifestFile);
            } else {
                // Copy asset to files
                is = hostApplication.getAssets().open(FileManager.BUNDLE_MANIFEST_NAME);
            }

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            if (!manifestFile.exists()) {
                FileUtils.ensureFile(manifestFile);
                FileOutputStream os = new FileOutputStream(manifestFile);
                os.write(buffer);
                os.close();
            }

            jsonStr = new String(buffer, 0, size);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Parse manifest file
        loadedBundleManifest = gson.fromJson(jsonStr, BundleManifest.class);

        if (BuildConfig.DEBUG) {
            long parseEndTime = System.currentTimeMillis();
            Log.d(LOG_TAG, "parse manifest consumes: " + (parseEndTime - parseStartTime) + " ms");
        }
    }

    private static void loadBundles() throws Exception {
        List<BundleManifest.BundleInfo> bundleInfoList = loadedBundleManifest.bundleInfoList();
        Exception exception = null;
        for (BundleManifest.BundleInfo bundleInfo : bundleInfoList) {
            try {
                long loadStartTime = 0;
                if (BuildConfig.DEBUG) {
                    loadStartTime = System.currentTimeMillis();
                }

                Bundle bundle = new Bundle(bundleInfo, apkBundleLauncher);
                loadedBundles.add(bundle);

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "bundle loaded: " + bundleInfo);
                    long loadEndTime = System.currentTimeMillis();
                    Log.d(LOG_TAG, "load bundle " + bundleInfo.packageName() + " consumes: " + (loadEndTime - loadStartTime) + " ms");
                }
            } catch (Exception e) {
                e.printStackTrace();
                exception = e;
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    // todo integrate with scheme
    public static void launchBundleActivity(Intent intent, Context context) {
        String uriStr = intent.getStringExtra(KEY_ACTIVITY_URI);
        if (TextUtils.isEmpty(uriStr)) {
            throw new IllegalArgumentException("intent must contain a valid key value of Small#KEY_ACTIVITY_URI");
        }
        Bundle bundle = findTargetBundle(uriStr);
        if (bundle == null) {
            Log.w(LOG_TAG, "fail to find target bundle for uri: " + uriStr);
            return;
        }
        try {
            Class target = bundle.getTargetClass(uriStr);
            apkBundleLauncher.launchActivity(target, intent, context);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // todo check the
    private static Bundle findTargetBundle(String uriStr) {
        for (Bundle bundle : loadedBundles) {
            if (bundle.isTarget(uriStr)) {
                return bundle;
            }
        }
        return null;
    }

    public static class SmallSetupException extends Exception {
        public SmallSetupException(Throwable throwable) {
            super(throwable);
        }
    }
}
