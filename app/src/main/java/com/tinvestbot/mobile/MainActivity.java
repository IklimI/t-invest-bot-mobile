package com.tinvestbot.mobile;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecureTokenStore tokenStore;
    private EditText tokenInput;
    private Button connectButton;
    private Button clearButton;
    private Button refreshButton;
    private Spinner accountSpinner;
    private ProgressBar progress;
    private TextView statusText;
    private LinearLayout portfolioContainer;
    private final List<AccountItem> accounts = new ArrayList<>();
    private String activeToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tokenStore = new SecureTokenStore(this);
        buildUi();
        restoreTokenAndConnect();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(32));
        scroll.addView(root);

        TextView title = text("T-Invest Bot", 28, true);
        root.addView(title);
        TextView subtitle = text("v0.1 · только чтение · реальные сделки отключены", 14, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        TextView warning = text("Безопасный режим: в приложении нет методов выставления заявок. Используйте только read-only токен.", 14, true);
        warning.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(warning);

        tokenInput = new EditText(this);
        tokenInput.setHint("Read-only токен T-Invest API");
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(14);
        root.addView(tokenInput, inputParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(8), 0, 0);
        connectButton = new Button(this);
        connectButton.setText("Подключить");
        clearButton = new Button(this);
        clearButton.setText("Удалить токен");
        buttons.addView(connectButton, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(clearButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-2, -2);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(10);
        root.addView(progress, progressParams);

        statusText = text("Не подключено", 14, false);
        statusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusText);

        accountSpinner = new Spinner(this);
        accountSpinner.setVisibility(View.GONE);
        root.addView(accountSpinner);

        refreshButton = new Button(this);
        refreshButton.setText("Обновить портфель");
        refreshButton.setVisibility(View.GONE);
        root.addView(refreshButton);

        portfolioContainer = new LinearLayout(this);
        portfolioContainer.setOrientation(LinearLayout.VERTICAL);
        portfolioContainer.setPadding(0, dp(10), 0, 0);
        root.addView(portfolioContainer);

        connectButton.setOnClickListener(v -> connectWithEnteredToken());
        clearButton.setOnClickListener(v -> clearToken());
        refreshButton.setOnClickListener(v -> loadSelectedPortfolio());
        accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!accounts.isEmpty()) loadPortfolio(accounts.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setContentView(scroll);
    }

    private void restoreTokenAndConnect() {
        executor.execute(() -> {
            try {
                String saved = tokenStore.load();
                if (saved != null && !saved.trim().isEmpty()) {
                    activeToken = saved;
                    runOnUiThread(() -> {
                        tokenInput.setText("••••••••••••••••");
                        statusText.setText("Найден сохранённый токен. Подключаюсь…");
                    });
                    loadAccounts(saved);
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Не удалось прочитать сохранённый токен."));
            }
        });
    }

    private void connectWithEnteredToken() {
        String entered = tokenInput.getText().toString().trim();
        if (entered.isEmpty() || entered.contains("•")) {
            if (activeToken != null) loadAccounts(activeToken);
            else toast("Введите read-only токен.");
            return;
        }
        setLoading(true, "Проверяю подключение…");
        executor.execute(() -> {
            try {
                tokenStore.save(entered);
                activeToken = entered;
                loadAccountsInternal(entered);
            } catch (Exception e) {
                showError("Не удалось сохранить токен: " + e.getMessage());
            }
        });
    }

    private void loadAccounts(String token) {
        setLoading(true, "Загружаю счета…");
        executor.execute(() -> loadAccountsInternal(token));
    }

    private void loadAccountsInternal(String token) {
        try {
            JSONObject response = new TInvestClient(token).getAccounts();
            JSONArray jsonAccounts = response.optJSONArray("accounts");
            List<AccountItem> loaded = new ArrayList<>();
            if (jsonAccounts != null) {
                for (int i = 0; i < jsonAccounts.length(); i++) {
                    JSONObject a = jsonAccounts.getJSONObject(i);
                    String id = a.optString("id", "");
                    if (id.trim().isEmpty()) continue;
                    String name = a.optString("name", "Брокерский счёт");
                    String type = a.optString("type", "");
                    loaded.add(new AccountItem(id, name, type));
                }
            }
            runOnUiThread(() -> showAccounts(loaded));
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showAccounts(List<AccountItem> loaded) {
        setLoading(false, loaded.isEmpty() ? "Открытые счета не найдены." : "Подключено. Счетов: " + loaded.size());
        accounts.clear();
        accounts.addAll(loaded);
        if (loaded.isEmpty()) {
            accountSpinner.setVisibility(View.GONE);
            refreshButton.setVisibility(View.GONE);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (AccountItem item : loaded) labels.add(item.label());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        accountSpinner.setAdapter(adapter);
        accountSpinner.setVisibility(View.VISIBLE);
        refreshButton.setVisibility(View.VISIBLE);
    }

    private void loadSelectedPortfolio() {
        int index = accountSpinner.getSelectedItemPosition();
        if (index >= 0 && index < accounts.size()) loadPortfolio(accounts.get(index));
    }

    private void loadPortfolio(AccountItem account) {
        if (activeToken == null) return;
        setLoading(true, "Загружаю портфель…");
        executor.execute(() -> {
            try {
                JSONObject response = new TInvestClient(activeToken).getPortfolio(account.id);
                runOnUiThread(() -> renderPortfolio(account, response));
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void renderPortfolio(AccountItem account, JSONObject p) {
        setLoading(false, "Данные обновлены");
        portfolioContainer.removeAllViews();

        portfolioContainer.addView(sectionTitle(account.name));
        JSONObject total = p.optJSONObject("totalAmountPortfolio");
        JSONObject shares = p.optJSONObject("totalAmountShares");
        JSONObject bonds = p.optJSONObject("totalAmountBonds");
        JSONObject currencies = p.optJSONObject("totalAmountCurrencies");
        JSONObject dailyYield = p.optJSONObject("dailyYield");
        JSONObject expectedYield = p.optJSONObject("expectedYield");
        JSONObject dailyYieldRelative = p.optJSONObject("dailyYieldRelative");

        addMetric("Стоимость портфеля", JsonNumbers.money(total));
        addMetric("Акции", JsonNumbers.money(shares));
        addMetric("Облигации", JsonNumbers.money(bonds));
        addMetric("Деньги / валюты", JsonNumbers.money(currencies));
        addMetric("Доходность портфеля", expectedYield == null ? "—" : JsonNumbers.percent(expectedYield));
        addMetric("Результат за день", dailyYield == null ? "—" : JsonNumbers.money(dailyYield));
        addMetric("За день, %", dailyYieldRelative == null ? "—" : JsonNumbers.percent(dailyYieldRelative));

        JSONArray positions = p.optJSONArray("positions");
        int count = positions == null ? 0 : positions.length();
        portfolioContainer.addView(sectionTitle("Позиции · " + count));

        BigDecimal totalValue = JsonNumbers.decimal(total);
        if (positions == null || positions.length() == 0) {
            portfolioContainer.addView(text("В портфеле нет позиций.", 15, false));
            return;
        }

        for (int i = 0; i < positions.length(); i++) {
            JSONObject pos = positions.optJSONObject(i);
            if (pos != null) portfolioContainer.addView(positionCard(pos, totalValue));
        }

        portfolioContainer.addView(sectionTitle("Автоаналитика риска"));
        portfolioContainer.addView(text(buildRiskSummary(positions, totalValue), 14, false));
    }

    private View positionCard(JSONObject pos, BigDecimal portfolioTotal) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFFE0E0E0);
        card.setBackground(bg);

        String ticker = pos.optString("ticker", "");
        if (ticker.trim().isEmpty()) ticker = pos.optString("figi", "Инструмент");
        String type = prettyType(pos.optString("instrumentType", ""));
        card.addView(text(ticker + (type.trim().isEmpty() ? "" : " · " + type), 18, true));

        JSONObject q = pos.optJSONObject("quantity");
        JSONObject price = pos.optJSONObject("currentPrice");
        JSONObject avg = pos.optJSONObject("averagePositionPrice");
        JSONObject pnl = pos.optJSONObject("expectedYield");

        addLine(card, "Количество", q == null ? "—" : JsonNumbers.quantity(q));
        addLine(card, "Текущая цена", JsonNumbers.money(price));
        addLine(card, "Средняя цена", JsonNumbers.money(avg));
        addLine(card, "Результат позиции", pnl == null ? "—" : JsonNumbers.format(JsonNumbers.decimal(pnl), 2));

        if (q != null && price != null && portfolioTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal value = JsonNumbers.decimal(q).multiply(JsonNumbers.decimal(price));
            BigDecimal weight = value.multiply(BigDecimal.valueOf(100)).divide(portfolioTotal, 2, java.math.RoundingMode.HALF_UP);
            addLine(card, "Оценка позиции", JsonNumbers.format(value, 2) + " ₽");
            addLine(card, "Доля портфеля", JsonNumbers.format(weight, 2) + "%");
        }
        return card;
    }

    private String buildRiskSummary(JSONArray positions, BigDecimal totalValue) {
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) return "Недостаточно данных для расчёта концентрации.";
        StringBuilder warnings = new StringBuilder();
        int concentrated = 0;
        for (int i = 0; i < positions.length(); i++) {
            JSONObject p = positions.optJSONObject(i);
            if (p == null) continue;
            JSONObject q = p.optJSONObject("quantity");
            JSONObject price = p.optJSONObject("currentPrice");
            if (q == null || price == null) continue;
            BigDecimal value = JsonNumbers.decimal(q).multiply(JsonNumbers.decimal(price));
            BigDecimal weight = value.multiply(BigDecimal.valueOf(100)).divide(totalValue, 2, java.math.RoundingMode.HALF_UP);
            if (weight.compareTo(BigDecimal.valueOf(20)) >= 0) {
                concentrated++;
                String ticker = p.optString("ticker", p.optString("figi", "позиция"));
                warnings.append("• ").append(ticker).append(": около ").append(JsonNumbers.format(weight, 1)).append("% портфеля — повышенная концентрация.\n");
            }
        }
        if (concentrated == 0) {
            warnings.append("Крупных позиций ≥20% по доступным данным не обнаружено.\n");
        }
        warnings.append("Это технический индикатор концентрации, а не рекомендация купить или продать.");
        return warnings.toString();
    }

    private void clearToken() {
        tokenStore.clear();
        activeToken = null;
        tokenInput.setText("");
        accounts.clear();
        portfolioContainer.removeAllViews();
        accountSpinner.setVisibility(View.GONE);
        refreshButton.setVisibility(View.GONE);
        statusText.setText("Токен удалён с телефона.");
    }

    private void setLoading(boolean loading, String status) {
        runOnUiThread(() -> {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            connectButton.setEnabled(!loading);
            refreshButton.setEnabled(!loading);
            if (status != null) statusText.setText(status);
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            setLoading(false, "Ошибка подключения");
            String safe = message == null ? "Неизвестная ошибка" : message;
            portfolioContainer.removeAllViews();
            TextView error = text(safe, 14, false);
            error.setPadding(dp(12), dp(12), dp(12), dp(12));
            portfolioContainer.addView(error);
        });
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 20, true);
        t.setPadding(0, dp(16), 0, dp(8));
        return t;
    }

    private void addMetric(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = text(label, 15, false);
        TextView v = text(value, 15, true);
        v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        row.setPadding(0, dp(4), 0, dp(4));
        portfolioContainer.addView(row);
    }

    private void addLine(LinearLayout parent, String label, String value) {
        TextView line = text(label + ": " + value, 14, false);
        line.setPadding(0, dp(3), 0, 0);
        parent.addView(line);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private String prettyType(String type) {
        switch (type.toLowerCase()) {
            case "share": return "акция";
            case "bond": return "облигация";
            case "etf": return "фонд";
            case "currency": return "валюта";
            case "future": return "фьючерс";
            case "option": return "опцион";
            default: return type;
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static final class AccountItem {
        final String id;
        final String name;
        final String type;
        AccountItem(String id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
        String label() {
            String shortId = id.length() > 6 ? id.substring(id.length() - 6) : id;
            return name + " · …" + shortId;
        }
    }
}
