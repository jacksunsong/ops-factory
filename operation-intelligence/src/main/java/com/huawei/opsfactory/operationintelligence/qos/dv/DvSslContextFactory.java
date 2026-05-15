/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Dv Ssl Context Factory.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class DvSslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(DvSslContextFactory.class);

    private final ConcurrentHashMap<String, SslContext> sslContextCache = new ConcurrentHashMap<>();

    /**
     * create Ssl Context.
     *
     * @param crtContent the crtContent
     * @param fileName the fileName
     * @param strictSsl the strictSsl
     * @return the result
     */
    public SslContext createSslContext(String crtContent, String fileName, boolean strictSsl) {
        if (crtContent == null || crtContent.isBlank()) {
            if (strictSsl) {
                throw new IllegalStateException("No SSL certificate configured and strict-ssl is enabled");
            }
            log.warn("INSECURE SSL: no certificate configured, falling back to insecure trust manager");
            return createInsecureSslContext();
        }

        return sslContextCache.computeIfAbsent(crtContent, k -> doCreateSslContext(k, fileName, strictSsl));
    }

    /**
     * create Ssl Context.
     *
     * @param crtContent the crtContent
     * @param fileName the fileName
     * @return the result
     */
    public SslContext createSslContext(String crtContent, String fileName) {
        return createSslContext(crtContent, fileName, true);
    }

    private SslContext doCreateSslContext(String crtContent, String fileName, boolean strictSsl) {
        try {
            byte[] certBytes = java.util.Base64.getDecoder().decode(crtContent);
            String type = (fileName != null && fileName.endsWith(".p12")) ? "PKCS12" : "JKS";
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream is = new ByteArrayInputStream(certBytes)) {
                keyStore.load(is, new char[0]);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

            return SslContextBuilder.forClient().sslContextProvider(null).trustManager(tmf).keyManager(kmf).build();
        } catch (Exception e) {
            if (strictSsl) {
                throw new RuntimeException("Failed to create SSL context with certificate (strict-ssl enabled)", e);
            }
            log.error(
                "INSECURE SSL: SSL context creation failed, falling back to insecure trust manager. "
                    + "This is a security risk. Set strict-ssl=true to enforce certificate validation. Error: {}",
                e.getMessage());
            return createInsecureSslContext();
        }
    }

    /**
     * create Insecure Ssl Context.
     *
     * @return the result
     */
    public SslContext createInsecureSslContext() {
        try {
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}
