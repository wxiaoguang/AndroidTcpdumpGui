package com.voxlearning.androidtcpdumpgui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.voxlearning.androidtcpdumpgui.util.Io;
import com.voxlearning.androidtcpdumpgui.util.Str;

import java.io.File;

public class ViewActivity extends AppCompatActivity {

    static final private String IntentAction_OpenCapture = "ViewActivity.IntentAction.ViewCapture";
    static final private String IntentExtra_CaptureName = "ViewActivity.IntentExtra.CaptureName";

    private EditText mEditTextInfo;
    private EditText mEditTextComment;
    private String mCaptureName;
    private String mLastComment;

    static public void openCapture(Context context, String name) {
        Intent intent = new Intent(context, ViewActivity.class);
        intent.setAction(IntentAction_OpenCapture);
        intent.putExtra(IntentExtra_CaptureName, name);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);


        mEditTextInfo = (EditText)findViewById(R.id.editTextInfo);
        mEditTextComment = (EditText)findViewById(R.id.editTextComment);

        mEditTextInfo.setKeyListener(null);
        mEditTextInfo.setMovementMethod(new ScrollingMovementMethod());
        mEditTextInfo.setHorizontallyScrolling(true);

        Intent intent = getIntent();
        if(IntentAction_OpenCapture.equals(intent.getAction())) {
            mCaptureName = intent.getStringExtra(IntentExtra_CaptureName);
            setTitle("Capture:" + mCaptureName);
            loadCapture();
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_viewcapture, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete:
                doDelete();
                return true;

            case R.id.menu_share:
                doSaveCommentZipShare();
                return true;

            case android.R.id.home:
                doGoBack();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        doGoBack();
    }

    @SuppressLint("DefaultLocale")
    private void loadCapture() {
        String dir = CaptureFilePath.OutputDir(mCaptureName);

        long startTime = Str.toLong(Str.trim(Io.readAll(dir + "/" + CaptureFilePath.FileName_Start)));
        long stopTime = Str.toLong(Str.trim(Io.readAll(dir + "/" + CaptureFilePath.FileName_Stop)));
        long packetsSize = new File(dir + "/" + CaptureFilePath.FileName_Packets).length();
        String comment = Io.readAll(dir + "/" + CaptureFilePath.FileName_Comment);
        String tcpdumpOut = Io.readAll(dir + "/" + CaptureFilePath.FileName_TcpdumpOut);
        String logs = Io.readAll(dir + "/" + CaptureFilePath.FileName_Logs);

        mEditTextInfo.setText("");

        if(startTime != 0)
            mEditTextInfo.append("Start: " + DateFormat.format("yyyy-MM-dd HH-mm-ss(z)", new java.util.Date(startTime)).toString() + "\n");

        if(stopTime != 0)
            mEditTextInfo.append("Stop: " + DateFormat.format("yyyy-MM-dd HH-mm-ss(z)", new java.util.Date(stopTime)).toString() + "\n");

        if(startTime != 0 && stopTime != 0) {
            mEditTextInfo.append("Duration: " + String.format("%.2f", (stopTime - startTime) / 1000.0 / 60.0) + " minutes\n");
        }
        mEditTextInfo.append("Packets Size: " + String.format("%.2f", packetsSize/1024.0/1024.0) + "MB\n");

        mEditTextInfo.append("\n\n");
        mEditTextInfo.append("Tcpdump Output:\n");
        if(tcpdumpOut != null)
            mEditTextInfo.append(tcpdumpOut);

        mEditTextInfo.append("\n\n");
        mEditTextInfo.append("Logs:\n");
        if(logs != null)
            mEditTextInfo.append(logs);

        mLastComment = Str.def(comment);
        mEditTextComment.setText(mLastComment);
    }

    private void doDelete() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dir = CaptureFilePath.OutputDir(mCaptureName);
                Io.deleteFollowSymlink(dir);
                dialog.dismiss();
                finish();
            }
        });
        b.setMessage("Do you want to delete " + mCaptureName + "?");
        b.show();
    }

    private void saveComment() {
        String comment = mEditTextComment.getText().toString();
        saveComment(comment);
    }

    private void saveComment(String comment) {
        if(!Str.eq(comment, mLastComment)) {
            String dir = CaptureFilePath.OutputDir(mCaptureName);
            Io.writeFile(dir + "/" + CaptureFilePath.FileName_Comment, comment);
            Io.writeFileAppend(dir + "/" + CaptureFilePath.FileName_Logs, CaptureManager.nowLogString() + " comment:" + comment + "\n");
            mLastComment = comment;
        }
    }

    private void doGoBack() {
        final String comment = mEditTextComment.getText().toString();
        if(Str.isEmpty(mLastComment)) {
            saveComment(comment);
            finish();
            return;
        }

        if(Str.eq(mLastComment, comment)) {
            finish();
            return;
        }


        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        b.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveComment(comment);
                dialog.dismiss();
                finish();
            }
        });
        b.setMessage("Comment changed, do you want to to save?");
        b.show();
    }


    private void doSaveCommentZipShare() {

        saveComment();

        String dirCapture = CaptureFilePath.OutputDir(mCaptureName);
        String []files = new String[] {
                dirCapture + "/" + CaptureFilePath.FileName_Start,
                dirCapture + "/" + CaptureFilePath.FileName_Stop,
                dirCapture + "/" + CaptureFilePath.FileName_Apps,
                dirCapture + "/" + CaptureFilePath.FileName_Comment,
                dirCapture + "/" + CaptureFilePath.FileName_Logs,
                dirCapture + "/" + CaptureFilePath.FileName_Packets,
                dirCapture + "/" + CaptureFilePath.FileName_TcpdumpOut,
        };


        String dirTmpShare = CaptureFilePath.BaseDir() + "/tmp/share";
        Io.deleteFollowSymlink(dirTmpShare);
        new File(dirTmpShare).mkdirs();

        String tmpZipFilePath = dirTmpShare + "/" + mCaptureName + ".zip";
        if(!Io.zip(files, tmpZipFilePath)) {
            Toast.makeText(this, "Failed to zip files", Toast.LENGTH_LONG).show();
            return;
        }


        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        intentShareFile.setType("application/zip");
        intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + tmpZipFilePath));
        intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Tcpdump:" + mCaptureName);
        intentShareFile.putExtra(Intent.EXTRA_TEXT, mLastComment);
        startActivity(Intent.createChooser(intentShareFile, "Captured:" + mCaptureName));
    }
}
