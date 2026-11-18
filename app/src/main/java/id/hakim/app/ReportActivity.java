package id.hakim.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cash Flow ala Catatan Finansial myBCA: donat per kategori + legenda + export. */
public class ReportActivity extends Activity {
    static final int[] PALET = {
        0xFF1FA463, 0xFFF2B705, 0xFF8E44AD, 0xFF0070E0, 0xFFFF7A45,
        0xFF00A6A6, 0xFFD64541, 0xFF5B7186, 0xFF7AC74F, 0xFF3E7CB1
    };
    private DbHelper db;
    private String ym = Util.ymNow();
    private String tab = "keluar";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        NavBawah.pasang(this, "cashflow");
        db = new DbHelper(this);
        findViewById(R.id.prev).setOnClickListener(v -> { ym = Util.geserBulan(ym, -1); render(); });
        findViewById(R.id.next).setOnClickListener(v -> { ym = Util.geserBulan(ym, 1); render(); });
        ((Button) findViewById(R.id.tab_keluar)).setOnClickListener(v -> { tab = "keluar"; render(); });
        ((Button) findViewById(R.id.tab_masuk)).setOnClickListener(v -> { tab = "masuk"; render(); });
        findViewById(R.id.btn_export).setOnClickListener(v -> {
            byte[] pdf = ExportUtil.buatPdf(db.byMonth(ym), "Bulan " + Util.bulanNama(ym));
            ExportUtil.mulaiSimpan(this, "cashflow-" + ym + ".pdf", pdf);
        });
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.r_bulan)).setText(Util.bulanNama(ym));
        long masuk = db.sum("masuk", ym), keluar = db.sum("keluar", ym);
        ((TextView) findViewById(R.id.r_ringkas)).setText(
            "Masuk " + Util.rp(masuk) + "\nKeluar " + Util.rp(keluar) + "\nSelisih " + Util.rp(masuk - keluar));
        gayaTab(findViewById(R.id.tab_keluar), "keluar".equals(tab));
        gayaTab(findViewById(R.id.tab_masuk), "masuk".equals(tab));
        renderToko();

        // Agregasi per kategori untuk tab aktif, urut terbesar dulu
        Map<String, Long> per = new LinkedHashMap<>();
        for (DbHelper.Tx t : db.byMonth(ym)) if (tab.equals(t.jenis)) per.merge(t.kategori, t.jumlah, Long::sum);
        List<Map.Entry<String, Long>> urut = new ArrayList<>(per.entrySet());
        urut.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        long total = "keluar".equals(tab) ? keluar : masuk;

        ((TextView) findViewById(R.id.donut_label)).setText("keluar".equals(tab) ? "Total Pengeluaran" : "Total Pemasukan");
        ((TextView) findViewById(R.id.donut_total)).setText(Util.rp(total));

        List<Long> vals = new ArrayList<>();
        for (Map.Entry<String, Long> e : urut) vals.add(e.getValue());
        ((DonutView) findViewById(R.id.donut)).setData(vals, PALET);

        LinearLayout legenda = findViewById(R.id.r_legenda);
        legenda.removeAllViews();
        if (urut.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Tidak ada data bulan ini.");
            tv.setTextColor(0xFF5B7186);
            tv.setGravity(Gravity.CENTER);
            legenda.addView(tv);
            return;
        }
        int i = 0;
        for (Map.Entry<String, Long> e : urut) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 7, 0, 7);
            View titik = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL); g.setColor(PALET[i % PALET.length]);
            titik.setBackground(g);
            titik.setLayoutParams(new LinearLayout.LayoutParams(18, 18));
            row.addView(titik);
            TextView nama = new TextView(this);
            nama.setText("  " + e.getKey());
            nama.setTextColor(0xFF16324F);
            nama.setTextSize(14.5f);
            nama.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(nama);
            TextView nilai = new TextView(this);
            long persen = total > 0 ? Math.round(e.getValue() * 100.0 / total) : 0;
            nilai.setText(Util.rp(e.getValue()) + "  (" + persen + "%)");
            nilai.setTextColor(0xFF16324F);
            nilai.setTextSize(14.5f);
            nilai.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(nilai);
            legenda.addView(row);
            i++;
        }
    }

    static final String[] TOKO = {"Toko 1", "Toko 2", "Toko 3"};
    static final int[] TOKO_WARNA = {0xFF0091C8, 0xFF1FA463, 0xFFFF7A45};

    /** Bagian khusus: total pemasukan masing-masing toko untuk bulan yang sedang dipilih (ym). */
    private void renderToko() {
        ((TextView) findViewById(R.id.r_toko_bulan)).setText("Bulan " + Util.bulanNama(ym));
        Map<String, Long> total = new LinkedHashMap<>();
        Map<String, Integer> hitung = new LinkedHashMap<>();
        for (String t : TOKO) { total.put(t, 0L); hitung.put(t, 0); }

        // Rangkaian harian untuk grafik garis: total pemasukan per hari, per toko
        int hari = 28;
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(Integer.parseInt(ym.substring(0, 4)), Integer.parseInt(ym.substring(5, 7)) - 1, 1);
            hari = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        } catch (Exception ignored) {}
        long[][] harian = new long[TOKO.length][hari];
        for (DbHelper.Tx t : db.byMonth(ym)) {
            if ("masuk".equals(t.jenis) && total.containsKey(t.kategori)) {
                total.merge(t.kategori, t.jumlah, Long::sum);
                hitung.merge(t.kategori, 1, Integer::sum);
                int idx = java.util.Arrays.asList(TOKO).indexOf(t.kategori);
                try {
                    int tgl = Integer.parseInt(t.tanggal.substring(8, 10));
                    if (idx >= 0 && tgl >= 1 && tgl <= hari) harian[idx][tgl - 1] += t.jumlah;
                } catch (Exception ignored) {}
            }
        }
        List<long[]> seri = new ArrayList<>();
        for (long[] h : harian) seri.add(h);
        ((LineChartView) findViewById(R.id.r_grafik_toko)).setData(seri, TOKO_WARNA);

        // Legenda warna grafik
        LinearLayout legendaToko = findViewById(R.id.r_toko_legenda);
        legendaToko.removeAllViews();
        for (int i = 0; i < TOKO.length; i++) {
            View titik = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL); g.setColor(TOKO_WARNA[i]);
            titik.setBackground(g);
            titik.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
            legendaToko.addView(titik);
            TextView nama = new TextView(this);
            nama.setText(" " + TOKO[i] + "   ");
            nama.setTextColor(0xFF16324F);
            nama.setTextSize(12.5f);
            legendaToko.addView(nama);
        }
        long semua = 0;
        for (long v : total.values()) semua += v;

        LinearLayout wadah = findViewById(R.id.r_toko);
        wadah.removeAllViews();
        for (String t : TOKO) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 7, 0, 7);
            TextView nama = new TextView(this);
            int n = hitung.get(t);
            nama.setText(t + (n > 0 ? "  (" + n + " transaksi)" : ""));
            nama.setTextColor(0xFF16324F);
            nama.setTextSize(14.5f);
            nama.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(nama);
            TextView nilai = new TextView(this);
            long persen = semua > 0 ? Math.round(total.get(t) * 100.0 / semua) : 0;
            nilai.setText(Util.rp(total.get(t)) + (semua > 0 ? "  (" + persen + "%)" : ""));
            nilai.setTextColor(0xFF16324F);
            nilai.setTextSize(14.5f);
            nilai.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(nilai);
            wadah.addView(row);
        }
        TextView totalTv = new TextView(this);
        totalTv.setText("Total semua toko: " + Util.rp(semua));
        totalTv.setTextColor(0xFF0B2239);
        totalTv.setTextSize(14.5f);
        totalTv.setTypeface(null, android.graphics.Typeface.BOLD);
        totalTv.setPadding(0, 8, 0, 0);
        wadah.addView(totalTv);
    }

    private void gayaTab(Button b, boolean aktif) {
        b.setBackgroundResource(aktif ? R.drawable.bg_tombol_biru : R.drawable.bg_tombol_putih);
        b.setTextColor(aktif ? 0xFFFFFFFF : 0xFF0070E0);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        ExportUtil.selesaiSimpan(this, req, result, data);
    }
}
