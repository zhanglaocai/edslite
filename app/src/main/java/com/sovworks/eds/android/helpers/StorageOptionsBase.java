package com.sovworks.eds.android.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.fs.util.StringPathUtil;
import com.sovworks.eds.settings.Settings;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressLint("NewApi")
public abstract class StorageOptionsBase
{
    public static class StorageInfo
    {
        public String label, path;
        public boolean isExternal, isReadOnly;
    }

    public static synchronized List<StorageInfo> getStoragesList(Context context)
    {
        if(_storagesList == null)
            loadStorageList(context);
        return _storagesList;
    }

    public static void loadStorageList(Context context)
    {
        StorageOptionsBase so = new StorageOptions(context);
        _storagesList = so.buildStoragesList();
    }

    public static StorageInfo getDefaultDeviceLocation(Context context)
    {
        for(StorageInfo si: getStoragesList(context))
            if(!si.isExternal)
                return si;
        return !getStoragesList(context).isEmpty() ? getStoragesList(context).get(0) : null;
    }

    private static List<StorageInfo> _storagesList;

    public StorageOptionsBase(Context context)
    {
        _context = context;
    }

    public final Context getContext()
    {
        return _context;
    }

    public  List<StorageInfo> buildStoragesList()
    {
        ArrayList<StorageInfo> res = new ArrayList<>();
        int extStoragesCounter = 1;
        StorageInfo si = getDefaultStorage();
        if(si!=null)
        {
            res.add(si);
            if(si.isExternal)
                extStoragesCounter++;
        }
        parseMountsFile(res, extStoragesCounter);
        return res;
    }

    protected static boolean isStorageAdded(Collection<StorageInfo> storages, String devPath, String mountPath)
    {
        StringPathUtil dpu = new StringPathUtil(devPath);
        StringPathUtil mpu = new StringPathUtil(mountPath);
        for(StorageInfo si: storages)
        {
            StringPathUtil spu = new StringPathUtil(si.path);
            if (spu.equals(mpu) || spu.equals(dpu))
                return true;
            if(((mountPath.startsWith("/mnt/media_rw/") && si.path.startsWith("/storage/")) ||
                    (si.path.startsWith("/mnt/media_rw/") && mountPath.startsWith("/storage/"))) &&
                    spu.getFileName().equals(mpu.getFileName()))
                return true;
        }
        return false;
    }

    protected StorageInfo getDefaultStorage()
    {
        String defPathState = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(defPathState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(defPathState))
        {
            StorageInfo info = new StorageInfo();
            if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD ||
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB && !Environment.isExternalStorageRemovable()) ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && (!Environment.isExternalStorageRemovable() || Environment.isExternalStorageEmulated()))
                    )
                info.label = _context.getString(R.string.built_in_memory_card);
            else
            {
                info.isExternal = true;
                info.label = _context.getString(R.string.external_storage) + " 1";
            }
            info.path = Environment.getExternalStorageDirectory().getPath();
            return info;
        }
        return null;
    }

    protected String readMountsFile()
    {
        try
        {
            return readMountsStd();
        }
        catch (IOException e)
        {
            Logger.log(e);
        }
        return "";
    }

    protected static String readMountsStd() throws IOException
    {
        Logger.debug("StorageOptions: trying to get mounts using std fs.");
        FileInputStream finp = new FileInputStream("/proc/mounts");
        InputStream inp = new BufferedInputStream(finp);
        try
        {
            return com.sovworks.eds.fs.util.Util.readFromFile(inp);
        }
        finally
        {
            inp.close();
        }
    }

    protected int parseMountsFile(Collection<StorageInfo> storages, int extCounter)
    {
        String mountsStr = readMountsFile();
        if (mountsStr.isEmpty())
            return extCounter;
        Settings settings = UserSettings.getSettings(_context);
        Pattern p = Pattern.compile("^([^\\s]+)\\s+([^\\s+]+)\\s+([^\\s+]+)\\s+([^\\s+]+).*?$", Pattern.MULTILINE);
        Matcher m = p.matcher(mountsStr);
        while (m.find())
        {
            String dev = m.group(1);
            String mountPath = m.group(2);
            String type = m.group(3);
            String[] flags = m.group(4).split(",");
            if (type.equals("vfat") || mountPath.startsWith("/mnt/") || mountPath.startsWith("/storage/"))
            {
                if (isStorageAdded(storages, dev, mountPath))
                    continue;
                if ((dev.startsWith("/dev/block/vold/") &&
                        (!mountPath.startsWith("/mnt/secure")
                                && !mountPath.startsWith("/mnt/asec")
                                && !mountPath.startsWith("/mnt/obb")
                                && !dev.startsWith("/dev/mapper")
                                && !type.equals("tmpfs"))
                        ) || (
                        (dev.startsWith("/dev/fuse") || dev.startsWith("/mnt/media")) && mountPath.startsWith("/storage/") && !mountPath.startsWith("/storage/emulated")
                        )
                   )
                {
                    StorageInfo si = new StorageInfo();
                    si.path = mountPath;
                    si.isExternal = true;
                    si.label = _context.getString(R.string.external_storage) + " " + extCounter;
                    si.isReadOnly = Arrays.asList(flags).contains("ro");
                    if(checkMountPoint(settings, si))
                    {
                        storages.add(si);
                        extCounter++;
                    }
                }
            }
        }
        return extCounter;
    }

    protected boolean checkMountPoint(Settings s, StorageOptionsBase.StorageInfo si)
    {
        File f = new File(si.path);
        return f.isDirectory() && !si.path.startsWith("/mnt/media_rw");
    }

    private final Context _context;

}
