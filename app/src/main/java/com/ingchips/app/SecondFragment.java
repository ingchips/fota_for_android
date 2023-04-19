package com.ingchips.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.content.ContentResolver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.res.TypedArrayUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.ingchips.app.databinding.FragmentSecondBinding;

import com.ingchips.fota.*;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SecondFragment extends Fragment implements OtaLocalFragment.LocalFileHander,
        OtaOnlineFragment.OnlineFileHander, OtaAppOnlyFragment.AppFileHander {

    private FragmentSecondBinding binding;
    FragmentActivity activity;

    Updater updater = null;

    OtaLocalFragment otaLocal = null;
    OtaOnlineFragment otaOnline = null;
    OtaAppOnlyFragment otaAppOnly = null;

    ViewPagerAdapter viewAdapter = null;
    private TabLayoutMediator mediator;
    private UpdatePackage pack = null;
    private PlanBuilder.Plan plan = null;

    public class ViewPagerAdapter
            extends FragmentStateAdapter {

        public ViewPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return otaOnline;
                case 1: return otaLocal;
                default: return otaAppOnly;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        try {
            binding = FragmentSecondBinding.inflate(inflater, container, false);
        } catch (Exception e) {
            Log.i("INFO", e.toString());
        }
        return binding.getRoot();
    }

    private void showMsg(String s) {
        binding.textLog.setText(s);
    }

    private void showDevVersion() {
        binding.verDevApp.setText(updater.getDevVer().getApp().toString());
        binding.verDevPlatform.setText(updater.getDevVer().getPlatform().toString());
    }

    private void PreParsePackage() {
        binding.verLatestApp.setText("...");
        binding.verLatestPlatform.setText("...");
        binding.verLatestPlatform.setBackgroundColor(Color.WHITE);
        binding.verLatestApp.setBackgroundColor(Color.WHITE);
        binding.textUpdateInfo.setText("");

        binding.btnUpdate.setVisibility(View.INVISIBLE);
        pack = null;
        plan = null;
    }

    private void ParsePacket() {
        if (pack == null) {
            showMsg("failed to parse update package");
            return;
        }

        binding.textUpdateInfo.setText(pack.readme);
        binding.verLatestApp.setText(pack.version.getApp().toString());
        binding.verLatestPlatform.setText(pack.version.getPlatform().toString());

        plan = PlanBuilder.fromPackage(pack, updater.getDevVer());
        if (plan == null) {
            showMsg("failed to parse update package");
            return;
        }

        if (plan.items.size() < 1) {
            showMsg("up to date");
            return;
        }

        Spinner spinner = binding.chipFamilySpinner;
        if (!PlanBuilder.makeFlashProcedure(plan, (int)spinner.getSelectedItemId(), getFlashTopAddress())) {
            showMsg("failed to make flash procedure");
            plan = null;
        } else {
            binding.btnUpdate.setVisibility(View.VISIBLE);
            if (plan.platform)
                binding.verLatestPlatform.setBackgroundColor(getResources().getColor(R.color.teal_200));
            if (plan.app)
                binding.verLatestApp.setBackgroundColor(getResources().getColor(R.color.teal_200));
        }
    }

    interface FileDownloaded {
        public void OnComplete(byte [] content);
    }

    private static byte [] dumpInputStream(InputStream stream) {
        ArrayList<Byte> lst = new ArrayList<>();
        byte[] buffer = new byte[1024];
        try {
            DataInputStream dis = new DataInputStream(stream);
            int length;
            while ((length = dis.read(buffer)) > 0)
                for (int i = 0; i < length; i++) lst.add(buffer[i]);
        } catch (Exception e) {}

        return ArrayList2ByteArray(lst);
    }

    private void Download(String uri,
                          FileDownloaded onDownloaded) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte []r = null;
                try {
                    URL u = new URL(uri);
                    r = dumpInputStream(u.openStream());
                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMsg("EXCEPTION: " + e.getMessage());
                        }
                    });
                }
                final byte []t = r;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onDownloaded.OnComplete(t);
                    }
                });
            }
        }).start();
    }

    private void ParseLastestJson(String server, byte []b) {
        try {
            JSONObject obj = new JSONObject(new String(b));
            Download(server + "/" + obj.getString("package"),
                    content ->
                            loadOtaPackStream(new ByteArrayInputStream(content))
            );
        } catch (Exception e) {
            showMsg("Exception: " + e.getMessage());
        }
    }

    public void onDownloadOnline(String server) {
        if (server.charAt(server.length() - 1) == '/')
            server = server.substring(0, server.length() - 1);
        String finalServer = server;
        Download(server + "/latest.json",
                content -> ParseLastestJson(finalServer, content));
    }

    private void loadOtaPackStream(InputStream stream) {
        PreParsePackage();

        try {
            pack = UpdatePackage.LoadFromStream(stream);
        } catch (Exception e)  {
            showMsg("EXCEPTION: " + e.getMessage());
        }

        ParsePacket();
    }

    private static byte[] ArrayList2ByteArray(ArrayList<Byte> lst) {
        byte[] t = new byte[lst.size()];
        for (int i = 0; i < lst.size(); i++) t[i] = lst.get(i).byteValue();
        return t;
    }

    private byte[] loadLocalFile(Uri uri) {
        ContentResolver resolver = getContext().getContentResolver();

        try {
            return dumpInputStream(resolver.openInputStream(uri));
        } catch (Exception e)  {
            showMsg("EXCEPTION: " + e.getMessage());
        }
        return null;
    }

    public static String extractFileName(String name) {
        String[] parts = name.split("/");
        return parts[parts.length - 1];
    }

    public static String decodeUri(String url) {
        try {
            return java.net.URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return url;
        }
    }

    public void onLocalAppFile(long appLoadAddr, Uri uri) {
        if (uri == null) {
            Toast.makeText(getContext(), "Please select a file.", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        byte []bin = loadLocalFile(uri);
        if (bin == null) return;

        PreParsePackage();

        String fn = extractFileName(decodeUri(uri.toString()));

        try {
            pack = UpdatePackage.MakeAppOnPackage(appLoadAddr,
                    bin,
                    fn,
                    String.format("On-the-fly update App:\n\n@0x%08x (%s)", appLoadAddr, fn));
        } catch (Exception e)  {
            showMsg("EXCEPTION: " + e.getMessage());
        }

        ParsePacket();
    }

    public void onLocalFile(Uri uri) {
        if (uri == null) {
            Toast.makeText(getContext(), "Please select a file.", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        try {
            loadOtaPackStream(new ByteArrayInputStream(loadLocalFile(uri)));
        } catch (Exception e)  {
            showMsg("EXCEPTION: " + e.getMessage());
        }

        if (pack == null) {
            showMsg("failed to parse update package");
            return;
        }

        ParsePacket();
    }

    private void onDisconnected(BluetoothGatt gatt) {
        Toast.makeText(getContext(), "Disconncted.", Toast.LENGTH_LONG)
                .show();
    }

    private long getFlashTopAddress() {
        String s = binding.editFlashTopAddr.getText().toString();
        return s.startsWith("0x") || s.startsWith("0X") ?
                Integer.parseInt(s.substring(2), 16)
                :
                Integer.parseInt(s);
    }
    private void updateFlashTopAddress() {
        Spinner spinner = binding.chipFamilySpinner;
        long top = PlanBuilder.getFlashTopAddress((int)spinner.getSelectedItemId());
        binding.editFlashTopAddr.setText(String.format("0x%08X", top));
    }

    @SuppressLint("ResourceType")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        activity = this.getActivity();

        binding.chipFamilySpinner.setAdapter(ArrayAdapter.createFromResource(this.getContext(),
                R.array.ota_chip_family, android.R.layout.simple_spinner_dropdown_item));
        binding.chipFamilySpinner.setPrompt("Select chip family");

        binding.chipFamilySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                updateFlashTopAddress();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }
        });

        updateFlashTopAddress();

        if (otaLocal == null) {
            otaLocal = new OtaLocalFragment(this);
            otaOnline = new OtaOnlineFragment(this);
            otaAppOnly = new OtaAppOnlyFragment(this);
            viewAdapter = new ViewPagerAdapter(this);
        }

        binding.otaDataSource.setAdapter(viewAdapter);
        mediator = new TabLayoutMediator(binding.otaTabs, binding.otaDataSource,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        switch (position) {
                            case 0:
                                tab.setText("Online");
                                break;
                            case 1:
                                tab.setText("Local");
                                break;
                            default:
                                tab.setText("On-the-fly");
                                break;
                        }
                       ;
                    }
        });
        mediator.attach();

        BLEUtil.stopScan();

        binding.btnUpdate.setOnClickListener( __ -> {
            SecondFragment.this.updater.doUpdate(plan);
        });

        PreParsePackage();
        binding.otaDataSource.setVisibility(View.INVISIBLE);
        binding.otaTabs.setVisibility(View.INVISIBLE);

        BLEUtil.setOnDisconnected(gatt -> {
            getActivity().runOnUiThread(() -> {
                onDisconnected(gatt);
            });
        });

        new Thread(new Runnable () {
            @Override
            public void run() {
                SecondFragment.this.updater = new Updater(FirstFragment.devToConnect,
                        (v, s) -> {
                            if (v >= 0)
                                binding.progressBar.setProgress(v);
                            if (s != null)
                                showMsg(s);
                        },
                        __ -> {
                            showDevVersion();
                            binding.otaDataSource.setVisibility(View.VISIBLE);
                            binding.otaTabs.setVisibility(View.VISIBLE);
                        },
                        isSecure -> {
                            if (isSecure) {
                                binding.textLog2.setText("SECURE");
                                binding.textLog2.setBackgroundColor(Color.parseColor("#2EC770"));
                            } else {
                                binding.textLog2.setText("UNSECURE");
                                binding.textLog2.setBackgroundColor(Color.parseColor("#FF5722"));
                            }
                        },
                        f -> getActivity().runOnUiThread(f)
                );
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        BLEUtil.setOnDisconnected(null);
        super.onDestroyView();
        updater.abort();
        binding = null;
    }
}