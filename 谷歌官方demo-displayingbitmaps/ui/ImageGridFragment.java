package cgw.com.displayingbitmaps.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.android.displayingbitmaps.R;
import com.example.android.displayingbitmaps.provider.Images;
import com.example.android.displayingbitmaps.util.ImageCache;
import com.example.android.displayingbitmaps.util.ImageResizer;

import java.util.List;

public class ImageGridFragment extends Fragment implements AdapterView.OnItemClickListener{

    private static final boolean D = true;
    private static final String TAG = "display";
    private static final String IMAGE_CACHE_DIR = "thumbs";

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private ImageAdapter mAdapter;
    private ImageResizer mImageResizer;
    private int mImageThumbSize;
    private int mImageThumbSpacing;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ImageGridFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ImageGridFragment newInstance(String param1, String param2) {
        ImageGridFragment fragment = new ImageGridFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public ImageGridFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.d(TAG,"ImageGridFragment onCreate");
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        ImageCache.ImageCacheParams cacheParams =
                new ImageCache.ImageCacheParams(getActivity(), IMAGE_CACHE_DIR);
        cacheParams.setMemCacheSizePercent(0.25f);// Set memory cache to 25% of app memory

        mImageResizer = new ImageResizer(getActivity(),100) {};
        mImageResizer.setLoadingImage(R.drawable.empty_photo);
        mImageResizer.addImageCache(getActivity().getSupportFragmentManager(), cacheParams);

        mAdapter = new ImageAdapter(getActivity());
        mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
        mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_cache:
                mImageResizer.clearCache();
                Toast.makeText(getActivity(), R.string.clear_cache_complete_toast,
                        Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(D) Log.d(TAG,"ImageGridFragment onCreateView");
        final View view = inflater.inflate(R.layout.image_grid_fragment, container, false);
        final GridView mGridView = (GridView)view.findViewById(R.id.gridview);
        mGridView.setAdapter(mAdapter);
        mGridView.setOnItemClickListener(this);
        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                // Pause fetcher to ensure smoother scrolling when flinging
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    // Before Honeycomb pause image loading on scroll to help with performance
                    mImageResizer.setPauseWork(true);
                } else {
                    mImageResizer.setPauseWork(false);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });

        // This listener is used to get the final width of the GridView and then calculate the
        // number of columns and the width of each column. The width of each column is variable
        // as the GridView has stretchMode=columnWidth. The column width is used to set the height
        // of each view so we get nice square thumbnails.
        mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
                    @Override
                    public void onGlobalLayout() {
                        if (mAdapter.getNumColumns() == 0) {
                            final int numColumns = (int) Math.floor(
                                    mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
                            if (numColumns > 0) {
                                final int columnWidth =
                                        (mGridView.getWidth() / numColumns) - mImageThumbSpacing;
                                if(D) Log.d(TAG,"columnWidth = "+columnWidth
                                        +"\nmImamgeThumbSize = "+mImageThumbSize
                                        +"\nnumColumns = "+numColumns);
                                mAdapter.setNumColumns(numColumns);
                                mAdapter.setItemHeight(columnWidth);
                                mGridView.getViewTreeObserver()
                                        .removeOnGlobalLayoutListener(this);
                            }
                        }
                    }
                });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(D) Log.d(TAG,"ImageGridFragment onResume");
        mImageResizer.setExitTasksEarly(false);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        if(D) Log.d(TAG,"ImageGridFragment onPause");
        mImageResizer.setPauseWork(false);
        mImageResizer.setExitTasksEarly(true);
        mImageResizer.flushCache();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(D) Log.d(TAG,"ImageGridFragment onDestroy");
        mImageResizer.closeCache();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Intent i = new Intent(getActivity(), ImageDetailActivity.class);
        i.putExtra(ImageDetailActivity.EXTRA_IMAGE, (int) id);
        startActivity(i);
    }

    /**
     * The main adapter that backs the GridView.
     */
    private class ImageAdapter extends BaseAdapter{

        private Context mContext;
        private int mItemHeight = 0;
        private int mNumColumns = 0;
        private GridView.LayoutParams mImageViewLayoutParams;
        private  List<String> mImagesList = null;
        private Images mImages = null;

        public ImageAdapter(Context context){
            mContext = context;
            mImageViewLayoutParams = new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT,GridView.LayoutParams.MATCH_PARENT);
            mImages = new Images(context);
            mImagesList =mImages.readImages(null);

        }

        @Override
        public int getCount() {
            return mImagesList!=null?mImagesList.size():0;
        }

        @Override
        public Object getItem(int position) {
            return mImagesList!=null?mImagesList.get(position):null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if(convertView == null){
                convertView = LayoutInflater.from(mContext).inflate(R.layout.image_grid_item, null);
                viewHolder = new ViewHolder();
                viewHolder.imageView = (ImageView)convertView.findViewById(R.id.mImageView);
                viewHolder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                viewHolder.imageView.setLayoutParams(mImageViewLayoutParams);
                convertView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // Check the height matches our calculated column width
            if(viewHolder.imageView.getLayoutParams().height!=mItemHeight){
                viewHolder.imageView.setLayoutParams(mImageViewLayoutParams);
            }
            mImageResizer.loadImage(mImagesList.get(position),viewHolder.imageView);
            return convertView;
        }

        class ViewHolder{
            public ImageView imageView;
        }

        /**
         * Sets the item height. Useful for when we know the column width so the height can be set
         * to match.
         *
         * @param height
         */
        public void setItemHeight(int height) {
            if (height == mItemHeight) {
                return;
            }
            mItemHeight = height;
            mImageViewLayoutParams =
                    new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, mItemHeight);
            mImageResizer.setImageSize(mItemHeight);
            notifyDataSetChanged();
        }

        public void setNumColumns(int numColumns) {
            mNumColumns = numColumns;
        }

        public int getNumColumns() {
            return mNumColumns;
        }
    }
}
