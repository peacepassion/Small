package net.wequick.small;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class BundleManifest {

  @SerializedName("version_name") private String versionName;
  @SerializedName("version_code") private int versionCode;
  @SerializedName("bundles") private List<BundleInfo> bundleInfoList;

  public List<BundleInfo> bundleInfoList() {
    return bundleInfoList;
  }

  public String versionName() {
    return versionName;
  }

  public int versionCode() {
    return versionCode;
  }

  public static class BundleInfo {
    @SerializedName("uri") private String uri;
    @SerializedName("pkg") private String packageName;
    @SerializedName("rules") private Map<String, String> rules;

    public String packageName() {
      return packageName;
    }

    public Map<String, String> rules() {
      return rules;
    }

    public String uri() {
      return uri;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("uri: ").append(uri).append("\n")
          .append("pkg: ").append(packageName).append("\n")
          .append("rules: ").append('{').append("\n");
      if (rules != null) {
        for (Map.Entry<String, String> rule : rules.entrySet()) {
          sb.append(rule.getKey()).append(": ").append(rule.getValue()).append("\n");
        }
      }
      sb.append('}').append("\n");
      return sb.toString();
    }
  }
}
