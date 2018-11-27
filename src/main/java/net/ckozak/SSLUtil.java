package net.ckozak;

import org.xnio.IoUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;

public final class SSLUtil {

    private static final char[] STORE_PASSWORD = "password".toCharArray();

    private SSLUtil() {}

    public static KeyStore loadKeyStore(final String name) throws IOException {
        final InputStream stream = SSLUtil.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new RuntimeException("Could not load keystore");
        }
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);

            return loadedKeystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }

    public static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws IOException {
        KeyManager[] keyManagers = null;
        if (keyStore != null) {
            try {
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, STORE_PASSWORD);
                keyManagers = keyManagerFactory.getKeyManagers();
            } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
                throw new IOException("Unable to initialise KeyManager[]", e);
            }
        }

        TrustManager[] trustManagers = getTrustManagers(trustStore);

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustManagers, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        }
        return sslContext;
    }


    public static TrustManager[] getTrustManagers(final KeyStore trustStore) throws IOException {
        TrustManager[] trustManagers = null;
        if (trustStore != null) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                throw new IOException("Unable to initialise TrustManager[]", e);
            }
        }
        return trustManagers;
    }

    public static X509TrustManager getX509TrustManager(final KeyStore trustStore) throws IOException {
        return Arrays.stream(getTrustManagers(trustStore))
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Failed to load an X509TrustManager"));
    }
}
