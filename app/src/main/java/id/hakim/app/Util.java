package id.hakim.app;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class Util {
    public static String rp(long n) {
        String s = NumberFormat.getInstance(new Locale("id", "ID")).format(Math.abs(n));
        return (n < 0 ? "-Rp " : "Rp ") + s;
    }
    public static String ymNow() { return new SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().getTime()); }
    public static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()); }
    public static String bulanNama(String ym) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(ym.substring(0, 4)), Integer.parseInt(ym.substring(5, 7)) - 1, 1);
            return new SimpleDateFormat("MMMM yyyy", new Locale("id", "ID")).format(cal.getTime());
        } catch (Exception e) { return ym; }
    }
    public static String geserBulan(String ym, int delta) {
        Calendar cal = Calendar.getInstance();
        cal.set(Integer.parseInt(ym.substring(0, 4)), Integer.parseInt(ym.substring(5, 7)) - 1, 1);
        cal.add(Calendar.MONTH, delta);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
    }
    public static String tglCantik(String ymd) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)));
            return new SimpleDateFormat("EEE, d MMM yyyy", new Locale("id", "ID")).format(cal.getTime());
        } catch (Exception e) { return ymd; }
    }
}
