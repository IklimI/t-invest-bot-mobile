package com.tinvestbot.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureTokenStore {
    private static final String PREFS = "secure_prefs";
    private static final String KEY_ALIAS = "tinvest_readonly_token_key_v1";
    private static final String TOKEN_CIPHER = "token_cipher";
    private static final String TOKEN_IV = "token_iv";

    private final SharedPreferences prefs;

    public SecureTokenStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String token) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(TOKEN_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    public String load() throws Exception {
        String cipherText = prefs.getString(TOKEN_CIPHER, null);
        String ivText = prefs.getString(TOKEN_IV, null);
        if (cipherText == null || ivText == null) return null;

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.decode(ivText, Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
        return new String(plain, StandardCharsets.UTF_8);
    }

    public void clear() {
        prefs.edit().remove(TOKEN_CIPHER).remove(TOKEN_IV).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
