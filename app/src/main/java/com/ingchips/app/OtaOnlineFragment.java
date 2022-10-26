package com.ingchips.app;

import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link OtaOnlineFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OtaOnlineFragment extends Fragment {

    interface OnlineFileHander {
        public void onDownloadOnline(String uri);
    }

    OnlineFileHander handler;

    public OtaOnlineFragment(OnlineFileHander handler) {
        this.handler = handler;
    }

    public static OtaOnlineFragment newInstance() {
        OtaOnlineFragment fragment = new OtaOnlineFragment(null);
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_ota_online, container, false);
        Button btn = (Button)view.findViewById(R.id.btnRecheck);
        btn.setOnClickListener(_view -> {
            String uri = ((EditText)view.findViewById(R.id.edit_fota_server)).getText().toString();
            handler.onDownloadOnline(uri);
        });
        return view;
    }
}