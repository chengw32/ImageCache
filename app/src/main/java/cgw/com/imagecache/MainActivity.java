package cgw.com.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<String> mImagesList = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImagesList = new ArrayList<>();

        readImages() ;

        for (int i = 0; i < mImagesList.size(); i++) {


            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (i >= mImagesList.size() - 1 ) i = 0;
            CacheUtils.getIns().getBitmap(String.valueOf(i));
        }



    }


    public List<String> readImages(){
        if(mImagesList!=null){
            mImagesList.clear();
        }

        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try{
                File mDirector = new File(Environment.getExternalStorageDirectory() + "/imagedir");
                if(!mDirector.exists()){
                    mDirector.mkdirs();
                }
                File[] files = mDirector.listFiles();
                for(File file : files){
                    String mFileName = file.getName();
                    if(mFileName.endsWith(".jpg")||mFileName.endsWith(".png")||mFileName.endsWith(".bmp")||mFileName.endsWith(".jpeg")||mFileName.endsWith(".JPG")){
                        mImagesList.add(file.getAbsolutePath());
                    }
                }

                // Sort images by date time
//                sortImage(mImagesList);
                if(mImagesList.size()==0){	// no needed file
                    return null;
                }
                return mImagesList;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return mImagesList;
    }

}
