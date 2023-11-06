package com.quuantum.autopilot;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private ListAdapter listAdapter;

    private static final int PERMISSION_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, PERMISSION_CODE);
        } else {
            showList();
            if (!this.isServiceRunning()) {
                this.startService();
            }
        }

        checkIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (!permissions[i].equals(Manifest.permission.RECEIVE_SMS)) {
                continue;
            }

            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                showList();
                if (!this.isServiceRunning()) {
                    this.startService();
                }
            } else {
                showInfo(getResources().getString(R.string.permission_needed));
            }

            return;
        }
    }

    private void checkIntent(){
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null && "qaupiwc".equals(uri.getScheme()) && "import".equals(uri.getHost())) {
                String sender = uri.getQueryParameter("sender");
                String url = uri.getQueryParameter("url");
                String headers = uri.getQueryParameter("headers");
                String body = uri.getQueryParameter("body");

                JSONObject data = new JSONObject();
                try {
                    data.put("sender",sender);
                    data.put("url",url);
                    data.put("headers",headers);
                    data.put("body",body);

                    showAddDialog(data);
                } catch (JSONException e) {
//                    throw new RuntimeException(e);
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void showList() {
        showInfo("");

        context = this;
        ListView listview = findViewById(R.id.listView);

        ArrayList<ForwardingConfig> configs = ForwardingConfig.getAll(context);

        listAdapter = new ListAdapter(configs, context);
        listAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if(listAdapter.getCount() ==0){
                    showInfo("No configs found yet.\n Scan the QR code provided in plugin settings or add the config manually by clicking '+' in right corner.");
                }
                else{
                    showInfo("");
                }
                super.onChanged();
            }
        });

        listview.setAdapter(listAdapter);

        FloatingActionButton fab = findViewById(R.id.btn_add);
        fab.setOnClickListener(v-> showAddDialog(null));
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if(com.quuantum.autopilot.SmsReceiverService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startService() {
        Context appContext = getApplicationContext();
        Intent intent = new Intent(this, SmsReceiverService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    private void showInfo(String text) {
        TextView notice = findViewById(R.id.info_notice);
        notice.setText(text);
    }

    private void showAddDialog(@Nullable JSONObject data) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View view = getLayoutInflater().inflate(R.layout.dialog_add, null);
            final EditText senderInput = view.findViewById(R.id.input_phone);
            final EditText urlInput = view.findViewById(R.id.input_url);
            final EditText templateInput = view.findViewById(R.id.input_json_template);
            final EditText headersInput = view.findViewById(R.id.input_json_headers);
            final CheckBox ignoreSslCheckbox = view.findViewById(R.id.input_ignore_ssl);

            templateInput.setText(ForwardingConfig.getDefaultJsonTemplate());
            headersInput.setText(ForwardingConfig.getDefaultJsonHeaders());
            try {
                if (data != null) {
                    ignoreSslCheckbox.setChecked(true);

                    String sender = data.getString("sender");
                    if (!"".equals(sender)) {
                        senderInput.setText(sender);
                    }

                    String url = data.getString("url");
                    if (!"".equals(url)) {
                        urlInput.setText(url);
                    }
                    String headers = data.getString("headers");
                    if (!"".equals(headers)) {
                        headersInput.setText(headers);
                    }
                    String body = data.getString("body");
                    if (!"".equals(body)) {
                        templateInput.setText(body);
                    }
                }
            }catch (JSONException e){
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            builder.setView(view);
            builder.setPositiveButton(R.string.btn_add, null);
            builder.setNegativeButton(R.string.btn_cancel, null);
            final AlertDialog dialog = builder.show();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view1 -> {
                String sender = senderInput.getText().toString();
                if (TextUtils.isEmpty(sender)) {
                    senderInput.setError(getString(R.string.error_empty_sender));
                    return;
                }

                String url = urlInput.getText().toString();
                if (TextUtils.isEmpty(url)) {
                    urlInput.setError(getString(R.string.error_empty_url));
                    return;
                }

                try {
                    new URL(url);
                } catch (MalformedURLException e) {
                    urlInput.setError(getString(R.string.error_wrong_url));
                    return;
                }

                String template = templateInput.getText().toString();
                try {
                    new JSONObject(template);
                } catch (JSONException e) {
                    templateInput.setError(getString(R.string.error_wrong_json));
                    return;
                }

                String headers = headersInput.getText().toString();
                try {
                    new JSONObject(headers);
                } catch (JSONException e) {
                    headersInput.setError(getString(R.string.error_wrong_json));
                    return;
                }

                boolean ignoreSsl = ignoreSslCheckbox.isChecked();

                ForwardingConfig config = new ForwardingConfig(context);
                config.setSender(sender);
                config.setUrl(url);
                config.setTemplate(template);
                config.setHeaders(headers);
                config.setIgnoreSsl(ignoreSsl);
                config.save();

                listAdapter.add(config);

                dialog.dismiss();
            });
    }

    public void showEditDialog(ForwardingConfig config) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = getLayoutInflater().inflate(R.layout.dialog_add, null);
        final EditText senderInput = view.findViewById(R.id.input_phone);
        final EditText urlInput = view.findViewById(R.id.input_url);
        final EditText templateInput = view.findViewById(R.id.input_json_template);
        final EditText headersInput = view.findViewById(R.id.input_json_headers);
        final CheckBox ignoreSslCheckbox = view.findViewById(R.id.input_ignore_ssl);

        templateInput.setText(config.getTemplate());
        headersInput.setText(config.getHeaders());
        senderInput.setText(config.getSender());
        urlInput.setText(config.getUrl());
        ignoreSslCheckbox.setChecked(config.getIgnoreSsl());

        builder.setView(view);
        builder.setPositiveButton(R.string.btn_update, null);
        builder.setNegativeButton(R.string.btn_cancel, null);
        final AlertDialog dialog = builder.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view1 -> {
            String sender = senderInput.getText().toString();
            if (TextUtils.isEmpty(sender)) {
                senderInput.setError(getString(R.string.error_empty_sender));
                return;
            }

            String url = urlInput.getText().toString();
            if (TextUtils.isEmpty(url)) {
                urlInput.setError(getString(R.string.error_empty_url));
                return;
            }

            try {
                new URL(url);
            } catch (MalformedURLException e) {
                urlInput.setError(getString(R.string.error_wrong_url));
                return;
            }

            String template = templateInput.getText().toString();
            try {
                new JSONObject(template);
            } catch (JSONException e) {
                templateInput.setError(getString(R.string.error_wrong_json));
                return;
            }

            String headers = headersInput.getText().toString();
            try {
                new JSONObject(headers);
            } catch (JSONException e) {
                headersInput.setError(getString(R.string.error_wrong_json));
                return;
            }

            boolean ignoreSsl = ignoreSslCheckbox.isChecked();

            config.remove();// remove the old config first

            config.setSender(sender);
            config.setUrl(url);
            config.setTemplate(template);
            config.setHeaders(headers);
            config.setIgnoreSsl(ignoreSsl);
            config.save();

            listAdapter.notifyDataSetChanged();

            dialog.dismiss();
        });
    }
}