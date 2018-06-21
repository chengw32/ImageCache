package cgw.com.imagecache;

import android.app.Application;

/**
 * Author chen_gw
 * Date 2018/6/19 14:26
 * DES :
 */
public class MyApp extends Application {

    private static MyApp ins;

    @Override
    public void onCreate() {
        super.onCreate();
       ins = this ;
    }

    public static MyApp getIns() {
        return ins;
    }
}
