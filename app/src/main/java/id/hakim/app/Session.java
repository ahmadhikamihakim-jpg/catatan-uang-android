package id.hakim.app;

import android.content.Context;
import android.content.SharedPreferences;

// Sesi login Firebase (hasil tukar token Google -> Firebase Auth REST).
// Disimpan lokal agar pengguna tidak perlu login setiap membuka aplikasi.
public class Session {
    private final SharedPreferences sp;

    public String uid, email, nama, idToken, refreshToken;
    public long kedaluwarsa; // epoch ms kira-kira kapan idToken berakhir

    private Session(SharedPreferences sp) { this.sp = sp; }

    public static Session ambil(Context ctx) {
        Session s = new Session(ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE));
        s.uid = s.sp.getString("uid", null);
        s.email = s.sp.getString("email", null);
        s.nama = s.sp.getString("nama", null);
        s.idToken = s.sp.getString("idToken", null);
        s.refreshToken = s.sp.getString("refreshToken", null);
        s.kedaluwarsa = s.sp.getLong("kedaluwarsa", 0);
        return s;
    }

    public boolean masuk() { return uid != null && refreshToken != null; }

    public void simpan(String uid, String email, String nama, String idToken, String refreshToken, long expiresInSec) {
        this.uid = uid; this.email = email; this.nama = nama;
        this.idToken = idToken; this.refreshToken = refreshToken;
        this.kedaluwarsa = System.currentTimeMillis() + expiresInSec * 1000L;
        sp.edit().putString("uid", uid).putString("email", email).putString("nama", nama)
            .putString("idToken", idToken).putString("refreshToken", refreshToken)
            .putLong("kedaluwarsa", kedaluwarsa).apply();
    }

    public void perbaruiToken(String idToken, String refreshToken, long expiresInSec) {
        this.idToken = idToken;
        if (refreshToken != null) this.refreshToken = refreshToken;
        this.kedaluwarsa = System.currentTimeMillis() + expiresInSec * 1000L;
        sp.edit().putString("idToken", this.idToken).putString("refreshToken", this.refreshToken)
            .putLong("kedaluwarsa", kedaluwarsa).apply();
    }

    public boolean tokenHampirHabis() {
        return idToken == null || System.currentTimeMillis() > kedaluwarsa - 5 * 60 * 1000L;
    }

    public void hapus() {
        uid = email = nama = idToken = refreshToken = null;
        kedaluwarsa = 0;
        sp.edit().clear().apply();
    }

    // Antrian hapus: fid dokumen Firestore yang sudah dihapus lokal & menunggu dihapus di awan.
    public static java.util.Set<String> antrianHapus(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE);
        return new java.util.HashSet<>(p.getStringSet("antrian_hapus", new java.util.HashSet<String>()));
    }
    public static void tambahAntrianHapus(Context ctx, String fid) {
        java.util.Set<String> s = antrianHapus(ctx);
        s.add(fid);
        ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE).edit().putStringSet("antrian_hapus", s).apply();
    }
    public static void kosongkanAntrianHapus(Context ctx, java.util.Set<String> selesai) {
        java.util.Set<String> s = antrianHapus(ctx);
        s.removeAll(selesai);
        ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE).edit().putStringSet("antrian_hapus", s).apply();
    }

    public static boolean modeLokal(Context ctx) {
        return ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE).getBoolean("mode_lokal", false);
    }
    public static void setModeLokal(Context ctx, boolean v) {
        ctx.getSharedPreferences("sesi", Context.MODE_PRIVATE).edit().putBoolean("mode_lokal", v).apply();
    }
}
