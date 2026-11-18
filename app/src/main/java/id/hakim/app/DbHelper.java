package id.hakim.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {
    // SQLite lokal menjadi cermin Firestore: setiap baris menyimpan fid (ID dokumen
    // Firestore) dan kolom tersinkron (0 = ada perubahan yang belum terkirim ke awan).
    public DbHelper(Context ctx) { super(ctx, "catatan_uang.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transaksi (id INTEGER PRIMARY KEY AUTOINCREMENT, jenis TEXT, jumlah INTEGER, kategori TEXT, tanggal TEXT, catatan TEXT, dibuat INTEGER, fid TEXT, tersinkron INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            db.execSQL("ALTER TABLE transaksi ADD COLUMN fid TEXT");
            db.execSQL("ALTER TABLE transaksi ADD COLUMN tersinkron INTEGER NOT NULL DEFAULT 0");
        }
    }

    public static class Tx {
        public long id; public String jenis, kategori, tanggal, catatan, fid; public long jumlah; public int tersinkron;
    }

    public long insert(Tx t) {
        ContentValues v = new ContentValues();
        v.put("jenis", t.jenis); v.put("jumlah", t.jumlah); v.put("kategori", t.kategori);
        v.put("tanggal", t.tanggal); v.put("catatan", t.catatan); v.put("dibuat", System.currentTimeMillis());
        v.put("tersinkron", 0);
        return getWritableDatabase().insert("transaksi", null, v);
    }
    public void update(Tx t) {
        ContentValues v = new ContentValues();
        v.put("jenis", t.jenis); v.put("jumlah", t.jumlah); v.put("kategori", t.kategori);
        v.put("tanggal", t.tanggal); v.put("catatan", t.catatan); v.put("tersinkron", 0);
        getWritableDatabase().update("transaksi", v, "id=?", new String[]{String.valueOf(t.id)});
    }
    public void delete(long id) { getWritableDatabase().delete("transaksi", "id=?", new String[]{String.valueOf(id)}); }

    public Tx byId(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transaksi WHERE id=?", new String[]{String.valueOf(id)});
        Tx t = null;
        if (c.moveToFirst()) t = fromCursor(c);
        c.close(); return t;
    }
    public List<Tx> all() { return query("SELECT * FROM transaksi ORDER BY tanggal DESC, id DESC", null); }

    /** Pencarian mutasi: kata dicari di kategori & catatan; jenis null = semua. */
    public List<Tx> cari(String kata, String jenis) {
        String sql = "SELECT * FROM transaksi WHERE (kategori LIKE ? OR catatan LIKE ?)";
        List<String> args = new ArrayList<>();
        String pola = "%" + (kata == null ? "" : kata.trim()) + "%";
        args.add(pola); args.add(pola);
        if (jenis != null) { sql += " AND jenis=?"; args.add(jenis); }
        sql += " ORDER BY tanggal DESC, id DESC";
        return query(sql, args.toArray(new String[0]));
    }
    public List<Tx> byMonth(String ym) { return query("SELECT * FROM transaksi WHERE substr(tanggal,1,7)=? ORDER BY tanggal DESC, id DESC", new String[]{ym}); }

    // Untuk sinkronisasi
    public List<Tx> belumSinkron() { return query("SELECT * FROM transaksi WHERE tersinkron=0", null); }
    public List<Tx> sudahSinkron() { return query("SELECT * FROM transaksi WHERE tersinkron=1 AND fid IS NOT NULL", null); }
    public Tx cariFid(String fid) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM transaksi WHERE fid=?", new String[]{fid});
        Tx t = null;
        if (c.moveToFirst()) t = fromCursor(c);
        c.close(); return t;
    }
    public void tandaiSinkron(long id, String fid) {
        ContentValues v = new ContentValues();
        v.put("fid", fid); v.put("tersinkron", 1);
        getWritableDatabase().update("transaksi", v, "id=?", new String[]{String.valueOf(id)});
    }
    public long masukkanDariAwan(Tx t) {
        ContentValues v = new ContentValues();
        v.put("jenis", t.jenis); v.put("jumlah", t.jumlah); v.put("kategori", t.kategori);
        v.put("tanggal", t.tanggal); v.put("catatan", t.catatan); v.put("dibuat", System.currentTimeMillis());
        v.put("fid", t.fid); v.put("tersinkron", 1);
        return getWritableDatabase().insert("transaksi", null, v);
    }
    public void perbaruiDariAwan(Tx t) {
        ContentValues v = new ContentValues();
        v.put("jenis", t.jenis); v.put("jumlah", t.jumlah); v.put("kategori", t.kategori);
        v.put("tanggal", t.tanggal); v.put("catatan", t.catatan); v.put("tersinkron", 1);
        getWritableDatabase().update("transaksi", v, "fid=?", new String[]{t.fid});
    }

    private List<Tx> query(String sql, String[] args) {
        List<Tx> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(fromCursor(c));
        c.close(); return out;
    }
    private Tx fromCursor(Cursor c) {
        Tx t = new Tx();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.jenis = c.getString(c.getColumnIndexOrThrow("jenis"));
        t.jumlah = c.getLong(c.getColumnIndexOrThrow("jumlah"));
        t.kategori = c.getString(c.getColumnIndexOrThrow("kategori"));
        t.tanggal = c.getString(c.getColumnIndexOrThrow("tanggal"));
        t.catatan = c.getString(c.getColumnIndexOrThrow("catatan"));
        int fi = c.getColumnIndex("fid");
        if (fi >= 0 && !c.isNull(fi)) t.fid = c.getString(fi);
        int ti = c.getColumnIndex("tersinkron");
        if (ti >= 0) t.tersinkron = c.getInt(ti);
        return t;
    }
    public long sum(String jenis, String ymOrNull) {
        String sql = "SELECT COALESCE(SUM(jumlah),0) FROM transaksi WHERE jenis=?";
        String[] args = ymOrNull == null ? new String[]{jenis} : new String[]{jenis, ymOrNull};
        if (ymOrNull != null) sql += " AND substr(tanggal,1,7)=?";
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        long r = 0; if (c.moveToFirst()) r = c.getLong(0);
        c.close(); return r;
    }
}
