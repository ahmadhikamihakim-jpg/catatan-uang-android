package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Mutasi ala myBCA: semua transaksi + pencarian + saringan masuk/keluar + export. */
public class MutasiActivity extends Activity {
    private DbHelper db;
    private List<DbHelper.Tx> items = new ArrayList<>();
    private MutasiAdapter adapter;
    private String jenis = null; // null = semua
    private String kata = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mutasi);
        NavBawah.pasang(this, "mutasi");
        db = new DbHelper(this);
        adapter = new MutasiAdapter();
        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = new Intent(this, FormActivity.class);
            i.putExtra("id", items.get(pos).id);
            startActivity(i);
        });
        EditText cari = findViewById(R.id.cari);
        cari.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { kata = s.toString(); segarkan(); }
        });
        aturSaringan(findViewById(R.id.f_semua), null);
        aturSaringan(findViewById(R.id.f_masuk), "masuk");
        aturSaringan(findViewById(R.id.f_keluar), "keluar");
        findViewById(R.id.btn_export).setOnClickListener(v -> {
            String judul = "Mutasi" + (jenis == null ? " Semua" : ("masuk".equals(jenis) ? " Masuk" : " Keluar"))
                + (kata.trim().isEmpty() ? "" : " — \"" + kata.trim() + "\"");
            ExportUtil.mulaiSimpan(this, "mutasi.pdf", ExportUtil.buatPdf(items, judul));
        });
        segarkan();
    }

    private void aturSaringan(Button b, String nilai) {
        b.setOnClickListener(v -> { jenis = nilai; segarkan(); });
    }

    private void segarkan() {
        items = db.cari(kata, jenis);
        long masuk = 0, keluar = 0;
        for (DbHelper.Tx t : items) { if ("masuk".equals(t.jenis)) masuk += t.jumlah; else keluar += t.jumlah; }
        ((TextView) findViewById(R.id.ringkas)).setText(
            items.size() + " transaksi • Masuk " + Util.rp(masuk) + " • Keluar " + Util.rp(keluar));
        findViewById(R.id.empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
        tandaiChip();
    }

    private void tandaiChip() {
        gayaChip(findViewById(R.id.f_semua), jenis == null);
        gayaChip(findViewById(R.id.f_masuk), "masuk".equals(jenis));
        gayaChip(findViewById(R.id.f_keluar), "keluar".equals(jenis));
    }

    private void gayaChip(Button b, boolean aktif) {
        b.setBackgroundResource(aktif ? R.drawable.bg_tombol_biru : R.drawable.bg_tombol_putih);
        b.setTextColor(aktif ? 0xFFFFFFFF : 0xFF0070E0);
    }

    @Override protected void onResume() { super.onResume(); segarkan(); }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        ExportUtil.selesaiSimpan(this, req, result, data);
    }

    private class MutasiAdapter extends BaseAdapter {
        public int getCount() { return items.size(); }
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
