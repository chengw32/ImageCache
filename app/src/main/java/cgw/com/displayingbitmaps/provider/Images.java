package cgw.com.displayingbitmaps.provider;

import android.content.Context;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by android on 11/19/15.
 */
public class Images {

    private static final boolean D = false;
    private static final String TAG = "display";
    private List<String> mImagesList = null;
    private static final String DCIM_Camera = "Camera";
    private Context mContext;
    public static int mImagesCount;

    public Images(Context context){
        mImagesList = new ArrayList<>();
        mContext = context;
    }

    public List<String> readImages(String mDirectorName){
        if(mImagesList!=null){
            mImagesList.clear();
        }

        if(D) Log.d(TAG, "Images ExternalStorageState = "+Environment.getExternalStorageState());
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try{
                File mDirector = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM),mDirectorName==null?DCIM_Camera:mDirectorName);
                if(D) Log.d(TAG, "Images mDirector = "+mDirector
                            + "\nmDirector exists? "+mDirector.exists());
                if(!mDirector.exists()){
                    mDirector.mkdirs();
                }
                File[] files = mDirector.listFiles();
                if(D) Log.d(TAG, "Images Images number = "+files.length);
                for(File file : files){
                    String mFileName = file.getName();
                    if(mFileName.endsWith(".jpg")||mFileName.endsWith(".bmp")||mFileName.endsWith(".jpeg")||mFileName.endsWith(".JPG")){
                        mImagesList.add(file.getAbsolutePath());
                    }
                }

                mImagesCount = mImagesList.size();
                // Sort images by date time
                sortImage(mImagesList);
                if(mImagesList.size()==0){	// no needed file
                    Toast.makeText(mContext, "File not found", Toast.LENGTH_LONG).show();
                    return null;
                }
                return mImagesList;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }

    public long getDateTime(String path) {
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            String dateTimeString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
            if (dateTimeString == null) return -1;

            ParsePosition pos = new ParsePosition(0);
            SimpleDateFormat sFormatter =  new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
            try {
                Date datetime = sFormatter.parse(dateTimeString, pos);
                if (datetime == null) return -1;
                return datetime.getTime();
            } catch (IllegalArgumentException ex) {
                return -1;
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return -1;
    }

    public void sortImage(List<String>listFilePath ){
        Collections.sort(listFilePath, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                // TODO Auto-generated method stub
                long time1 = getDateTime(lhs);
                long time2 = getDateTime(rhs);
                return time2<=time1 ? -1 : 1;

            }
        });
    }
}
