package cgw.com.imagecache;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;
import android.widget.TabHost;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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

        //内存中获取缓存
        Bitmap memoryCache = getMemoryCache(url);
        if (null == memoryCache) {
            //硬盘中获取缓存
            Bitmap diskCache = getDiskCache(url);
            if (null == diskCache) {

                //从网络或者sd卡获取源图片
                getFromNet(url);
            } else {
                Log.e("-----", "从硬盘缓存获取");
            }
        } else {
            Log.e("-----", "从内存缓存获取");
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
        Log.e("---", " 放入二级缓存");
        secondCachePool.add(new WeakReference(bitmap, getQueue()));
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
                        //通过 remove  因为 remove 是一个阻塞的 没有的话不会执行
                        try {
                            Reference<Bitmap> reference = recycleQueue.remove();
                            Bitmap bitmap = reference.get();
                            if (null != bitmap && !bitmap.isRecycled()) {
                                bitmap.recycle();
                                Log.e("---", "被回收了");
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
    public void putToMemoryCache(String url, Bitmap bitmap) {
        initMemoryCache();
        memoryCache.put(url, bitmap);
    }

    private void initMemoryCache() {
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

                @Override
                protected int sizeOf(String key, Bitmap value) {
                    final int bitmapSize = getBitmapSize(value) / 1024;
                    return bitmapSize == 0 ? 1 : bitmapSize;
                }
            };
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int getBitmapSize(Bitmap bitmap) {

        // From KitKat onward use getAllocationByteCount() as allocated bytes can potentially be
        // larger than bitmap byte count.
        if (Utils.hasKitKat()) {
            return bitmap.getAllocationByteCount();
        }

        if (Utils.hasHoneycombMR1()) {
            return bitmap.getByteCount();
        }

        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
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

        File file = new File(Environment.getExternalStorageDirectory() + "/imageCache");
        if (!file.exists()) file.mkdirs();
        return file;
    }

    private void initDiskCache() {
        if (null == diskLruCache) {
            try {
                diskLruCache = DiskLruCache.open(getDiskCacheDir(),
                        BuildConfig.VERSION_CODE, 1, 1000 * 1024 * 1024);
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
        DiskLruCache.Snapshot snapshot = null;
        OutputStream outputStream = null;
        try {
            snapshot = diskLruCache.get(url);
            if (null == snapshot) {

                DiskLruCache.Editor edit = diskLruCache.edit(url);
                if (null != edit) {
                    //deit 打开输出流
                    outputStream = edit.newOutputStream(0);
                    // 将 bitmap compress压缩 然后通过输出流 写到 edit
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    //edit 提交缓存
                    edit.commit();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot) {
                snapshot.close();
            }
            if (null != outputStream) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }

    public Bitmap getDiskCache(String url) {
        initDiskCache();
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = diskLruCache.get(url);

            if (null == snapshot) {
                return null;
            }

            //获得文件输入流 读取bitmap
            InputStream is = snapshot.getInputStream(0);


            BitmapFactory.Options options = new BitmapFactory.Options();
            //先解析图片信息，判断是否可以用复用二级缓存里的内存空间
            options.inJustDecodeBounds = true;
            if (null != is) {
                FileDescriptor fd = ((FileInputStream) is).getFD();
                BitmapFactory.decodeFileDescriptor(fd, null, options);
//                BitmapFactory.decodeStream(is,null,options);

            }
            if (Utils.hasHoneycomb()) {
               addInBitmapOptions(options);
            }

            // 真正的解析图片  如果 inBitmap 里有 对象 在 decodeStrieam 的时候就会去复用 而不用重新分配内存
            options.inJustDecodeBounds = false;

            if (is != null) {
                Log.e("-----", "从硬盘缓存获取 ----  复用 inbitmap ------ " + options.inBitmap);
                FileDescriptor fd = ((FileInputStream) is).getFD();
//                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);

                //放入内存缓存
                if (null != bitmap) {
                    Log.e("-----", "放入 内存缓存");
                    memoryCache.put(url, bitmap);
                }
                return bitmap;

            }
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
        options.inMutable = true;


        initSecondCacheIns();
        Iterator<WeakReference<Bitmap>> iterator = secondCachePool.iterator();
        synchronized (secondCachePool) {


            while (iterator.hasNext()) {
                Bitmap bitmap = iterator.next().get();


                if (null != bitmap && bitmap.isMutable()) {

                    //options 里面的宽高是要解码的图片  而 bitmap 对象里面的是被复用的图片 在 4.4 版本需要宽高一致 所以需要对比
                    options.inSampleSize = calculateInSampleSize(options,bitmap.getWidth(),bitmap.getHeight());

                    if (canUseForInBitmap(bitmap, options)) {
                        options.inBitmap = bitmap ;
                        //然后从 容器中移除
                        iterator.remove();
                    }
                } else {
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
    private boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions) {


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

        String path = "/storage/emulated/0/imagedir/" + url + ".png";
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inSampleSize = 1;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        if (null != bitmap) {

            CacheUtils.getIns().putToMemoryCache(url, bitmap);
            CacheUtils.getIns().putToDiskCache(url, bitmap);
            return bitmap;
        }

        return null;
    }



    public static int calculateInSampleSize(BitmapFactory.Options options,
                                            int reqWidth, int reqHeight) {
        // BEGIN_INCLUDE (calculate_sample_size)
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            /*long totalPixels = width * height / inSampleSize;

            // Anything more than 2x the requested pixels we'll sample down further
            final long totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels > totalReqPixelsCap) {
                inSampleSize *= 2;
                totalPixels /= 2;
            }*/
        }
        return inSampleSize;
        // END_INCLUDE (calculate_sample_size)
    }

}
