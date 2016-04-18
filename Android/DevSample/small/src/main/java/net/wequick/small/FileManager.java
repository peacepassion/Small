package net.wequick.small;

import java.io.File;

public final class FileManager {

  public static final String SMALL_DIR = "small";
  public static final String SMALL_BUNDLE_DIR = "bundles";
  public static final String SMALL_OPT_DEX_DIR = "opt_dexes";
  public static final String BUNDLE_MANIFEST_NAME = "bundle.json";

  public static String smallDir() {
    return Small.hostApplication().getFilesDir() + File.separator + SMALL_DIR;
  }

  public static String smallBundleManifestDir() {
    return smallDir();
  }

  public static String smallBundlesDir() {
    return smallDir() + File.separator + SMALL_BUNDLE_DIR;
  }

  public static String smallOptDexDir() {
    return smallDir() + File.separator + SMALL_OPT_DEX_DIR;
  }

  public static String libDir() {
    return Small.hostApplication().getApplicationInfo().nativeLibraryDir;
  }
}
