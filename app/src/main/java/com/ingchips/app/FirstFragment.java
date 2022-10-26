package com.ingchips.app;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ingchips.app.databinding.FragmentFirstBinding;
import com.ingchips.fota.*;

import java.util.Hashtable;

public class FirstFragment extends Fragment {

    static public FloatingActionButton fab = null;

    class DevView {
        public LinearLayout Container = null;
        TextView label = null;
        Button btn = null;

        public void Update(ScanResult result) throws SecurityException
        {
            String s = result.getDevice().getName();
            label.setText((s != null) && (s.length() > 0) ? s : "<unnamed>");
        }

        LinearLayout mkTitle(Context ctx, boolean hasBtn)
        {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            LinearLayout Container = new LinearLayout(ctx);
            Container.setOrientation(LinearLayout.HORIZONTAL);
            Container.setLayoutParams(params);

            params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.weight = 1.0f;
            params.gravity = Gravity.CENTER_VERTICAL;
            TextView t = new TextView(ctx);
            t.setLayoutParams(params);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                t.setTextAppearance(androidx.appcompat.R.style.Base_TextAppearance_AppCompat_Headline);
            }

            label = t;
            Container.addView(t);

            if (hasBtn) {
                btn = new Button(ctx);

                params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.RIGHT;

                btn.setText("Connect");
                btn.setLayoutParams(params);
                Container.addView(btn);
            }

            return Container;
        }

        public DevView(Context ctx, ScanResult result, FirstFragment owner)
        {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            BluetoothDevice dev = result.getDevice();

            Container = new LinearLayout(ctx);
            Container.setOrientation(LinearLayout.VERTICAL);
            Container.setLayoutParams(params);

            LinearLayout title = mkTitle(ctx, (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) || result.isConnectable());

            TextView t = new TextView(ctx);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                t.setTextAppearance(androidx.appcompat.R.style.Base_TextAppearance_AppCompat_Subhead);
            }
            t.setText(dev.getAddress());

            Container.addView(title);
            Container.addView(t);

            if (btn != null)
                btn.setOnClickListener(view -> {
                    owner.connectDev(dev);
                });

            Update(result);
        }
    }

    private FragmentFirstBinding binding;
    static FirstFragment instance;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 30000;
    private Hashtable<String, DevView> devDict = new Hashtable<>();

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        instance = this;
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    void initBLEAdapter() {
        BLEUtil.init(this.getContext());
    }

    public static BluetoothDevice devToConnect = null;

    void connectDev(BluetoothDevice dev) throws SecurityException {
        fab.setVisibility(View.INVISIBLE);
        BLEUtil.stopScan();
        devToConnect = dev;
        NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment);
    }

    void doScan() throws SecurityException {
        if (BLEUtil.isScanning()) return;

        devDict.clear();
        initBLEAdapter();
        if (!BLEUtil.isReady()) return;

        BLEUtil.startScan(result -> {
            String k = result.getDevice().getAddress();
            if (devDict.containsKey(k)) return;
            DevView t = new DevView(binding.getRoot().getContext(), result, instance);
            devDict.put(k, t);
            binding.devListView.addView(t.Container);
        }, SCAN_PERIOD);
    }

    static public void scan() {
        if (instance == null) return;
        instance.doScan();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (fab != null)
            fab.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        instance = null;
    }

}