package com.painelnexus.monitor.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.painelnexus.monitor.R;
import com.painelnexus.monitor.databinding.ActivityMainBinding;
import com.painelnexus.monitor.service.FloatingMonitorService;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnToggle.setOnClickListener(v -> handleToggle());
        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, PermissionActivity.class)));

        askNotificationPermission();
        animateEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi(FloatingMonitorService.isRunning());
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void animateEntrance() {
        AnimationSet set = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(
                0.7f, 1f, 0.7f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        scale.setDuration(600);
        fade.setDuration(600);
        set.addAnimation(scale);
        set.addAnimation(fade);
        binding.ivLogo.startAnimation(set);

        binding.cardMain.setTranslationY(80f);
        binding.cardMain.setAlpha(0f);
        binding.cardMain.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(200)
                .start();
    }

    private void updateUi(boolean running) {
        binding.tvStatusBadge.setText(running ? "● ATIVO" : "● INATIVO");
        binding.tvStatusBadge.setTextColor(getColor(
                running ? R.color.nexus_green : R.color.nexus_red));
        binding.btnToggle.setText(running ? "PARAR MONITOR" : "INICIAR MONITOR");
    }

    private void handleToggle() {
        if (FloatingMonitorService.isRunning()) {
            stopService(new Intent(this, FloatingMonitorService.class));
            updateUi(false);
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Permita a sobreposição para mostrar o painel sobre outros apps.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        ContextCompat.startForegroundService(
                this, new Intent(this, FloatingMonitorService.class));
        updateUi(true);
        Toast.makeText(this,
                "Monitor iniciado. Abra seu jogo; arraste o painel para mover.",
                Toast.LENGTH_LONG).show();
    }
}
