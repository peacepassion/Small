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

package net.wequick.small.util;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * This class consists exclusively of static methods that accelerate reflections.
 */
public class ReflectAccelerator {
    // AssetManager.addAssetPath
    private static Method sAddAssetPath;
    // DexPathList
    private static Constructor sDexElementConstructor;
    private static Class sDexElementClass;
    private static Field sPathListField;
    private static Field sDexElementsField;
    private static Field sDexPathList_nativeLibraryDirectories_field;
    private static Field sDexPathList_nativeLibraryPathElements_field;
    // ApplicationInfo.resourceDirs
    private static Field sContextImpl_mResources_field;
    private static Method sInstrumentation_execStartActivityV21_method;
    private static Method sInstrumentation_execStartActivityV20_method;
    // DexClassLoader - V13
    private static Field sDexClassLoader_mFiles_field;
    private static Field sDexClassLoader_mPaths_field;
    private static Field sDexClassLoader_mZips_field;
    private static Field sDexClassLoader_mDexs_field;
    private static Field sPathClassLoader_libraryPathElements_field;
    // ActivityClientRecord
    private static Field sActivityClientRecord_intent_field;
    private static Field sActivityClientRecord_activityInfo_field;

    private static Instrumentation hostInstrumentation;

    private ReflectAccelerator() { /** cannot be instantiated */ }

    //______________________________________________________________________________________________
    // API

    public static int addAssetPath(AssetManager assets, String path) {
        if (sAddAssetPath == null) {
            sAddAssetPath = getMethod(AssetManager.class,
                    "addAssetPath", new Class[]{String.class});
        }
        if (sAddAssetPath == null) return 0;
        Integer ret = invoke(sAddAssetPath, assets, path);
        if (ret == null) return 0;
        return ret;
    }

    public static Instrumentation hostInstrumentation()
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
        IllegalAccessException, NoSuchFieldException {
        if (hostInstrumentation != null) {
            return hostInstrumentation;
        }
        final Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        final Method method = activityThreadClass.getMethod("currentActivityThread");
        Object thread = method.invoke(null, (Object[]) null);
        Field field = activityThreadClass.getDeclaredField("mInstrumentation");
        field.setAccessible(true);
        hostInstrumentation = (Instrumentation) field.get(thread);
        return hostInstrumentation;
    }

    public static void hookInstrumentation(Instrumentation wrapper, Context context)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, NoSuchFieldException {
        final Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        final Method method = activityThreadClass.getMethod("currentActivityThread");
        Object thread = method.invoke(null, (Object[]) null);
        Field field = activityThreadClass.getDeclaredField("mInstrumentation");
        field.setAccessible(true);
        hostInstrumentation = (Instrumentation) field.get(thread);
        field.set(thread, wrapper);

        if (context instanceof Activity) {
            field = Activity.class.getDeclaredField("mInstrumentation");
            field.setAccessible(true);
            field.set(context, wrapper);
        }
    }

    //______________________________________________________________________________________________
    // Dex path list

    /**
     * Make dex element
     * @see <a href="https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java">DexPathList.java</a>
     * @param pkg archive android package with any file extensions
     * @param dexFile
     * @return dalvik.system.DexPathList$Element
     */
    private static Object makeDexElement(File pkg, DexFile dexFile) throws Exception {
        return makeDexElement(pkg, false, dexFile);
    }

    private static Object makeDexElement(File dir) throws Exception {
        return makeDexElement(dir, true, null);
    }

    private static Object makeDexElement(File pkg, boolean isDirectory, DexFile dexFile) throws Exception {
        if (sDexElementClass == null) {
            sDexElementClass = Class.forName("dalvik.system.DexPathList$Element");
        }
        if (sDexElementConstructor == null) {
            sDexElementConstructor = sDexElementClass.getConstructors()[0];
        }
        Class<?>[] types = sDexElementConstructor.getParameterTypes();
        switch (types.length) {
            case 3:
                if (types[1].equals(ZipFile.class)) {
                    // Element(File apk, ZipFile zip, DexFile dex)
                    ZipFile zip = new ZipFile(pkg);
                    return sDexElementConstructor.newInstance(pkg, zip, dexFile);
                } else {
                    // Element(File apk, File zip, DexFile dex)
                    return sDexElementConstructor.newInstance(pkg, pkg, dexFile);
                }
            case 4:
            default:
                // Element(File apk, boolean isDir, File zip, DexFile dex)
                if (isDirectory) {
                    return sDexElementConstructor.newInstance(pkg, true, null, null);
                } else {
                    return sDexElementConstructor.newInstance(pkg, false, pkg, dexFile);
                }
        }
    }

