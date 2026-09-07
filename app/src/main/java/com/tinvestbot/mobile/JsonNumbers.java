package com.tinvestbot.mobile;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class JsonNumbers {
    private JsonNumbers() {}

    public static BigDecimal decimal(JSONObject value) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal units = new BigDecimal(value.optString("units", "0"));
        BigDecimal nano = new BigDecimal(value.optString("nano", "0"));
        return units.add(nano.movePointLeft(9));
    }

    public static String money(JSONObject money) {
        if (money == null) return "—";
        BigDecimal amount = decimal(money);
        String currency = money.optString("currency", "RUB");
        String symbol;
        switch (currency) {
            case "RUB": symbol = " ₽"; break;
            case "USD": symbol = " $"; break;
            case "EUR": symbol = " €"; break;
            default: symbol = " " + currency;
        }
        return format(amount, 2) + symbol;
    }

    public static String quantity(JSONObject quotation) {
        BigDecimal q = decimal(quotation);
        return format(q, q.stripTrailingZeros().scale() > 2 ? 4 : 2);
    }

    public static String percent(JSONObject quotation) {
        return format(decimal(quotation), 2) + "%";
    }

    public static String format(BigDecimal value, int maxFraction) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("ru", "RU"));
        nf.setMaximumFractionDigits(maxFraction);
        nf.setMinimumFractionDigits(0);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(value);
    }
}
