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
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.text.TextUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.wequick.small.util.BundleParser;
import net.wequick.small.util.ReflectAccelerator;

public class ApkBundleLauncher {

    public static final char REDIRECT_FLAG = '>';

    private Map<String, LoadedApk> loadedApks = new HashMap<>();
    private Map<String, ActivityInfo> loadedActivities = new HashMap<>();
    private Map<String, List<IntentFilter>> loadedIntentFilters = new HashMap<>();

    private Instrumentation hostInstrumentation;

    public void setup(Context context) throws LauncherSetupException {
        try {
            hook(context);
        } catch (Exception e) {
          throw new LauncherSetupException("fail to setup launcher", e);
        }
    }

    private void hook(Context context)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
        NoSuchFieldException, ClassNotFoundException {
        final Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        final Method method = activityThreadClass.getMethod("currentActivityThread");
        Object thread = method.invoke(null, (Object[]) null);
        Field field = activityThreadClass.getDeclaredField("mInstrumentation");
        field.setAccessible(true);
        hostInstrumentation = (Instrumentation) field.get(thread);
        Instrumentation wrapper = new InstrumentationWrapper(this);
        field.set(thread, wrapper);

        field = activityThreadClass.getDeclaredField("mH");
        field.setAccessible(true);
        Handler ah = (Handler) field.get(thread);
        field = Handler.class.getDeclaredField("mCallback");
        field.setAccessible(true);
        field.set(ah, new ActivityThreadHandlerCallback(this));
    }

    public void registerBundle(BundleParser parser, LoadedApk loadedApk)
        throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        registerActivities(parser.getPackageInfo());
        registerIntentFilters(parser);
        registerApk(parser.getPackageInfo().packageName, loadedApk);

        String bundleApplicationName = parser.getPackageInfo().applicationInfo.className;
        if (!TextUtils.isEmpty(bundleApplicationName)) {
            createApplication(bundleApplicationName);
        }

        // Merge all the resources in bundles and replace the host one
        Application app = Small.hostApplication();
        ResourcesMerger rm = ResourcesMerger.merge(app.getBaseContext(), this);
        ReflectAccelerator.setResources(app, rm);
    }

    private void registerActivities(PackageInfo pluginInfo) {
        // Record activities for intent redirection
        if (pluginInfo.activities == null) {
            return;
        }
        for (ActivityInfo ai : pluginInfo.activities) {
            loadedActivities.put(ai.name, ai);
        }
    }

    private void registerApk(String packageName, LoadedApk loadedApk) {
        loadedApks.put(packageName, loadedApk);
    }

    private void registerIntentFilters(BundleParser parser) {
        // Record intent-filters for implicit action
        ConcurrentHashMap<String, List<IntentFilter>> filters = parser.getIntentFilters();
        if (filters != null) {
            loadedIntentFilters.putAll(filters);
        }
    }

    private void createApplication(String bundleApplicationName)
        throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // Call bundle application onCreate
        Class applicationClass = Class.forName(bundleApplicationName);
        Application bundleApplication = Instrumentation.newApplication(
            applicationClass, Small.hostApplication());
        hostInstrumentation.callApplicationOnCreate(bundleApplication);
    }

    public Instrumentation hostInstrumentation() {
        return hostInstrumentation;
    }

    public Map<String, ActivityInfo> loadedActivities() {
        return loadedActivities;
    }

    public Map<String, LoadedApk> loadedApks() {
        return loadedApks;
    }

    public Map<String, List<IntentFilter>> loadedIntentFilters() {
        return loadedIntentFilters;
    }

    public void launchActivity(Class target, Intent intent, Context context) {
        intent.setComponent(new ComponentName(context, target));
        context.startActivity(intent);
    }


    public static class LauncherSetupException extends Exception {
        public LauncherSetupException() {
        }

        public LauncherSetupException(String detailMessage) {
            super(detailMessage);
        }

        public LauncherSetupException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public LauncherSetupException(Throwable throwable) {
            super(throwable);
        }
    }
}
