package com.armine.sftpmanager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.armine.sftpmanager.Tasks.ConnectTask;
import com.armine.sftpmanager.Tasks.UploadTask;
import com.google.android.material.snackbar.Snackbar;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_VIDEO_CAPTURE = 2;
    static final String TAG = "MainActivity";

    Button btn_connect, btn_switch_folder, btn_take_photo, btn_capture_video;
    EditText eIP, ePort, eUser, ePassword;
    ProgressDialog mProgressDialog;
    String IP, port, user, password;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        eIP = findViewById(R.id.register_ip);
        ePort = findViewById(R.id.register_port);
        eUser = findViewById(R.id.register_user);
        ePassword = findViewById(R.id.register_password);

        btn_connect = findViewById(R.id.btn_connect);
        btn_switch_folder = findViewById(R.id.btn_switch_folder);
        btn_take_photo = findViewById(R.id.btn_take_photo);
        btn_capture_video = findViewById(R.id.btn_capture_video);

        // Permission check
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 84);
        }

        if(loadInfo()){
            eIP.setText(IP);
            ePort.setText(port);
            eUser.setText(user);
            ePassword.setText(password);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveInfo();
    }

    ConnectTask connectTask;
    Session session;
    Boolean connectFlag = false;
    /**
     * Called when the user taps the ** Connect ** or ** Logout ** button
     */
    public void saveConnection(View view) {
            if(!connectFlag)
                sessionConnect(view);
            else
                sessionLogout();
    }

    /**
     * Called when the user taps the ** Folder ** button, open the folder on sever
     */
    public void switchFolder(View view){
        if (session != null) {
            Intent FolderIntent = new Intent(MainActivity.this, FolderActivity.class);

            Globals.session = session;
            connectTask.cancel(true);
            Log.d(TAG, "launching Folder activity");
            MainActivity.this.startActivity(FolderIntent);
        }
        else
        {
            Snackbar.make(view, "Please ensure connection is successful!", Snackbar.LENGTH_LONG).show();
        }
    }


    private void sessionConnect(View view){
        IP = eIP.getText().toString();
        port = ePort.getText().toString();
        user = eUser.getText().toString();
        password = ePassword.getText().toString();
        Globals.currentPath = "/home/" + user;
        // Check whether necessary fields are filled
        if (!user.equals("") && !IP.equals("") && !port.equals("")) {
            connectTask = new ConnectTask(MainActivity.this);

            // Connect with password
            if (!password.equals("")) {
                connectTask.execute(user, IP, port, String.valueOf(false), String.valueOf(false), password, "");
            } else {
                Snackbar.make(view, "Please input a password", Snackbar.LENGTH_LONG).show();
                return;
            }

            // Check whether connect are established
            try {
                session = connectTask.get();
                if (session == null) {
                    Snackbar.make(view, "Could not connect", Snackbar.LENGTH_LONG).show();
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

            // After connection is successful
            Snackbar.make(view, "Connect successfully!", Snackbar.LENGTH_SHORT).show();

            btn_connect.setText(R.string.btn_logout);
            btn_switch_folder.setVisibility(View.VISIBLE);
            btn_take_photo.setVisibility(View.VISIBLE);
            btn_capture_video.setVisibility(View.VISIBLE);

            connectFlag = true;
        } else {
            Snackbar.make(view, "Check input fields", Snackbar.LENGTH_LONG).show();
        }
    }

    private void sessionLogout(){
        Globals.channel.disconnect();
        Globals.session.disconnect();
        // After disconnection
        btn_connect.setText(R.string.btn_connect);
        btn_switch_folder.setVisibility(View.INVISIBLE);
        btn_take_photo.setVisibility(View.INVISIBLE);
        btn_capture_video.setVisibility(View.INVISIBLE);

        connectFlag = false;
    }

    final String noInitResourceStr = "Please click 'Folder' to initialize resource!";
    /**
     * Called when the user taps the ** Camera ** button, open the folder on sever
     */
    public void openCamera(View view){
        if(Globals.channel == null){
            Log.e(TAG, noInitResourceStr);
            Snackbar.make(view, noInitResourceStr, Snackbar.LENGTH_LONG).show();
        }else {
            if(view.getId() == R.id.btn_take_photo)
                takePhoto();
            else if(view.getId() == R.id.btn_capture_video)
                captureVideo();
        }
    }

    Uri photoURI;
    private void takePhoto(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.armine.sftpmanager.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
//                takePictureIntent.putExtra("android.intent.extra.quickCapture", true);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void captureVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    String currentPhotoPath;
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_IMAGE_CAPTURE) {
            if(resultCode == RESULT_OK){
                Uri uri_temp = photoURI;
                // Upload and revoke openCamera again
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        uploadFile(uri_temp);
                    }
                }, 500);
                takePhoto();
            }
            else{
            // Delete temp empty image
            File fdelete = new File(currentPhotoPath);
                if (fdelete.exists()) {
                    if (fdelete.delete()) {
                        Log.d(TAG, "Empty image delete successfully!");
                    } else {
                        Log.d(TAG, "Empty image delete unsuccessfully!");
                    }
                }
                else{
                    Log.d(TAG, "Invalid path!");
                }
            }
        }
        else if(requestCode == REQUEST_VIDEO_CAPTURE){
            if(resultCode == RESULT_OK){
                Uri videoUri = data.getData();
                uploadFile(videoUri);
            }
        }
    }

    byte[] fileUpBytes;
    private void uploadFile(Uri uri){
        Globals.fileUpName = FolderActivity.getFileName(uri);

        Log.d("UPLOAD", "got: " + uri);
        Log.d("UPLOAD", "filename: " + Globals.fileUpName);

        try {
            fileUpBytes = FolderActivity.readBytes(getContentResolver().openInputStream(uri));
            Globals.fileUpBytes = fileUpBytes;

            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setMessage("Uploading: " + uri.getLastPathSegment());
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            Globals.mProgressDialogUpload = mProgressDialog;

            UploadTask uploadTask = new UploadTask(MainActivity.this);
            uploadTask.execute(Globals.currentPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean loadInfo(){
        try(FileInputStream fileInput = openFileInput("data");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInput))){
            for(int i = 0; i < 4; i ++){
                switch (i){
                    case 0:
                        IP = reader.readLine();
                        break;
                    case 1:
                        port = reader.readLine();
                        break;
                    case 2:
                        user = reader.readLine();
                        break;
                    case 3:
                        password = reader.readLine();
                        break;
                }
            }
            return true;
        } catch (FileNotFoundException e){
            Toast.makeText(this, "No history exist", Toast.LENGTH_LONG).show();
        } catch(IOException e){
            e.printStackTrace();
        }
        return false;
    }

    private void saveInfo(){
        try (FileOutputStream fileOutput = openFileOutput("data", Context.MODE_PRIVATE);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fileOutput))) {

            writer.write(IP);
            writer.newLine();
            writer.write(port);
            writer.newLine();
            writer.write(user);
            writer.newLine();
            writer.write(password);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}