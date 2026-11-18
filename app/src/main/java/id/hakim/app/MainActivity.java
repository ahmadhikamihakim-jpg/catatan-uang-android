package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.util.List;

public class MainActivity extends Activity {
    private DbHelper db;
    private List<DbHelper.Tx> items;
    private TxAdapter adapter;
    private boolean sinkronBerjalan = false;
    private boolean saldoTampil = true;
    private long saldoNilai = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Gerbang masuk: belum login & bukan mode lokal -> ke layar login
        if (!Session.ambil(this).masuk() && !Session.modeLokal(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        NavBawah.pasang(this, "beranda");
        db = new DbHelper(this);
        // Saklar sembunyi/tampilkan saldo (pilihannya diingat)
        saldoTampil = getSharedPreferences("tampilan", MODE_PRIVATE).getBoolean("saldo_tampil", true);
        findViewById(R.id.btn_mata).setOnClickListener(v -> {
            saldoTampil = !saldoTampil;
            getSharedPreferences("tampilan", MODE_PRIVATE).edit().putBoolean("saldo_tampil", saldoTampil).apply();
            tampilkanSaldo();
        });
        setupBarAkun();
        // Sinkron otomatis saat aplikasi dibuka (bila sudah masuk)
        if (Session.ambil(this).masuk()) jalankanSinkron(true);
        adapter = new TxAdapter();
        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = new Intent(this, FormActivity.class);
            i.putExtra("id", items.get(pos).id);
            startActivity(i);
        });
        findViewById(R.id.btn_in).setOnClickListener(v -> openForm("masuk"));
        findViewById(R.id.btn_out).setOnClickListener(v -> openForm("keluar"));
        findViewById(R.id.btn_cashflow).setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));
        findViewById(R.id.btn_mutasi).setOnClickListener(v -> startActivity(new Intent(this, MutasiActivity.class)));
        findViewById(R.id.btn_aktivitas).setOnClickListener(v -> startActivity(new Intent(this, AktivitasActivity.class)));
        pasUkuranMenuCepat();
        findViewById(R.id.btn_export).setOnClickListener(v -> {
            String ym = Util.ymNow();
            byte[] pdf = ExportUtil.buatPdf(db.byMonth(ym), "Bulan " + Util.bulanNama(ym));
            ExportUtil.mulaiSimpan(this, "laporan-rens-kitchen-" + ym + ".pdf", pdf);
        });
    }


    /** Kecilkan huruf sampai teks benar-benar muat di lebar tampilan (diukur, bukan ditebak). */
    private static float ukuranPas(TextView tv, float spMulai, float spMin) {
        int lebar = tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
        if (lebar <= 0) return spMulai;
        String teks = tv.getText().toString();
        if (tv instanceof Button) teks = teks.toUpperCase(java.util.Locale.ROOT);
        android.graphics.Paint p = tv.getPaint();
        float sp = spMulai;
        while (sp > spMin) {
            p.setTextSize(sp * tv.getResources().getDisplayMetrics().scaledDensity);
            if (p.measureText(teks) <= lebar) break;
            sp -= 0.5f;
        }
        return sp;
    }

    private static void pasUkuran(TextView tv, float spMulai, float spMin) {
        tv.post(() -> tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ukuranPas(tv, spMulai, spMin)));
    }

    /** Empat tombol menu cepat memakai ukuran bersama (yang paling sempit), supaya simetris. */
    private void pasUkuranMenuCepat() {
        Button[] tombol = {
            findViewById(R.id.btn_mutasi), findViewById(R.id.btn_aktivitas),
            findViewById(R.id.btn_cashflow), findViewById(R.id.btn_export)
        };
        tombol[0].post(() -> {
            float sp = 13f;
            for (Button b : tombol) sp = Math.min(sp, ukuranPas(b, 13f, 9f));
            for (Button b : tombol) b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        });
    }

    private void openForm(String jenis) {
        Intent i = new Intent(this, FormActivity.class);
        i.putExtra("jenis", jenis);
        startActivity(i);
    }

    private void setupBarAkun() {
        Session s = Session.ambil(this);
        TextView akun = findViewById(R.id.akun);
        Button btnAuth = findViewById(R.id.btn_auth);
        Button btnSync = findViewById(R.id.btn_sync);
        if (s.masuk()) {
            akun.setText("☁ " + (s.email == null ? "Akun Google" : s.email));
            btnAuth.setText("Keluar");
            btnSync.setVisibility(View.VISIBLE);
        } else {
            akun.setText("📱 Mode lokal (belum tersimpan di awan)");
            btnAuth.setText("Masuk");
            btnSync.setVisibility(View.GONE);
        }
        btnAuth.setOnClickListener(v -> {
            if (Session.ambil(this).masuk()) {
                Session.ambil(this).hapus();
                Session.setModeLokal(this, false);
                GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                Toast.makeText(this, "Sampai jumpa! Kamu sudah keluar.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                Session.setModeLokal(this, false);
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });
        btnSync.setOnClickListener(v -> jalankanSinkron(false));
    }

    private void jalankanSinkron(boolean senyap) {
        if (sinkronBerjalan) return;
        sinkronBerjalan = true;
        SyncEngine.sinkron(this, new SyncEngine.Selesai() {
            public void ok(String ringkasan) {
                sinkronBerjalan = false;
                if (!senyap) Toast.makeText(MainActivity.this, "☁ " + ringkasan, Toast.LENGTH_LONG).show();
                segarkan();
            }
            public void gagal(String pesan) {
                sinkronBerjalan = false;
                if (!senyap) Toast.makeText(MainActivity.this, "Sinkron gagal: " + LoginActivity.potong(pesan), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void segarkan() {
        if (db == null) return;
        long masukAll = db.sum("masuk", null), keluarAll = db.sum("keluar", null);
        saldoNilai = masukAll - keluarAll;
        tampilkanSaldo();
        String ym = Util.ymNow();
        ((TextView) findViewById(R.id.bulan_masuk)).setText("▼ Masuk\n" + Util.rp(db.sum("masuk", ym)));
        ((TextView) findViewById(R.id.bulan_keluar)).setText("▲ Keluar\n" + Util.rp(db.sum("keluar", ym)));
        items = db.all();
        findViewById(R.id.empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    /** Saldo tampil angka asli, atau bintang-bintang bila disembunyikan. */
    private void tampilkanSaldo() {
        TextView tvSaldo = findViewById(R.id.saldo);
        tvSaldo.setText(saldoTampil ? Util.rp(saldoNilai) : "**********");
        // Ukuran huruf DIUKUR dari lebar asli kolom di layar pengguna: mulai 42sp,
        // turunkan sampai teksnya benar-benar muat satu baris (tidak patah, tidak terpotong).
        pasUkuran(tvSaldo, 42f, 18f);
        ((android.widget.ImageButton) findViewById(R.id.btn_mata))
            .setImageResource(saldoTampil ? R.drawable.ic_eye : R.drawable.ic_eye_off);
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) segarkan();
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        ExportUtil.selesaiSimpan(this, req, result, data);
    }

    private class TxAdapter extends BaseAdapter {
        public int getCount() { return items == null ? 0 : items.size(); }
        public Object getItem(int p) { return items.get(p); }
        public long getItemId(int p) { return items.get(p).id; }
        public View getView(int p, View convert, ViewGroup parent) {
            if (convert == null) convert = getLayoutInflater().inflate(R.layout.item_tx, parent, false);
            DbHelper.Tx t = items.get(p);
            boolean masuk = "masuk".equals(t.jenis);
            TextView ic = convert.findViewById(R.id.ic);
            ic.setText(masuk ? "▼" : "▲");
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL); g.setColor(masuk ? 0xFF1F9D63 : 0xFFD64541);
            ic.setBackground(g);
            ((TextView) convert.findViewById(R.id.kat)).setText(t.kategori);
            String sub = Util.tglCantik(t.tanggal);
            if (t.catatan != null && !t.catatan.trim().isEmpty()) sub += " • " + t.catatan;
            ((TextView) convert.findViewById(R.id.sub)).setText(sub);
            TextView amt = convert.findViewById(R.id.amt);
            amt.setText((masuk ? "+ " : "- ") + Util.rp(t.jumlah));
            amt.setTextColor(masuk ? 0xFF1F9D63 : 0xFFD64541);
            return convert;
        }
    }
}
