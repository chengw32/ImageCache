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


        int size = 100;

//        for (int i = 0; i < size; i++) {
//
//
//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            if (i >= size - 1 ) i = 0;
//            CacheUtils.getIns().getBitmap(String.valueOf(i));
//
//        }



    }

    int i = 1;
    public void xxxxxx(View v){
        if (i >= 3)i--;
        else
        i++;
        CacheUtils.getIns().getBitmap(String.valueOf(i));
    }

}
