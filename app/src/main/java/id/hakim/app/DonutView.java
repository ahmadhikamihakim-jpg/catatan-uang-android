package id.hakim.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Diagram donat (cincin) ala Catatan Finansial myBCA. */
public class DonutView extends View {
    private final List<Float> nilai = new ArrayList<>();
    private final List<Integer> warna = new ArrayList<>();
    private final Paint cat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF kotak = new RectF();

    public DonutView(Context c) { super(c); }
    public DonutView(Context c, AttributeSet a) { super(c, a); }

    public void setData(List<Long> vals, int[] colors) {
        nilai.clear(); warna.clear();
        double total = 0; for (long v : vals) total += v;
        for (int i = 0; i < vals.size(); i++) {
            nilai.add(total > 0 ? (float) (vals.get(i) * 360.0 / total) : 0f);
            warna.add(colors[i % colors.length]);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sisi = Math.min(getWidth(), getHeight());
        float tebal = sisi * 0.16f;
        float pad = tebal / 2f + 4f;
        kotak.set(pad, pad, sisi - pad, sisi - pad);
        cat.setStyle(Paint.Style.STROKE);
        cat.setStrokeWidth(tebal);
        cat.setStrokeCap(Paint.Cap.BUTT);
        if (nilai.isEmpty()) {
            cat.setColor(0xFFD8E4F0);
            canvas.drawArc(kotak, 0, 360, false, cat);
            return;
        }
        float mulai = -90f;
        for (int i = 0; i < nilai.size(); i++) {
            float sapuan = nilai.get(i);
            if (sapuan <= 0) continue;
            cat.setColor(warna.get(i));
            // celah kecil antar ruas
            canvas.drawArc(kotak, mulai + 0.7f, Math.max(sapuan - 1.4f, 0.5f), false, cat);
            mulai += sapuan;
        }
    }
}
