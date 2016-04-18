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

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import java.io.File;
import net.wequick.small.util.BundleParser;
import net.wequick.small.util.FileUtils;
import net.wequick.small.util.JNIUtils;
import net.wequick.small.util.ReflectAccelerator;

public class Bundle {

    private static final String FILE_DEX = "bundle.dex";

    private File bundleFile = null;

    private BundleParser bundleParser;
    private BundleManifest.BundleInfo bundleInfo;
    private LoadedApk loadedApk;
    private ApkBundleLauncher apkBundleLauncher;

    public Bundle(BundleManifest.BundleInfo bundleInfo, ApkBundleLauncher apkBundleLauncher)
        throws BundleLoadException, BundleParser.BundleParseException {
        this.bundleInfo = bundleInfo;
        this.apkBundleLauncher = apkBundleLauncher;
        init();
    }

    private void init() throws BundleLoadException, BundleParser.BundleParseException {
        String bundleUri = bundleInfo.uri();
        if (TextUtils.isEmpty(bundleUri)) {
            throw new IllegalArgumentException("bundle uri in Bundle.json cannot be empty");
        }
        if (bundleUri.contains("/")) {
            throw new IllegalArgumentException("bundle uri in Bundle.json cannot contain '/' for it means path");
        }

        String packageName = bundleInfo.packageName();
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("pkg in Bundle.json cannot be empty");
        }

        String bundlePath = FileManager.smallBundlesDir();
        FileUtils.ensureDir(bundlePath);
        String bundleName = packageName.replaceAll("\\.", "_") + ".bundle";
        bundleFile = new File(bundlePath, bundleName);

        if (Small.isNewHostApp()) {
            FileUtils.copyAsset(bundleName, bundleFile);
        }

