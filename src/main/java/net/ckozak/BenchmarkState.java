package net.ckozak;

import com.google.common.collect.ImmutableList;
import io.undertow.Undertow;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;

import java.security.KeyStore;

@State(Scope.Benchmark)
public class BenchmarkState {
    public static final int PORT = 12345;

    private Undertow undertow;
    private OkHttpClient okHttpClient;

    public enum Wrapper implements HandlerWrapper {
        BUFFERED() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new RequestBufferingHandler(handler, 1);
            }
        },
        NOT_BUFFERED() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return handler;
            }
        }
    }

    @Param({"NOT_BUFFERED", "BUFFERED"})
    private Wrapper wrapper;

    @Setup
    public void before() throws Exception {
        setupClient();
        setupServer();
    }

    private void setupClient() throws Exception {
        KeyStore truststore = SSLUtil.loadKeyStore("client.truststore");
        okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(
                        SSLUtil.createSSLContext(null, truststore).getSocketFactory(),
                        SSLUtil.getX509TrustManager(truststore))
                .protocols(ImmutableList.of(Protocol.HTTP_1_1))
                .hostnameVerifier((name, session) -> true)
                .build();
    }

    private void setupServer() throws Exception {
        DeploymentInfo deployment = Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath("/")
                .setDeploymentName("root")
                .addServlet(Servlets.servlet("jersey", ServletContainer.class, new ImmediateInstanceFactory<>(new ServletContainer(new ResourceConfig()
                        .register(new UndertowBenchmark.BenchmarkResource())
                        .register(JacksonFeature.class)
                ))).addMapping("/api/*"));
        DeploymentManager deploymentManager = Servlets.newContainer().addDeployment(deployment);
        deploymentManager.deploy();
        HttpHandler handler = deploymentManager.start();
        handler = wrapper.wrap(handler);
        undertow = Undertow.builder()
                .setWorkerThreads(100)
                .setIoThreads(7)
                .setByteBufferPool(new DefaultByteBufferPool(true, 16 * 1024 - 20, 1024, 6))
                .setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.NOT_REQUESTED)
                .setHandler(handler)
                .addHttpsListener(PORT, "0.0.0.0", SSLUtil.createSSLContext(
                        SSLUtil.loadKeyStore("server.keystore"),
                        SSLUtil.loadKeyStore("server.truststore")))
                .build();
        undertow.start();
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    @TearDown
    public void after() {
        undertow.stop();
    }
}
