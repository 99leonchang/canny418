package com.example.canny418.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.canny418.R;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;

    private static final int RESULT_LOAD_IMAGE = 1;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView textView = root.findViewById(R.id.text_home);
        homeViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        final Button button = root.findViewById(R.id.button_load_picture);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                Log.d("CANNY", "Clicked");
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, RESULT_LOAD_IMAGE);
            }
        });


        return root;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(getActivity()) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("CANNY", "OpenCV loaded successfully");
//                    mOpenCvCameraView.enableView();
//                    mOpenCvCameraView.setOnTouchListener(BlobActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private CannyResult detectEdges(Bitmap bitmap, int threshold1, int threshold2, int iter) {
        long prevTime = System.currentTimeMillis();
        Bitmap ret = null;
        for(int i = 0; i < iter; i++) {
            // Convert bitmap to matrix
            Mat rgba = new Mat();
            Utils.bitmapToMat(bitmap, rgba);

            Mat edges = new Mat(rgba.size(), CvType.CV_8UC1);
            Imgproc.cvtColor(rgba, edges, Imgproc.COLOR_RGB2GRAY, 4);
            Imgproc.Canny(edges, edges, threshold1, threshold2);

            ret = bitmap.copy(bitmap.getConfig(), true);
            Utils.matToBitmap(edges, ret);
        }
        long currTime = System.currentTimeMillis();

        CannyResult cannyResult = new CannyResult();
        cannyResult.bitmap = ret;
        cannyResult.avgMilliSeconds = (currTime - prevTime)/iter;
        cannyResult.iter = iter;

        return cannyResult;
    }


    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("CANNY", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, getActivity(), mLoaderCallback);
        } else {
            Log.d("CANNY", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("CANNY", "Callback");
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {
            Log.d("CANNY", "Resulted");
            Uri selectedImage = data.getData();
            Log.d("CANNY", "Image" + selectedImage);
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getActivity().getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            assert cursor != null;
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            Log.d("CANNY", "ImagePath " + picturePath);

            ImageView img1 = (ImageView) getActivity().findViewById(R.id.img1);
            ImageView img2 = (ImageView) getActivity().findViewById(R.id.img2);
            InputStream stream = null;
            try {
                stream = getActivity().getContentResolver().openInputStream(selectedImage);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            EditText t1 = (EditText) getActivity().findViewById(R.id.threshOne);
            EditText t2 = (EditText) getActivity().findViewById(R.id.threshTwo);
            EditText t3 = (EditText) getActivity().findViewById(R.id.iterations);

            int threshold1 = Integer.parseInt(t1.getText().toString());
            int threshold2 = Integer.parseInt(t2.getText().toString());
            int iter = Integer.parseInt(t3.getText().toString());
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            img1.setImageBitmap(bitmap);

            // canny edge detection
            CannyResult cannyResult = detectEdges(bitmap, threshold1, threshold2, iter);

            TextView result = (TextView) getActivity().findViewById(R.id.avgMilliSeconds);
            result.setText("" + cannyResult.avgMilliSeconds);

            img2.setImageBitmap(cannyResult.bitmap);
//            imageView.setImageURI(selectedImage);
//            imageView.setImageDrawable(BitmapFactory.decodeFile(picturePath));

        }


    }
}
