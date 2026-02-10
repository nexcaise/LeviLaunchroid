package org.levimc.launcher.core.mods;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ModNativeLoader {
    private static final String TAG = "ModNativeLoader";
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void loadEnabledSoMods(ModManager modManager, File cacheDir) {
        List<Mod> mods = getEnabledMods(modManager);
        for (Mod mod : mods) {
            File src = new File(modManager.getCurrentVersion().modsDir, mod.getFileName());
            File dir = new File(cacheDir, "mods");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, mod.getFileName());
            try {
                copyFile(src, dst);
                System.load(dst.getAbsolutePath());
                Log.i(TAG, "Loaded so: " + dst.getName());
            } catch (IOException | UnsatisfiedLinkError e) {
                Log.e(TAG, "Can't load " + src.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public static void loadEnabledSoMods(ModManager modManager, File cacheDir, int index) {
        List<Mod> mods = getEnabledMods(modManager);
        for (Mod mod : mods) {
            File src = new File(modManager.getCurrentVersion().modsDir, mod.getFileName());
            File dir = new File(cacheDir, "mods");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, mod.getFileName());
            try {
                if(!dst.exists()) copyFile(src, dst);
                if(!nativeLoadMod(dst.getAbsolutePath(), index)) {
                    System.loadLibrary("newmodloading");
                    nativeLoadMod(dst.getAbsolutePath(), index);
                }
                Log.i(TAG, "Loaded so: " + dst.getName());
            } catch (IOException | UnsatisfiedLinkError e) {
                Log.e(TAG, "Can't load " + src.getName() + ": " + e.getMessage());
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
    
    private static List<Mod> getEnabledMods(ModManager modManager) {
        List<Mod> mods = new ArrayList<>();
        for (Mod mod : modManager.getMods()) {
            if (mod.isEnabled()) {
                mods.add(mod);
            }
        }
        return mods;
    }
    
    private static native boolean nativeLoadMod(String path, int index);
}
