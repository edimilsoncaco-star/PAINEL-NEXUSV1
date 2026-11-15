package com.painelnexus.monitor.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.painelnexus.monitor.R;
import com.painelnexus.monitor.databinding.ActivityPermissionBinding;

public class PermissionActivity extends AppCompatActivity {

    private ActivityPermissionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnGrantOverlay.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Permissão já concedida ✓", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean granted = Settings.canDrawOverlays(this);
        binding.tvOverlayStatus.setText(granted
                ? "Sobreposição: CONCEDIDA ✓"
                : "Sobreposição: NEGADA ✗");
        binding.tvOverlayStatus.setTextColor(getColor(
                granted ? R.color.nexus_green : R.color.nexus_red));
    }
}
