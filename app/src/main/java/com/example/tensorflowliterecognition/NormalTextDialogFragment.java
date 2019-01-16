package com.example.tensorflowliterecognition;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Created by Ryan on 2018/3/16.
 */

public class NormalTextDialogFragment extends DialogFragment {


    public interface OnClickCallBack {
        void onPositiveButton();

        void onNegativeButton();
    }

    private OnClickCallBack onClickCallBack;


    public void setOnClickCallBack(OnClickCallBack onClickCallBack) {
        this.onClickCallBack = onClickCallBack;
    }

    /**
     * 创建Dialog时调用
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // 创建构造器
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // 设置参数
        builder.setMessage("是否需要裁剪图片")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onClickCallBack != null) {
                            onClickCallBack.onPositiveButton();
                        }
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onClickCallBack != null) {
                            onClickCallBack.onNegativeButton();
                        }
                    }
                });
        // 创建对话框并返回.
        return builder.create();
    }

}