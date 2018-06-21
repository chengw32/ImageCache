package cgw.com.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.banner);

        int size = 100;

        for (int i = 0; i < size; i++) {

            Log.e("---",""+i);

            if (i >= 90) i = 0;
            CacheUtils.getIns().getBitmap(String.valueOf(i));

        }


    }


}
