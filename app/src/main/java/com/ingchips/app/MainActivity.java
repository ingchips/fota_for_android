package com.ingchips.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.ingchips.app.databinding.ActivityMainBinding;
import com.ingchips.fota.*;

import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private final String APP_PERMISSIONS [] = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
    };

    private void ensurePermissions(Context ctx) {
        ArrayList<String> permissions =  new ArrayList<>();

        for (String s: APP_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(ctx, s) !=
                    PackageManager.PERMISSION_GRANTED)
                permissions.add(s);
        }
        if (permissions.size() > 0)
            ActivityCompat.requestPermissions(this, APP_PERMISSIONS, 1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        MainActivity self = this;

        FirstFragment.fab = binding.fab;

        binding.fab.setOnClickListener(view -> {
                    ensurePermissions(this);
                    if (!BLEUtil.permissionsOK(this)) {
                        Snackbar.make(binding.getRoot(), "This app need Location to use BLE", Snackbar.LENGTH_LONG)
                                .setAction("OK", view2 -> BLEUtil.requestBlePermissions(self, 1))
                                .show();
                    } else
                        FirstFragment.scan();
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}