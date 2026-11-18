package id.hakim.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Sinkronisasi dua arah antara SQLite lokal dan Firestore milik pengguna yang masuk.
// Strategi: dorong perubahan lokal dulu, lalu tarik keadaan awan dan jadikan cermin lokal.
public class SyncEngine {

    public interface Selesai {
        void ok(String ringkasan);
        void gagal(String pesan);
    }

    public static void sinkron(Context ctx, Selesai cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Handler h = new Handler(Looper.getMainLooper());
            try {
                Session s = Session.ambil(app);
                if (!s.masuk()) throw new Exception("Belum masuk");
                if (s.tokenHampirHabis()) {
                    AuthApi.Hasil segar = AuthApi.segarkan(s);
                    s.perbaruiToken(segar.idToken, segar.refreshToken, segar.expiresInSec);
                }
                String ringkasan = lakukan(app, s);
                h.post(() -> cb.ok(ringkasan));
            } catch (Exception e) {
                final String pesan = e.getMessage() == null ? "Koneksi bermasalah" : e.getMessage();
                h.post(() -> cb.gagal(pesan));
            }
        }).start();
    }

    private static String lakukan(Context ctx, Session s) throws Exception {
        DbHelper db = new DbHelper(ctx);
        int terkirim = 0, ditarik = 0;

        // 1) Hapus di awan dokumen yang sudah dihapus lokal
        Set<String> antri = Session.antrianHapus(ctx);
        Set<String> selesai = new HashSet<>();
        for (String fid : antri) {
            int code = FirestoreApi.hapus(s.uid, s.idToken, fid);
            if (code == 200 || code == 404) selesai.add(fid);
            else if (code == 401) throw new Exception("Sesi berakhir, silakan masuk lagi");
            else throw new Exception("Gagal menghapus di awan (HTTP " + code + ")");
        }
        Session.kosongkanAntrianHapus(ctx, selesai);

        // 2) Dorong transaksi lokal yang belum tersinkron
        for (DbHelper.Tx t : db.belumSinkron()) {
            int code;
            if (t.fid == null || t.fid.isEmpty()) {
                String fidBaru = UUID.randomUUID().toString();
                code = FirestoreApi.buat(s.uid, s.idToken, fidBaru, t);
                if (code == 200 || code == 201) { db.tandaiSinkron(t.id, fidBaru); terkirim++; }
                else if (code == 401) throw new Exception("Sesi berakhir, silakan masuk lagi");
                else throw new Exception("Gagal mengirim ke awan (HTTP " + code + ")");
            } else {
                code = FirestoreApi.ubah(s.uid, s.idToken, t.fid, t);
                if (code == 200) { db.tandaiSinkron(t.id, t.fid); terkirim++; }
                else if (code == 404) { // dokumen awan hilang -> buat ulang
                    int c2 = FirestoreApi.buat(s.uid, s.idToken, t.fid, t);
                    if (c2 == 200 || c2 == 201) { db.tandaiSinkron(t.id, t.fid); terkirim++; }
                    else throw new Exception("Gagal mengirim ke awan (HTTP " + c2 + ")");
                }
                else if (code == 401) throw new Exception("Sesi berakhir, silakan masuk lagi");
                else throw new Exception("Gagal memperbarui di awan (HTTP " + code + ")");
            }
        }

        // 3) Tarik keadaan awan: tambahkan/perbarui cermin lokal
        List<DbHelper.Tx> awan = FirestoreApi.daftar(s.uid, s.idToken);
        Set<String> fidAwan = new HashSet<>();
        for (DbHelper.Tx t : awan) {
            fidAwan.add(t.fid);
            DbHelper.Tx lokal = db.cariFid(t.fid);
            if (lokal == null) { db.masukkanDariAwan(t); ditarik++; }
            else { db.perbaruiDariAwan(t); }
        }

        // 4) Baris lokal yang punya fid tapi sudah tidak ada di awan -> dihapus di perangkat lain
        for (DbHelper.Tx t : db.sudahSinkron()) {
            if (t.fid != null && !fidAwan.contains(t.fid)) db.delete(t.id);
        }
        db.close();
        return "Sinkron selesai: " + terkirim + " terkirim, " + ditarik + " baru dari awan";
    }
}
