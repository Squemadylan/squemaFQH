package com.byterax.phoenix.read;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.byterax.phoenix.read.service.ServiceClient;

public class MainActivity extends Activity {

    private View statusCardRoot;
    private TextView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView statusTargets;
    private TextView hintText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusCardRoot = findViewById(R.id.status_card_root);
        statusIcon = findViewById(R.id.status_card_icon);
        statusTitle = findViewById(R.id.status_card_title);
        statusSubtitle = findViewById(R.id.status_card_subtitle);
        statusTargets = findViewById(R.id.status_card_targets);
        hintText = findViewById(R.id.hint_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ServiceClient.get().tryConnect();
        refreshStatusCard();
    }

    private void refreshStatusCard() {
        if (statusCardRoot == null) {
            return;
        }

        boolean framework = RuntimeDetector.isFrameworkInstalled();
        boolean libxposed = RuntimeDetector.isLibxposedLoaded();
        boolean root = RuntimeDetector.hasRoot();
        boolean alive = framework || libxposed || root;

        int bgColor = alive ? getColor(R.color.status_active) : getColor(R.color.status_inactive);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f * getResources().getDisplayMetrics().density);
        bg.setColor(bgColor);
        statusCardRoot.setBackground(bg);

        statusIcon.setText(alive ? "\u263A" : "\u2639");
        statusTitle.setText(alive
                ? getString(R.string.status_channel_ready)
                : getString(R.string.status_channel_off));

        if (alive) {
            statusSubtitle.setText(getString(R.string.status_runtime_summary,
                    String.valueOf(framework), String.valueOf(libxposed), String.valueOf(root)));
            statusTargets.setVisibility(View.VISIBLE);
            statusTargets.setText("\u756a\u8304 \u2713  /  \u7ea2\u679c \u2713");
        } else {
            statusSubtitle.setText(getString(R.string.status_service_off));
            statusTargets.setVisibility(View.GONE);
        }

        hintText.setVisibility(alive ? View.GONE : View.VISIBLE);
        hintText.setText(R.string.status_hint);
    }
}
