package com.ingchips.app;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link OtaAppOnlyFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OtaAppOnlyFragment extends Fragment {

    interface AppFileHander {
        public void onLocalAppFile(long appLoadAddr, Uri uri);
    }

    AppFileHander handler;
    EditText appAddr;

    public OtaAppOnlyFragment(AppFileHander handler) {
        this.handler = handler;
    }

    public static OtaAppOnlyFragment newInstance() {
        OtaAppOnlyFragment fragment = new OtaAppOnlyFragment(null);
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private long getAppAddr() {
        String s = appAddr.getText().toString().toLowerCase();
        long r = 0;
        if (s.startsWith("0x"))
            r = Integer.parseInt(s.substring(2), 16);
        else
            r = Integer.parseInt(s);
        return r;
    }

    ActivityResultLauncher<String[]> fileLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    this.handler.onLocalAppFile(getAppAddr(), uri);
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_ota_app_only, container, false);
        Button btn = (Button)view.findViewById(R.id.btn_sel_local_app);
        appAddr = (EditText)view.findViewById(R.id.edit_app_load_addr);
        btn.setOnClickListener(__ -> fileLauncher.launch(new String[] {"*/*"}));
        return view;
    }
}