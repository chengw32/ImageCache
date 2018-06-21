package cgw.com.imagecache;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
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
    private BitmapFactory.Options options = new BitmapFactory.Options();

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
        if (null == memoryCache)
            getDiskCache();

    }


    private Set secondCachePool;
    private ReferenceQueue recycleQueue;

    /**
     * Author 陈
     * Time 2018/6/15 17:21
     * Des 添加到二级缓存
     */
    private void addSecondCache(Bitmap bitmap) {
        if (null == secondCachePool)
            secondCachePool = Collections.synchronizedSet(new HashSet<WeakReference<Bitmap>>());
        secondCachePool.add(new WeakReference<Bitmap>(bitmap, getQueue()));
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

    private Bitmap getMemoryCache(String url) {
        ActivityManager manager = (ActivityManager) MyApp.getIns().getSystemService(ACTIVITY_SERVICE);
        // 系统分配给应用的最大空间
        //单位是 M
        int memoryClass = manager.getMemoryClass();
        //设置缓存为最大空间的 1/8 转换成字节
        if (null == memoryCache) {
            memoryCache = new LruCache<String, Bitmap>(memoryClass / 8 * 1024 * 1024) {

                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {

                    //加入二级缓存
                    addSecondCache(oldValue);
                }
            };
        }
        return memoryCache.get(url);
    }


    public Bitmap getDiskCache(String url) {

        if (null == diskLruCache){
            try {
                diskLruCache = DiskLruCache.open(new File(dir),
                        BuildConfig.VERSION_CODE,1,10*1024*1024);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        DiskLruCache.Snapshot snapshot = null ;
        try {
            snapshot = diskLruCache.get(url);
            if (null == snapshot)return null ;
            //获得文件输入流 读取bitmap

            //为了能够被复用内存
            options.inMutable = true;
            InputStream is = snapshot.getInputStream(0);
            Bitmap bitmap = BitmapFactory.decodeStream(is,null,options);
            options.inBitmap = bitmap;
            //放入内存缓存
            if (null != bitmap)memoryCache.put(url,bitmap);
            return bitmap ;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot){
                snapshot.close();
            }
        }

        return null;
    }
}