    public static boolean expandDexPathList(ClassLoader cl, String dexPath, String optDexPath) {
        if (Build.VERSION.SDK_INT < 14) {
            try {
                /*
                 * see https://android.googlesource.com/platform/libcore/+/android-2.3_r1/dalvik/src/main/java/dalvik/system/DexClassLoader.java
                 */
                if (sDexClassLoader_mFiles_field == null) {
                    sDexClassLoader_mFiles_field = getDeclaredField(cl.getClass(), "mFiles");
                    sDexClassLoader_mPaths_field = getDeclaredField(cl.getClass(), "mPaths");
                    sDexClassLoader_mZips_field = getDeclaredField(cl.getClass(), "mZips");
                    sDexClassLoader_mDexs_field = getDeclaredField(cl.getClass(), "mDexs");
                }
                if (sDexClassLoader_mFiles_field == null
                        || sDexClassLoader_mPaths_field == null
                        || sDexClassLoader_mZips_field == null
                        || sDexClassLoader_mDexs_field == null) {
                    return false;
                }
                
                File pathFile = new File(dexPath);
                expandArray(cl, sDexClassLoader_mFiles_field, new Object[]{pathFile}, true);

                expandArray(cl, sDexClassLoader_mPaths_field, new Object[]{dexPath}, true);

                ZipFile zipFile = new ZipFile(dexPath);
                expandArray(cl, sDexClassLoader_mZips_field, new Object[]{zipFile}, true);

                DexFile dexFile = DexFile.loadDex(dexPath, optDexPath, 0);
                expandArray(cl, sDexClassLoader_mDexs_field, new Object[]{dexFile}, true);
            } catch (Exception e) {
                return false;
            }
        } else {
            try {
                File pkg = new File(dexPath);
                DexFile dexFile = DexFile.loadDex(dexPath, optDexPath, 0);
                Object element = makeDexElement(pkg, dexFile);
                fillDexPathList(cl, element);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public static void expandNativeLibraryDirectories(ClassLoader classLoader, File libPath) {
        if (Build.VERSION.SDK_INT < 14) {
            if (sPathClassLoader_libraryPathElements_field == null) {
                sPathClassLoader_libraryPathElements_field = getDeclaredField(
                        classLoader.getClass(), "libraryPathElements");
            }
            List<String> paths = getValue(sPathClassLoader_libraryPathElements_field, classLoader);
            if (paths == null) return;
            paths.add(libPath.getAbsolutePath() + File.separator);
            return;
        }

        if (sPathListField == null) return;

        Object pathList = getValue(sPathListField, classLoader);
        if (pathList == null) return;

        if (sDexPathList_nativeLibraryDirectories_field == null) {
            sDexPathList_nativeLibraryDirectories_field = getDeclaredField(
                    pathList.getClass(), "nativeLibraryDirectories");
            if (sDexPathList_nativeLibraryDirectories_field == null) return;
        }

        try {
            if (Build.VERSION.SDK_INT < 23) {
                // File[] nativeLibraryDirectories
                File[] paths = new File[]{libPath};
                expandArray(pathList, sDexPathList_nativeLibraryDirectories_field, paths, false);
            } else {
                // List<File> nativeLibraryDirectories
                List<File> paths = getValue(sDexPathList_nativeLibraryDirectories_field, pathList);
                if (paths == null) return;
                paths.add(libPath);

                // Element[] nativeLibraryPathElements
                if (sDexPathList_nativeLibraryPathElements_field == null) {
                    sDexPathList_nativeLibraryPathElements_field = getDeclaredField(
                            pathList.getClass(), "nativeLibraryPathElements");
                }
                if (sDexPathList_nativeLibraryPathElements_field == null) return;

                Object dexElement = makeDexElement(libPath);
                expandArray(pathList, sDexPathList_nativeLibraryPathElements_field,
                        new Object[]{dexElement}, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add elements to Object[] with reflection
     * @see <a href="https://github.com/casidiablo/multidex/blob/publishing/library/src/android/support/multidex/MultiDex.java">MultiDex</a>
     * @param target
     * @param arrField
     * @param extraElements
     * @param push true=push to array head, false=append to array tail
     * @throws IllegalAccessException
     */
    public static void expandArray(Object target, Field arrField,
                                   Object[] extraElements, boolean push)
            throws IllegalAccessException {
        Object[] original = (Object[]) arrField.get(target);
        Object[] combined = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length + extraElements.length);
        if (push) {
            System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
            System.arraycopy(original, 0, combined, extraElements.length, original.length);
        } else {
            System.arraycopy(original, 0, combined, 0, original.length);
            System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
        }
        arrField.set(target, combined);
    }

    public static void sliceArray(Object target, Field arrField, int deleteIndex)
            throws  IllegalAccessException {
        Object[] original = (Object[]) arrField.get(target);
        if (original.length == 0) return;

        Object[] sliced = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length - 1);
        if (deleteIndex > 0) {
            // Copy left elements
            System.arraycopy(original, 0, sliced, 0, deleteIndex);
        }
        int rightCount = original.length - deleteIndex - 1;
        if (rightCount > 0) {
            // Copy right elements
            System.arraycopy(original, deleteIndex + 1, sliced, deleteIndex, rightCount);
        }
        arrField.set(target, sliced);
    }

    private static void fillDexPathList(ClassLoader cl, Object element)
            throws NoSuchFieldException, IllegalAccessException {
        if (sPathListField == null) {
            sPathListField = getDeclaredField(DexClassLoader.class.getSuperclass(), "pathList");
        }
        Object pathList = sPathListField.get(cl);
        if (sDexElementsField == null) {
            sDexElementsField = getDeclaredField(pathList.getClass(), "dexElements");
        }
        expandArray(pathList, sDexElementsField, new Object[]{element}, true);
    }

    public static void removeDexPathList(ClassLoader cl, int deleteIndex) {
        try {
            if (sPathListField == null) {
                sPathListField = getDeclaredField(DexClassLoader.class.getSuperclass(), "pathList");
            }
            Object pathList = sPathListField.get(cl);
            if (sDexElementsField == null) {
                sDexElementsField = getDeclaredField(pathList.getClass(), "dexElements");
            }
            sliceArray(pathList, sDexElementsField, deleteIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setResources(Application app, Resources resources) {
        setResources(app.getBaseContext(), resources);
        // Though we replace the application resources, while a new activity created,
        // it will be attached to a new context who's resources is got from somewhere cached.
        // So we need to update the cache to ensure `activity.mContext.mResources' correct.
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                Class cl = Class.forName("android.app.ContextImpl");
                Field field = cl.getDeclaredField("mResourcesManager");
                field.setAccessible(true);
                Object resManager = field.get(app.getBaseContext());
                field = resManager.getClass().getDeclaredField("mActiveResources");
                field.setAccessible(true);
                Map map = (Map) field.get(resManager);
                Object k = map.keySet().iterator().next();
                WeakReference<Resources> wr = new WeakReference<Resources>(resources);
                map.put(k, wr);
            } else {
                Field field = getDeclaredField(app.getBaseContext().getClass(), "mPackageInfo");
                Object apk = getValue(field, app.getBaseContext());
                if (apk == null) return;
                field = getDeclaredField(apk.getClass(), "mResources");
                setValue(field, apk, resources);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    public static void setResources(Context context, Resources resources) {
        if (sContextImpl_mResources_field == null) {
            sContextImpl_mResources_field = getDeclaredField(
                    context.getClass(), "mResources");
            if (sContextImpl_mResources_field == null) return;
        }
        setValue(sContextImpl_mResources_field, context, resources);
    }

    public static AssetManager newAssetManager() {
        AssetManager assets;
        try {
            assets = AssetManager.class.newInstance();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
            return null;
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
            return null;
        }
        return assets;
    }

    public static Instrumentation.ActivityResult execStartActivityV21(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, android.os.Bundle options) {
        if (sInstrumentation_execStartActivityV21_method == null) {
            Class[] types = new Class[] {Context.class, IBinder.class, IBinder.class,
                    Activity.class, Intent.class, int.class, android.os.Bundle.class};
            sInstrumentation_execStartActivityV21_method = getMethod(Instrumentation.class,
                    "execStartActivity", types);
        }
        if (sInstrumentation_execStartActivityV21_method == null) return null;
        return invoke(sInstrumentation_execStartActivityV21_method, instrumentation,
                who, contextThread, token, target, intent, requestCode, options);
    }

    public static Instrumentation.ActivityResult execStartActivityV20(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode) {
        if (sInstrumentation_execStartActivityV20_method == null) {
            Class[] types = new Class[] {Context.class, IBinder.class, IBinder.class,
                    Activity.class, Intent.class, int.class};
            sInstrumentation_execStartActivityV20_method = getMethod(Instrumentation.class,
                    "execStartActivity", types);
        }
        if (sInstrumentation_execStartActivityV20_method == null) return null;
        return invoke(sInstrumentation_execStartActivityV20_method, instrumentation,
                who, contextThread, token, target, intent, requestCode);
    }

    public static Intent getIntent(Object/*ActivityClientRecord*/ r) {
        if (sActivityClientRecord_intent_field == null) {
            sActivityClientRecord_intent_field = getDeclaredField(r.getClass(), "intent");
        }
        return getValue(sActivityClientRecord_intent_field, r);
    }

    public static void setActivityInfo(Object/*ActivityClientRecord*/ r, ActivityInfo ai) {
        if (sActivityClientRecord_activityInfo_field == null) {
            sActivityClientRecord_activityInfo_field = getDeclaredField(
                    r.getClass(), "activityInfo");
        }
        setValue(sActivityClientRecord_activityInfo_field, r, ai);
    }

    //______________________________________________________________________________________________
    // Private

    private static Method getMethod(Class cls, String methodName, Class[] types) {
        try {
            Method method = cls.getMethod(methodName, types);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field getDeclaredField(Class cls, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static <T> T invoke(Method method, Object target, Object... args) {
        try {
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            // Ignored
            e.printStackTrace();
            return null;
        }
    }

    private static <T> T getValue(Field field, Object target) {
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void setValue(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            // Ignored
            e.printStackTrace();
        }
    }
}
