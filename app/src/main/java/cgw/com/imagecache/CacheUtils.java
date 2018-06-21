package cgw.com.imagecache;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;
import android.widget.TabHost;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import cgw.com.diskUtils.DiskLruCache;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Author chen_gw
 * Date 2018/6/15 9:21
 * DES :
 */
public class CacheUtils {
    private static CacheUtils ins;
    private DiskLruCache diskLruCache;


    public static CacheUtils getIns() {
        if (null == ins) {
            synchronized (CacheUtils.class) {
                if (null == ins) ins = new CacheUtils();
            }
        }
        return ins;
    }


    public void getBitmap(String url) {

        Bitmap memoryCache = getMemoryCache(url);
        if (null == memoryCache) {
            Bitmap diskCache = getDiskCache(url);
            if (null == diskCache) {

                getFromNet(url);
            } else {
                Log.e("-----", "从硬盘获取");
            }
        } else {
            Log.e("-----", "从内存获取");
        }

    }


    private Set<WeakReference<Bitmap>> secondCachePool;
    private ReferenceQueue recycleQueue;

    /**
     * Author 陈
     * Time 2018/6/15 17:21
     * Des 添加到二级缓存
     */
    private void addSecondCache(Bitmap bitmap) {
        initSecondCacheIns();
        secondCachePool.add(new WeakReference<Bitmap>(bitmap, getQueue()));
    }

    private void initSecondCacheIns() {
        if (null == secondCachePool)
            secondCachePool = Collections.synchronizedSet(new HashSet<WeakReference<Bitmap>>());
    }


    Thread clearQueueThread;

    private ReferenceQueue getQueue() {
        if (null == recycleQueue) {
            recycleQueue = new ReferenceQueue();
            clearQueueThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {

                        //通过 poll
//                            Reference<Bitmap> poll = recycleQueue.poll();
//                            Bitmap bitmap = poll.get();
//                            if (null != bitmap && !bitmap.isRecycled())bitmap.recycle();
//

                        //通过 remove
                        try {
                            Reference<Bitmap> reference = recycleQueue.remove();
                            Bitmap bitmap = reference.get();
                            if (null != bitmap && !bitmap.isRecycled()) {
                                bitmap.recycle();
                                Log.e("cache", "被回收了");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }
            });
            clearQueueThread.start();
        }
        return recycleQueue;
    }

    LruCache<String, Bitmap> memoryCache;

    /**
     * Author 陈
     * Time 2018/6/21 15:58
     * Des 放入二级缓存
     */
    private void putToMemoryCache(String url, Bitmap bitmap) {
       initMemoryCache();
        memoryCache.put(url, bitmap);
    }

    private void initMemoryCache(){
        if (null == memoryCache) {
            ActivityManager manager = (ActivityManager) MyApp.getIns().getSystemService(ACTIVITY_SERVICE);
            // 系统分配给应用的最大空间
            //单位是 M
            int memoryClass = manager.getMemoryClass();
            //设置缓存为最大空间的 1/8 转换成字节
            memoryCache = new LruCache<String, Bitmap>(memoryClass / 8 * 1024 * 1024) {

                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {

                    //加入二级缓存
                    addSecondCache(oldValue);
                }
            };
        }
    }

