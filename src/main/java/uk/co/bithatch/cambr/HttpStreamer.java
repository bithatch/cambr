package uk.co.bithatch.cambr;

import static io.activej.inject.module.Modules.combine;
import static io.activej.launchers.initializers.Initializers.ofEventloop;
import static io.activej.launchers.initializers.Initializers.ofHttpServer;

import org.jetbrains.annotations.NotNull;

import io.activej.bytebuf.ByteBuf;
import io.activej.config.Config;
import io.activej.config.ConfigModule;
import io.activej.csp.ChannelSupplier;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.inspector.ThrottlingController;
import io.activej.http.AsyncHttpServer;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.binding.OptionalDependency;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraphModule;

public class HttpStreamer extends Launcher {
	public static final String PROPERTIES_FILE = "http-server.properties";

	@Inject
	AsyncHttpServer httpServer;

	private @NotNull ChannelSupplier<ByteBuf> stream;
	private int port;
	private boolean localOnly;

	public HttpStreamer(ChannelSupplier<ByteBuf> stream, int port, boolean localOnly) {
		this.stream = stream;
		this.port = port;
		this.localOnly = localOnly;
	}

	@Provides
	Config config() {
		return Config.create().with("http.listenAddresses", (localOnly ? "127.0.0.1" : "0.0.0.0") + ":" + port);
	}

	@Provides
	Eventloop eventloop(Config config, OptionalDependency<ThrottlingController> throttlingController) {
		return Eventloop.create().withInitializer(ofEventloop(config.getChild("eventloop")))
				.withInitializer(eventloop -> eventloop.withInspector(throttlingController.orElse(null)));
	}

	@Provides
	AsyncHttpServer server(Eventloop eventloop, AsyncServlet rootServlet, Config config) {
		return AsyncHttpServer.create(eventloop, rootServlet).withInitializer(ofHttpServer(config.getChild("http")));
	}

	@Override
	protected final Module getModule() {
		return combine(ServiceGraphModule.create(), ConfigModule.create().withEffectiveConfigLogger(),
				getBusinessLogicModule());
	}

	protected Module getBusinessLogicModule() {
		return Module.empty();
	}

	@Override
	protected void run() throws Exception {
		logger.info("HTTP Server is now available at {}", String.join(", ", httpServer.getHttpAddresses()));
		awaitShutdown();
	}

	@Provides
	AsyncServlet servlet() {
		return request -> HttpResponse.ok200().withHeader(HttpHeaders.CONTENT_TYPE,
				"multipart/x-mixed-replace; boundary=--" + CambrController.BOUNDARY).withBodyStream(stream);
	}
}