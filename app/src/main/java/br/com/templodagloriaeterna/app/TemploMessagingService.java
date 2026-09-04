package br.com.templodagloriaeterna.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class TemploMessagingService extends FirebaseMessagingService {
    static final String CHANNEL_ID = "avisos_igreja";
    static final String PREFS = "templo_notifications";
    static final String PREF_ENABLED = "enabled";
    static final String PREF_ASKED = "asked";
    static final String PREF_TOKEN = "token";
    static final String PREF_PREFERENCES = "preferences";
    static final String DEFAULT_PREFERENCES = "{\"avisos\":true,\"comunidade\":true,\"agenda\":true,\"cartas\":true,\"ministerios\":true,\"midia\":true,\"escola\":true,\"kids\":true}";

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_TOKEN, token).apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean(PREF_ENABLED, false)) return;

        Map<String, String> data = message.getData();
        String title = valueOr(data.get("title"), "Templo da Glória Eterna");
        String body = valueOr(data.get("body"), "Você recebeu um novo aviso.");
        String url = valueOr(data.get("url"), "https://www.templodagloriaeterna.com.br/membro/notificacoes");

        createNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_NOTIFICATION_URL, url);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                Math.abs((url + System.currentTimeMillis()).hashCode()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_templo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(getColor(R.color.gold))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(Math.abs((message.getMessageId() == null ? url : message.getMessageId()).hashCode()), notification.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
