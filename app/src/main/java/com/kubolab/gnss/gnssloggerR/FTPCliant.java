package com.kubolab.gnss.gnssloggerR;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;

public class FTPCliant {

    private Context myContext;
    private ProgressDialog myProgressDialog;
    private FTPClient myFTPClient;

    public FTPCliant(Context context) {
        myContext = context;
    }

    // 非同期処理開始
    protected void onPreExecute() {
        myProgressDialog = new ProgressDialog(myContext);
        myProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        myProgressDialog.setTitle("Navigation File Download");
        myProgressDialog.setMessage("Downloading...");
        myProgressDialog.show();
    }

    // 非同期処理 n
    protected String doInBackground(String... params) {
        String remoteserver = "198.118.242.40";                 //FTPサーバーアドレス
        int remoteport = 46127;    //FTPサーバーポート
        String remotefile = params[2];                   //サーバーフォルダ
        String userid = "";                       //ログインユーザID
        String passwd = "";                       //ログインパスワード
        boolean passive = Boolean.valueOf(params[5]);    //パッシブモード使用

        //ＦＴＰファイル受信
        FTP ftp = new FTP(myContext);
        String result = ftp.receiveData(remoteserver, remoteport, userid, passwd, passive, remotefile);
        ftp = null;

        return result;
    }

    // 非同期処理終了
    protected void onPostExecute(String result) {
        myProgressDialog.dismiss();
        if (result == null) {
            Toast.makeText(myContext, "Download Complete", Toast.LENGTH_SHORT ).show();
        } else {
            Toast.makeText(myContext, "Download Error", Toast.LENGTH_SHORT ).show();
        }
    }

    // インナークラス　ＦＴＰクライアント commons net使用
    private class FTP extends ContextWrapper {
        public FTP(Context base) {
            super(base);
        }
        private String receiveData (String remoteserver, int remoteport,
                                String userid, String passwd, boolean passive, String remotefile) {
            int reply = 0;
            boolean isLogin = false;
            myFTPClient = new FTPClient();

            try {
                myFTPClient.setConnectTimeout(5000);
                //接続
                myFTPClient.connect(remoteserver, remoteport);
                reply = myFTPClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    throw new Exception("Connect Status:" + String.valueOf(reply));
                }
                //ログイン
                if (!myFTPClient.login(userid, passwd)) {
                    throw new Exception("Invalid user/password");
                }
                isLogin = true;
                //転送モード
                if (passive) {
                    myFTPClient.enterLocalPassiveMode(); //パッシブモード
                } else {
                    myFTPClient.enterLocalActiveMode();  //アクティブモード
                }
                //ファイル送信
                myFTPClient.setDataTimeout(15000);
                myFTPClient.setSoTimeout(15000);
                FileInputStream fileInputStream = this.openFileInput("送信ファイル名");
                myFTPClient.storeFile(remotefile, fileInputStream);
                reply = myFTPClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    throw new Exception("Send Status:" + String.valueOf(reply));
                }
                fileInputStream.close();
                fileInputStream = null;
                //ログアウト
                myFTPClient.logout();
                isLogin = false;
                //切断
                myFTPClient.disconnect();
            } catch(Exception e) {
                return e.getMessage();
            } finally {
                if (isLogin) {
                    try {myFTPClient.logout();} catch (IOException e) {}
                }
                if (myFTPClient.isConnected()) {
                    try {myFTPClient.disconnect();} catch (IOException e) {}
                }
                myFTPClient = null;
            }
            return null;
        }
    }

}
