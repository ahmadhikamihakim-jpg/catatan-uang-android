package id.hakim.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Grafik garis sederhana (Canvas): beberapa seri nilai harian dalam satu bulan.
 * Meniru gaya grafik Aktivitas myBCA: garis berwarna dengan arsiran tipis
 * di bawahnya, garis bantu mendatar, label nilai maksimum dan tanggal di bawah.
 */
public class LineChartView extends View {
    private List<long[]> seri = new ArrayList<>();
    private int[] warna = new int[0];

    private final Paint catGarisBantu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catTeks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catGaris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catIsi = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LineChartView(Context ctx) { super(ctx); init(); }
    public LineChartView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        catGarisBantu.setColor(0xFFD9E4EF);
        catGarisBantu.setStrokeWidth(1f * d);
        catTeks.setColor(0xFF5B7186);
        catTeks.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
        catGaris.setStyle(Paint.Style.STROKE);
        catGaris.setStrokeWidth(2.2f * d);
        catGaris.setStrokeJoin(Paint.Join.ROUND);
        catGaris.setStrokeCap(Paint.Cap.ROUND);
        catIsi.setStyle(Paint.Style.FILL);
    }

    public void setData(List<long[]> data, int[] colors) {
        seri = data == null ? new ArrayList<>() : data;
        warna = colors == null ? new int[0] : colors;
        invalidate();
    }

    static String ringkas(long v) {
        if (v >= 1_000_000L) {
            long jt = v / 100_000L; // satu desimal dalam satuan juta
            return "Rp " + (jt / 10) + "," + (jt % 10) + " jt";
        }
        if (v >= 1_000L) return "Rp " + Math.round(v / 1000.0) + " rb";
        return "Rp " + v;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (seri.isEmpty()) return;
        int hari = seri.get(0).length;
        if (hari == 0) return;

        long maks = 0;
        for (long[] s : seri) for (long v : s) if (v > maks) maks = v;

        float d = getResources().getDisplayMetrics().density;
        float kiri = 6f * d, kanan = getWidth() - 6f * d;
        float atas = 20f * d, bawah = getHeight() - 20f * d;
        float lebar = kanan - kiri, tinggi = bawah - atas;

        // Garis bantu mendatar + label tanggal di bawah
        catTeks.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i <= 3; i++) {
            float y = bawah - tinggi * i / 3f;
            c.drawLine(kiri, y, kanan, y, catGarisBantu);
        }
        int[] labelHari = {1, 8, 15, 22, hari};
        catTeks.setTextAlign(Paint.Align.CENTER);
        for (int h : labelHari) {
            if (h < 1 || h > hari) continue;
            float x = kiri + lebar * (h - 1) / Math.max(1, hari - 1);
            c.drawText(String.valueOf(h), x, getHeight() - 6f * d, catTeks);
        }

        if (maks <= 0) {
            catTeks.setTextAlign(Paint.Align.CENTER);
            c.drawText("Belum ada pemasukan toko bulan ini", getWidth() / 2f, atas + tinggi / 2f, catTeks);
            return;
        }

        // Label nilai maksimum di kiri atas
        catTeks.setTextAlign(Paint.Align.LEFT);
        c.drawText(ringkas(maks), kiri, 13f * d, catTeks);

        double skala = maks * 1.08; // sedikit ruang di atas titik tertinggi
        for (int s = 0; s < seri.size(); s++) {
            long[] data = seri.get(s);
            int col = s < warna.length ? warna[s] : 0xFF0070E0;
            Path jalur = new Path();
            Path arsir = new Path();
            for (int i = 0; i < data.length; i++) {
                float x = kiri + lebar * i / Math.max(1, hari - 1);
                float y = (float) (bawah - tinggi * (data[i] / skala));
                if (i == 0) { jalur.moveTo(x, y); arsir.moveTo(x, bawah); arsir.lineTo(x, y); }
                else { jalur.lineTo(x, y); arsir.lineTo(x, y); }
                if (i == data.length - 1) { arsir.lineTo(x, bawah); arsir.close(); }
            }
            catIsi.setColor((col & 0x00FFFFFF) | 0x24000000);
            c.drawPath(arsir, catIsi);
            catGaris.setColor(col);
            c.drawPath(jalur, catGaris);
        }
    }
}
