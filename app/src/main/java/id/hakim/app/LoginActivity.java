package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

// Layar masuk: login dengan akun Google (Gmail) lewat Google Sign-In,
// lalu tokennya ditukar menjadi sesi Firebase (AuthApi).
public class LoginActivity extends Activity {
    private static final int RC_MASUK = 9001;
    private GoogleSignInClient klien;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        status = findViewById(R.id.login_status);

        GoogleSignInOptions opsi = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(FirebaseConfig.WEB_CLIENT_ID)
            .requestEmail()
            .build();
        klien = GoogleSignIn.getClient(this, opsi);

        findViewById(R.id.btn_google).setOnClickListener(v -> {
            status.setText("Membuka pilihan akun Google…");
            startActivityForResult(klien.getSignInIntent(), RC_MASUK);
        });
        findViewById(R.id.btn_lokal).setOnClickListener(v -> {
            Session.setModeLokal(this, true);
            bukaUtama();
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != RC_MASUK) return;
        Task<GoogleSignInAccount> tugas = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount akun = tugas.getResult(ApiException.class);
            String tokenGoogle = akun.getIdToken();
            if (tokenGoogle == null) throw new Exception("Token Google kosong");
            status.setText("Akun dipilih: " + akun.getEmail() + "\nMenyiapkan sesi…");
            findViewById(R.id.btn_google).setEnabled(false);
            new Thread(() -> {
                try {
                    AuthApi.Hasil h = AuthApi.masukDenganGoogle(tokenGoogle);
                    Session.ambil(this).simpan(h.uid, h.email, h.nama, h.idToken, h.refreshToken, h.expiresInSec);
                    Session.setModeLokal(this, false);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Selamat datang, " + (h.nama == null || h.nama.isEmpty() ? h.email : h.nama), Toast.LENGTH_LONG).show();
                        SyncEngine.sinkron(this, new SyncEngine.Selesai() {
                            public void ok(String ringkasan) { bukaUtama(); }
                            public void gagal(String pesan) {
                                Toast.makeText(LoginActivity.this, "Masuk berhasil. Sinkron awal ditunda: " + potong(pesan), Toast.LENGTH_LONG).show();
                                bukaUtama();
                            }
                        });
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        findViewById(R.id.btn_google).setEnabled(true);
                        status.setText("Gagal menyiapkan sesi Firebase.");
                        Toast.makeText(this, "Gagal masuk: " + potong(e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } catch (ApiException e) {
            status.setText("Login dibatalkan / gagal (kode " + e.getStatusCode() + "). Coba lagi, atau pakai mode lokal.");
        } catch (Exception e) {
            status.setText("Ada masalah: " + potong(e.getMessage()));
        }
    }

    static String potong(String s) {
        if (s == null) return "-";
        return s.length() > 140 ? s.substring(0, 140) + "…" : s;
    }

    private void bukaUtama() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
