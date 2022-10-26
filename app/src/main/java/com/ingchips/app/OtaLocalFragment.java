package com.ingchips.app;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link OtaLocalFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OtaLocalFragment extends Fragment {

    interface LocalFileHander {
        public void onLocalFile(Uri uri);
    }

    LocalFileHander handler;

    public OtaLocalFragment(LocalFileHander handler) {
        this.handler = handler;
    }

    public static OtaLocalFragment newInstance() {
        OtaLocalFragment fragment = new OtaLocalFragment(null);
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    ActivityResultLauncher<String[]> fileLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    this.handler.onLocalFile(uri);
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_ota_local, container, false);
        Button btn = (Button)view.findViewById(R.id.btn_sel_file);
        btn.setOnClickListener(__ -> fileLauncher.launch(new String[] {"application/zip"}));
        return view;
    }
}