package com.example.shreyas.objecttracking;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class Serial_monitor extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{


    static boolean flag=false;
    static boolean connected=false;
    static {
        if (!(OpenCVLoader.initDebug())) {
            ;
        }
        else {
            flag=true;
        }
    }

    JavaCameraView cameraView;
    Mat mRgba;
    Bitmap bmp;
    int x,y;
    int points;
    int x_center,y_center;
    int cameraWidth=176,cameraHeight=144;


    Button btOpen;
    TextView tvRead;

    Physicaloid mPhysicaloid; // initialising library

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_monitor);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (JavaCameraView)findViewById(R.id.camera_view);
        if (flag) {
            cameraView.setCameraIndex(0);
            cameraView.setMaxFrameSize(cameraWidth,cameraHeight); //(1200,600)
            cameraView.setCvCameraViewListener(this);
            cameraView.enableView();
        }



        btOpen = (Button) findViewById(R.id.btOpen);
        tvRead = (TextView) findViewById(R.id.tvRead);
        setEnabledUi(false);
        mPhysicaloid = new Physicaloid(this);

    }

    public void onClickOpen(View v) {

        if (!connected) {
            // Opening
            mPhysicaloid.setBaudrate(9600);

            if (mPhysicaloid.open()) {
                setEnabledUi(true);

                mPhysicaloid.addReadListener(new ReadLisener() {
                    @Override
                    public void onRead(int size) {
                        byte[] buf = new byte[size];
                        mPhysicaloid.read(buf, size);
                        tvAppend(tvRead, Html.fromHtml("<font color=blue>" + new String(buf) + "</font>"));
                    }
                });
            } else {
                Toast.makeText(this, "Cannot open", Toast.LENGTH_LONG).show();
            }
        }
        else {
            // Closing
            if (mPhysicaloid.close()) {
                mPhysicaloid.clearReadListener();
                setEnabledUi(false);
            }
        }

    }

    public void sendData() {
        //String str = etWrite.getText().toString()+"\r\n";
        String str;
        if (x_center>cameraWidth*3/4)
            str="2\n"; // Right
        else if (x_center<cameraWidth/4)
            str="3\n"; // Left
        else if (y_center<cameraHeight/4)
            str="1\n"; // Forward
        else if (y_center>cameraHeight*3/4)
            str="4\n"; // Reverse
        else
            str="0\n"; // Stop

        if (str.length() > 0) {
            byte[] buf = str.getBytes();
            mPhysicaloid.write(buf, buf.length);
        }
    }

    private void setEnabledUi(boolean on) {
        connected = on;
        if (connected) {
            // connection is established
            // converting button to close mode
            btOpen.setEnabled(true);
            btOpen.setText("Close");
        }
        else {
            // connection not established
            // converting button to open mode
            btOpen.setEnabled(true);
            btOpen.setText("Open");
        }
    }

    Handler mHandler = new Handler();

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    public int dist(int x1,int y1,int z1,int x2,int y2,int z2) {
        int d = (x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)+(z2-z1)*(z2-z1);
        return d;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView.isEnabled()) {
            cameraView.disableView();
        }
    }
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        try {
            bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mRgba, bmp);
        }
        catch (CvException e){
            Log.d("Exception",e.getMessage());
        }

        int x = 0;
        int y = 0;

        int all_x = 0;
        int all_y = 0;

        int max = 100_000_000;
        while(x < cameraWidth) {

            while(y < cameraHeight){

                int pixel = bmp.getPixel(x, y);

                int redValue = Color.red(pixel);
                int blueValue = Color.blue(pixel);
                int greenValue = Color.green(pixel);

                int d = dist(redValue,greenValue,blueValue,255,0,0);
                if (d<max) {
                    max=d;
                    all_x=x;
                    all_y=y;
                }
                y++;
            }
            x++;
            y = 0;
        }

        y = 0;
        x = 0;

        if (dist(x_center,y_center,0,all_x,all_y,0)>25*25) {
            x_center = all_x;
            y_center = all_y;
            sendData();
        }

        return mRgba;
    }

}