    private Bitmap getMemoryCache(String url) {
        //从内存取 bitmap
        initMemoryCache();
        return memoryCache.get(url);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public File getExternalCacheDir(Context context) {
        if (Utils.hasFroyo()) {
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public boolean isExternalStorageRemovable() {
        if (Utils.hasGingerbread()) {
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    /**
     * Author 陈
     * Time 2018/6/21 17:10
     * Des 存储位置
     */
    public File getDiskCacheDir() {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(MyApp.getIns()).getPath() :
                        MyApp.getIns().getCacheDir().getPath();

        return new File(cachePath + File.separator + "chen");
    }

    private void initDiskCache(){
        if (null == diskLruCache) {
            try {
                diskLruCache = DiskLruCache.open(getDiskCacheDir(),
                        BuildConfig.VERSION_CODE, 1, 10 * 1024 * 1024);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * Author 陈
     * Time 2018/6/21 17:50
     * Des 硬盘缓存
     */
    public void putToDiskCache(String url, Bitmap bitmap) {

        initDiskCache();
        try {
            DiskLruCache.Snapshot snapshot = diskLruCache.get(url);
            if (null == snapshot) {

                DiskLruCache.Editor edit = diskLruCache.edit(url);
                if (null != edit) {
                    //deit 打开输出流
                    OutputStream outputStream = edit.newOutputStream(0);
                    // 将 bitmap compress压缩 然后通过输出流 写到 edit
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    //edit 提交缓存
                    edit.commit();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public Bitmap getDiskCache(String url) {


        initDiskCache();
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = diskLruCache.get(url);
            if (null == snapshot) return null;
            //获得文件输入流 读取bitmap
            InputStream is = snapshot.getInputStream(0);


            BitmapFactory.Options options = new BitmapFactory.Options();

            //先解析图片信息，判断是否可以用复用二级缓存里的内存空间
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            addInBitmapOptions(options);

            Log.e("-----","inBitmap是否为空  "+ options.inBitmap);
            // 真正的解析图片  如果 inBitmap 里有 对象 在 decodeStrieam 的时候就会去复用 而不用重新分配内存
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            //放入内存缓存
            if (null != bitmap) memoryCache.put(url, bitmap);
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot) {
                snapshot.close();
            }
        }

        return null;
    }


    /**
     * Author 陈
     * Time 2018/6/21 16:33
     * Des 判断 二级缓存里是否有可复用的内存空间 然后赋值给 inBitmap
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void addInBitmapOptions(BitmapFactory.Options options) {
        //BEGIN_INCLUDE(add_bitmap_options)
        // inBitmap only works with mutable bitmaps so force the decoder to
        // return mutable bitmaps.
        options.inMutable = false;
        options.inSampleSize = 1;


        initSecondCacheIns();
        Iterator<WeakReference<Bitmap>> iterator = secondCachePool.iterator();
        synchronized (secondCachePool) {

            while (iterator.hasNext()) {
                Bitmap bitmap = iterator.next().get();
                if (null != bitmap && bitmap.isMutable()) {

                    if (canUseForInBitmap(bitmap, options)) {
                        //内存可以复用 所以将 bitmap 赋值到 inBitmap
                        options.inBitmap = bitmap;
                        //然后从 容器中移除
                        iterator.remove();
                    }
                } else {
                    //如果二级缓存里的 bitmap 为空或者不可复用 则直接移除掉
                    iterator.remove();
                }
            }
        }


    }


    /**
     * Author 陈
     * Time 2018/6/21 16:35
     * Des 判断 是否能用 inBitmap 属性
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean canUseForInBitmap(
            Bitmap candidate, BitmapFactory.Options targetOptions) {
        //BEGIN_INCLUDE(can_use_for_inbitmap)

        // TODO: 2018/6/21  计算出 inSampleSize

        if (!Utils.hasKitKat()) {
            // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
            return candidate.getWidth() == targetOptions.outWidth
                    && candidate.getHeight() == targetOptions.outHeight
                    && targetOptions.inSampleSize == 1;
        }

        // From Android 4.4 (KitKat) onward we can re-use if the byte size of the new bitmap
        // is smaller than the reusable bitmap candidate allocation byte count.
        int width = targetOptions.outWidth / targetOptions.inSampleSize;
        int height = targetOptions.outHeight / targetOptions.inSampleSize;
        int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
        return byteCount <= candidate.getAllocationByteCount();
    }

    /**
     * Author 陈
     * Time 2018/6/21 16:36
     * Des 判断 图片编码格式
     */
    private static int getBytesPerPixel(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        } else if (config == Bitmap.Config.RGB_565) {
            return 2;
        } else if (config == Bitmap.Config.ARGB_4444) {
            return 2;
        } else if (config == Bitmap.Config.ALPHA_8) {
            return 1;
        }
        return 1;
    }

    /**
     * Author 陈
     * Time 2018/6/21 17:19
     * Des 这里假设是从网络获取的
     */
    public Bitmap getFromNet(String url) {

        Bitmap bitmap = BitmapFactory.decodeResource(MyApp.getIns().getResources(), R.mipmap.banner);
        if (null != bitmap) {

            Log.e("-----", "从网络获取");
            CacheUtils.getIns().putToMemoryCache(url, bitmap);
            CacheUtils.getIns().putToDiskCache(url, bitmap);
            return bitmap;
        }

        return null;
    }
}
