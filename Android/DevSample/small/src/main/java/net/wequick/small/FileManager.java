package net.wequick.small;

import java.io.File;
import net.wequick.small.util.FileUtils;

public final class FileManager {

  public static final String BUNDLE_MANIFEST_NAME = "bundle.json";
  public static final String SMALL_UPGRADE_JAR = "small.jar";

  private static final String SMALL_DIR = "small";
  private static final String SMALL_WORKING_DIR = "working";
  private static final String SMALL_BUNDLE_DIR = "bundles";
  private static final String SMALL_OPT_DEX_DIR = "opt_dexes";
  private static final String SMALL_UPGRADE_DIR = "upgrade";

  public static File smallDir() {
    File dir = new File(Small.hostApplication().getFilesDir() + File.separator + SMALL_DIR);
    FileUtils.ensureDir(dir);
    return dir;
  }

  public static File smallWorkingDir() {
    File dir = new File(smallDir(), SMALL_WORKING_DIR);
    FileUtils.ensureDir(dir);
    return dir;
  }

  public static File smallBundleManifestDir() {
    return smallWorkingDir();
  }

  public static File smallBundlesDir() {
    File dir = new File(smallWorkingDir(), SMALL_BUNDLE_DIR);
    FileUtils.ensureDir(dir);
    return dir;
  }

  public static File smallOptDexDir() {
    File dir = new File(smallWorkingDir(), SMALL_OPT_DEX_DIR);
    FileUtils.ensureDir(dir);
    return dir;
  }

  public static File smallUpgradeDir() {
    File dir = new File(smallDir(), SMALL_UPGRADE_DIR);
    FileUtils.ensureDir(dir);
    return dir;
  }

  // todo // FIXME: 16/4/19
  public static File libDir() {
    File dir = new File(Small.hostApplication().getApplicationInfo().nativeLibraryDir);
    FileUtils.ensureDir(dir);
    return dir;
  }
}