        bundleParser = new BundleParser(bundleFile, bundleInfo.packageName());
        bundleParser.parsePackage();
        load();
        // Record version code for upgrade
        SharedPreferenceManager.setBundleVersionCode(bundleInfo.packageName(), bundleParser.getPackageInfo().versionCode);
    }

    private void load() throws BundleLoadException {
        String packageName = bundleParser.getPackageInfo().packageName;

        bundleParser.collectActivities();
        PackageInfo pluginInfo = bundleParser.getPackageInfo();

        // Load the bundle
        loadedApk = new LoadedApk();
        loadedApk.assetPath = bundleParser.getSourcePath();
        loadedApk.activities = pluginInfo.activities;

        // Add dex element to class loader's pathList
        File packagePath = new File(new File(FileManager.smallOptDexDir()), packageName);
        FileUtils.ensureDir(packagePath.getAbsolutePath());
        File optDexFile = new File(packagePath, FILE_DEX);

        // todo handle upgrade
        //if (Small.getBundleUpgraded(packageName)) {
        //    // If upgraded, delete the opt dex file for recreating
        //    if (optDexFile.exists()) optDexFile.delete();
        //    Small.setBundleUpgraded(packageName, false);
        //}

        String apkPath = loadedApk.assetPath;
        ReflectAccelerator.expandDexPathList(Small.hostApplication().getClassLoader(), apkPath, optDexFile.getPath());
        loadedApk.dexFile = optDexFile;

        // Expand the native library directories if plugin has any JNIs. (#79)
        int abiFlags = bundleParser.getABIFlags();
        String abiPath = JNIUtils.getExtractABI(abiFlags, FileManager.libDir().contains("64"));
        if (abiPath != null) {
            String libDir = FileManager.libDir() + File.separator + abiPath + File.separator;
            FileUtils.ensureDir(libDir);
            File libPath = new File(libDir);
            try {
                // Extract the JNIs with specify ABI
                FileUtils.unZipFolder(new File(apkPath), packagePath, libDir);
                // Add the JNI search path
                ReflectAccelerator.expandNativeLibraryDirectories(Small.hostApplication().getClassLoader(), libPath);
            } catch (Exception e) {
                throw new BundleLoadException("fail to load bundle: " + bundleInfo, e);
            }
        }

        try {
            apkBundleLauncher.registerBundle(bundleParser, loadedApk);
        } catch (Exception e) {
            throw new BundleLoadException("fail to load bundle: " + bundleInfo, e);
        }
    }

    public boolean isTarget(String uriStr) {
        String host = uriStr;
        int splashIndex = uriStr.indexOf('/');
        if (splashIndex != -1) {
            host = uriStr.substring(0, splashIndex);
        }
        return host.equals(bundleInfo.uri());
    }

    public Class getTargetClass(String uriStr) throws ClassNotFoundException {
        String path = "";
        int splashIndex = uriStr.indexOf('/');
        if (splashIndex != -1) {
            path = uriStr.substring(splashIndex + 1);
        }
        ActivityInfo[] activityInfos = bundleParser.getPackageInfo().activities;
        String activityName = activityInfos[0].name;
        if (!TextUtils.isEmpty(path)) {
            activityName = bundleInfo.rules().get(path);
        }
        if (TextUtils.isEmpty(activityName)) {
            throw new ClassNotFoundException();
        }
        if (activityName.startsWith(".")) {
            activityName = bundleParser.getPackageInfo().packageName + activityName;
        }
        return Class.forName(activityName);
    }

    public LoadedApk loadedApk() {
        return loadedApk;
    }

    public static class BundleLoadException extends Exception {
        public BundleLoadException() {
        }

        public BundleLoadException(String detailMessage) {
            super(detailMessage);
        }

        public BundleLoadException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }

    //public Intent getIntent() { return mIntent; }
    //public void setIntent(Intent intent) { mIntent = intent; }
    //
    //public String getPackageName() {
    //    return mPackageName;
    //}
    //
    //public Uri getUri() {
    //    return uri;
    //}
    //
    //public void setURL(URL url) {
    //    this.url = url;
    //}
    //
    //public URL getURL() {
    //    return url;
    //}
    //
    //public File getBuiltinFile() {
    //    return bundleFile;
    //}
    //
    //public File getPatchFile() {
    //    return mPatchFile;
    //}
    //
    //public void setPatchFile(File file) {
    //    mPatchFile = file;
    //}
    //
    //public String getType() {
    //    return type;
    //}
    //
    //public void setType(String type) {
    //    this.type = type;
    //}
    //
    //public String getQuery() {
    //    return query;
    //}
    //
    //public void setQuery(String query) {
    //    this.query = query;
    //}
    //
    //public String getPath() {
    //    return path;
    //}
    //
    //public void setPath(String path) {
    //    this.path = path;
    //}
    //
    //public boolean isLaunchable() {
    //    return launchable && enabled;
    //}
    //
    //public void setLaunchable(boolean flag) {
    //    this.launchable = flag;
    //}
    //
    //public <T> T createObject(Context context, String type) {
    //    if (mApplicableLauncher == null) {
    //        setup();
    //    }
    //    if (mApplicableLauncher == null) return null;
    //    return mApplicableLauncher.createObject(this, context, type);
    //}
    //
    //public boolean isEnabled() {
    //    return enabled;
    //}
    //
    //public void setEnabled(boolean enabled) {
    //    this.enabled = enabled;
    //}
    //
    //public boolean isPatching() {
    //    return patching;
    //}
    //
    //public void setPatching(boolean patching) {
    //    this.patching = patching;
    //}
    //
    //public BundleParser getBundleParser() {
    //    return bundleParser;
    //}
    //
    //public void setBundleParser(BundleParser bundleParser) {
    //    this.bundleParser = bundleParser;
    //}
    //
    //public static void setBundleLastModified(String bundleName, long lastModified) {
    //    SharedPreferences sp = hostApplication.
    //        getSharedPreferences(SHARED_PREFERENCES_BUNDLE_MODIFIES, 0);
    //    SharedPreferences.Editor editor = sp.edit();
    //    editor.putLong(bundleName, lastModified);
    //    editor.commit();
    //}
    //
    //public static long getBundleLastModified(String bundleName) {
    //    SharedPreferences sp = hostApplication.
    //        getSharedPreferences(SHARED_PREFERENCES_BUNDLE_MODIFIES, 0);
    //    if (sp == null) return 0;
    //    return sp.getLong(bundleName, 0);
    //}
    //
    //public static void setBundleUpgraded(String bundleName, boolean flag) {
    //    SharedPreferences sp = hostApplication.
    //        getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
    //    SharedPreferences.Editor editor = sp.edit();
    //    editor.putBoolean(bundleName, flag);
    //    editor.commit();
    //}
    //
    //public static boolean getBundleUpgraded(String bundleName) {
    //    SharedPreferences sp = hostApplication.
    //        getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
    //    if (sp == null) return false;
    //    return sp.getBoolean(bundleName, false);
    //}
}
