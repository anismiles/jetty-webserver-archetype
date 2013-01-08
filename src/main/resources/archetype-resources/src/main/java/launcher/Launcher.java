#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
/*
 * Copyright 2013 Strumsoft Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ${package}.launcher;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launcher class to start/stop Jetty Webserver
 * 
 * @author "Animesh Kumar <animesh@strumsoft.com>"
 * 
 */
public class Launcher {
    // Logger
    static final Logger log = LoggerFactory.getLogger(Launcher.class);
    static final PrintStream out = System.out;
    static final PrintStream err = System.err;
    
    static final String NOTIFICATION_PREFIX = "* ";
    static final String REQUEST_PREFIX = "> ";
    static final String RESPONSE_PREFIX = "< ";

    // Conf
    static final String CONF_FILE = "jetty.properties";
    static final String CONF_PARAM = "jetty.configurationFile";

    // Server defaults
    static final String host_default = "127.0.0.1";
    static final String port_default = "8085";
    static final String secret_default = "eb27fb2e61ed603363461b3b4e37e0a0";
    static final String contextPath_default = "/";
    static final String workDirPath_default = null;
    static final String logsEnabled_default = "false";

    final String host;
    final int port;
    final String contextPath;
    final String workDirPath;
    final String secret;

    // Detailed Request/Response Logs
    final boolean logsEnabled;

    public static void main(String[] args) throws Exception {
        String command = (args.length > 0) ? args[0] : "";
        if ("stop".equals(command)) {
            new Launcher().requestStop();
        } else if ("start".equals(command)) {
            new Launcher().startJetty();
        } else {
            out.println("Usage: java -jar <file.jar> [start|stop]${symbol_escape}n${symbol_escape}t" + "start    Start the server${symbol_escape}n${symbol_escape}t"
                    + "stop     Stop the server gracefully${symbol_escape}n");
            System.exit(-1);
        }
    }

    public Launcher() {
        Properties properties = new Properties();
        try {
            ClassLoader loader = Launcher.class.getClassLoader();
            URL url = loader.getResource(CONF_FILE);
            properties.load(url.openStream());

            // from file
            String propFile = System.getProperty(CONF_PARAM);
            if (null != propFile) {
                properties.load(new FileInputStream(propFile));
            }

            // Load system
            properties.putAll(System.getProperties());
        } catch (Exception ignored) {
            // ignored.printStackTrace();
        }

        // Defaults
        host = properties.getProperty("jetty.host", host_default);
        port = Integer.parseInt(properties.getProperty("jetty.port", port_default));
        secret = properties.getProperty("jetty.secret", secret_default);
        contextPath = properties.getProperty("jetty.contextPath", contextPath_default);
        workDirPath = properties.getProperty("jetty.workDir", workDirPath_default);

        // Detailed Logs
        logsEnabled = Boolean.parseBoolean(properties.getProperty(
                "jetty.logs", logsEnabled_default));

        out.println("Jetty Configuration Properties ===>> " + "${symbol_escape}n${symbol_escape}thost=" + host + "${symbol_escape}n${symbol_escape}tport=" + port
                + "${symbol_escape}n${symbol_escape}tcontext=" + contextPath + "${symbol_escape}n${symbol_escape}tsecret=" + secret + "${symbol_escape}n${symbol_escape}twork-dir=" + workDirPath
                + "${symbol_escape}n${symbol_escape}tlogs=" + logsEnabled);
    }

    void startJetty() {
        String warFile;
        File workDir;
        try {
            // Get the war-file
            ProtectionDomain protectionDomain = Launcher.class.getProtectionDomain();
            warFile = protectionDomain.getCodeSource().getLocation().toExternalForm();

            // Work directory
            File warDir = new File(protectionDomain.getCodeSource().getLocation().getPath());
            String currentDir = warDir.getParent();
            workDir = getTempDirectory(currentDir, warDir.getName());
        } catch (IOException exp) {
            err.println("error = " + exp.getMessage());
            exp.printStackTrace();
            return;
        }

        // Server
        final Server server = new Server();
        server.setStopAtShutdown(true);
        // Allow 5 seconds to complete.
        server.setGracefulShutdown(5000);
        // Increase thread pool
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(100);
        server.setThreadPool(threadPool);

        // Ensure using the non-blocking connector (NIO)
        Connector connector = new SelectChannelConnector();
        connector.setHost(host);
        connector.setPort(port);
        connector.setMaxIdleTime(30000);
        server.setConnectors(new Connector[] { connector });

        // Add the warFile (this jar)
        // final WebAppContext context = new ThrowyWebAppContext(warFile, contextPath);
        final WebAppContext context = new WebAppContext(warFile, contextPath) {
            @Override
            protected void doStart() throws Exception {
                super.doStart();
                if (getUnavailableException() != null) {
                    throw (Exception) getUnavailableException();
                }
            }
        };
        
        context.setServer(server);
        context.setTempDirectory(workDir);

        // Handles ping request
        final Date since = new Date();
        final AbstractHandler pingHandler = new AbstractHandler() {
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) throws IOException, ServletException {
                if (!target.equals("/jetty-ping")) {
                    return;
                }
                response.setContentType("text/html;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                response.getWriter().println("since: " + since);
            }
        };

        // Shutdown Handler
        final AbstractHandler shutdownHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) throws IOException, ServletException {
                if (!target.equals("/jetty-shutdown") || !request.getMethod().equals("POST")
                        || !secret.equals(request.getParameter("secret"))) {
                    return;
                }
                response.setContentType("text/html;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                response.getWriter().println("Stopping...");
                stopJetty(server, context);
            }
        };

        try {
            // Handler wrapper
            final HandlerWrapper handlerWrapper = (logsEnabled) ? new DetailedLoggingHandler()
                    : new HandlerWrapper();
            handlerWrapper.setHandler(new HandlerList() {
                {
                    setHandlers(new Handler[] { shutdownHandler, // shutdown
                            pingHandler, // Handle ping
                            context // Handle app
                    });
                }
            });

            server.setHandler(handlerWrapper);

            // Add Lifecycle listener
            server.addLifeCycleListener(new LifeCycle.Listener() {
                @Override
                public void lifeCycleFailure(LifeCycle lc, Throwable th) {
                    log.error("${symbol_pound}==> error = {}", th.getMessage());
                    err.println("${symbol_pound}==> error = " + th.getMessage());
                    th.printStackTrace();
                }

                @Override
                public void lifeCycleStarted(LifeCycle lc) {
                    log.info(String.format("${symbol_pound}==> Started jetty @ http://%s:%d/", host, port));
                    out.println(String.format("${symbol_pound}==> Started jetty @ http://%s:%d/", host, port));
                }

                @Override
                public void lifeCycleStarting(LifeCycle lc) {
                    log.info(String.format("${symbol_pound}==> Starting jetty @ http://%s:%d/ ...", host, port));
                    out.println(String.format("${symbol_pound}==> Starting jetty @ http://%s:%d/ ...", host, port));
                }

                @Override
                public void lifeCycleStopped(LifeCycle lc) {
                    log.info(String.format("${symbol_pound}==> Stopped jetty @ http://%s:%d/", host, port));
                    out.println(String.format("${symbol_pound}==> Stopped jetty @ http://%s:%d/", host, port));
                }

                @Override
                public void lifeCycleStopping(LifeCycle lc) {
                    log.info(String.format("${symbol_pound}==> Stopping jetty @ http://%s:%d/ ...", host, port));
                    out.println(String.format("${symbol_pound}==> Stopping jetty @ http://%s:%d/ ...", host, port));
                }

            });
            server.start();
            server.join();
        } catch (Exception e) {
            System.exit(-1);
        }
    }

    void stopJetty(final Server server, final WebAppContext context) {
        try {
            if (null != context) {
                context.stop();
            }
            if (null != server) {
                server.stop();
            }
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
        }
        System.exit(0);
    }

    void requestStop() throws URISyntaxException, IOException {
        try {
            URI uri = URIUtils.createURI("http", host, port, "jetty-shutdown", "secret=" + secret, null);
            new DefaultHttpClient().execute(new HttpPost(uri));
        } catch (Exception ignored) {
        }
    }

    File getTempDirectory(String currentDir, String folder) throws IOException {
        File workDir;
        if (workDirPath != null) {
            workDir = new File(workDirPath);
        } else {
            workDir = new File(currentDir, folder + ".work");
        }
        out.println("Trying to clean work directory = " + workDir);
        FileUtils.deleteDirectory(workDir);
        return workDir;
    }

    class DetailedLoggingHandler extends HandlerWrapper {
        // Request counter
        AtomicLong count = new AtomicLong(0);

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request,
                HttpServletResponse response) throws IOException, ServletException {
            if (!log.isDebugEnabled()) {
                super.handle(target, baseRequest, request, response);
            } else {
                try {
                    long counter = count.incrementAndGet();
                    // DetailedLogger detailedLogger = new DetailedLogger(count.incrementAndGet());
                    final WrappedRequest wRequest = new WrappedRequest((HttpServletRequest) request);
                    final WrappedResponse wResponse = new WrappedResponse((HttpServletResponse) response);
                    // Log request
                    log.debug("{}", printRequest(counter, wRequest));
                    // Handle request
                    super.handle(target, baseRequest, wRequest, wResponse);
                    // Log response
                    log.debug("{}", printResponse(counter, wResponse));
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        StringBuilder printRequest(long count, WrappedRequest request) throws IOException {
            StringBuilder b = new StringBuilder();
            b.append('${symbol_escape}n');
            // Request line
            prefixId(b, count).append(NOTIFICATION_PREFIX).append("Server in-bound request").append('${symbol_escape}n');
            // Method
            prefixId(b, count).append(REQUEST_PREFIX).append(request.getMethod()).append(" ")
                    .append(request.getPathInfo()).append('${symbol_escape}n');
            // Query
            if (null != request.getQueryString()) {
                prefixId(b, count).append(REQUEST_PREFIX).append("?").append(request.getQueryString())
                        .append('${symbol_escape}n');
            }

            // Headers
            Enumeration<String> headers = request.getHeaderNames();
            while (headers.hasMoreElements()) {
                String header = headers.nextElement();
                Enumeration<String> values = request.getHeaders(header);
                while (values.hasMoreElements()) {
                    String value = values.nextElement();
                    prefixId(b, count).append(REQUEST_PREFIX).append(header).append(": ").append(value)
                            .append('${symbol_escape}n');
                }
            }
            prefixId(b, count).append(REQUEST_PREFIX).append('${symbol_escape}n');

            // Entity
            b.append(request.getRequestBody()).append("${symbol_escape}n");
            return b;
        }

        StringBuilder printResponse(long count, WrappedResponse response) throws IOException {
            StringBuilder b = new StringBuilder();
            b.append('${symbol_escape}n');
            // Response line
            prefixId(b, count).append(NOTIFICATION_PREFIX).append("Server out-bound response").append('${symbol_escape}n');
            prefixId(b, count).append(RESPONSE_PREFIX).append(Integer.toString(response.getStatus()))
                    .append('${symbol_escape}n');

            // Headers
            for (String header : response.getHeaderNames()) {
                for (String value : response.getHeaders(header)) {
                    prefixId(b, count).append(RESPONSE_PREFIX).append(header).append(": ").append(value)
                            .append('${symbol_escape}n');
                }
            }
            prefixId(b, count).append(RESPONSE_PREFIX).append('${symbol_escape}n');

            // Entity
            String content = response.getContent();
            if (null != content) {
                b.append(response.getContent()).append("${symbol_escape}n");
            }
            return b;
        }

        StringBuilder prefixId(StringBuilder b, long count) {
            b.append(count).append(" ");
            return b;
        }

        class WrappedRequest extends HttpServletRequestWrapper {

            private ByteArrayInputStream bais = null;
            private ByteArrayOutputStream baos = null;
            private BufferedServletInputStream bsis = null;
            private byte[] buffer = null;

            class BufferedServletInputStream extends ServletInputStream {
                private ByteArrayInputStream bais;

                public BufferedServletInputStream(ByteArrayInputStream bais) {
                    this.bais = bais;
                }

                @Override
                public int available() {
                    return this.bais.available();
                }

                @Override
                public int read() {
                    return this.bais.read();
                }

                @Override
                public int read(byte[] buf, int off, int len) {
                    return this.bais.read(buf, off, len);
                }

            }

            public WrappedRequest(HttpServletRequest req) throws IOException {
                super(req);
                // Read InputStream and store its content in a buffer.
                InputStream is = req.getInputStream();
                this.baos = new ByteArrayOutputStream();
                byte buf[] = new byte[1024];
                int read;
                while ((read = is.read(buf)) > 0) {
                    this.baos.write(buf, 0, read);
                }
                this.buffer = this.baos.toByteArray();
            }

            @Override
            public ServletInputStream getInputStream() {
                this.bais = new ByteArrayInputStream(this.buffer);
                this.bsis = new BufferedServletInputStream(this.bais);
                return this.bsis;
            }

            public String getRequestBody() throws IOException {
                BufferedReader reader = new BufferedReader(new InputStreamReader(this.getInputStream()));
                String line = null;
                StringBuilder inputBuffer = new StringBuilder();
                do {
                    line = reader.readLine();
                    if (null != line) {
                        inputBuffer.append(line.trim());
                    }
                } while (line != null);
                reader.close();
                return inputBuffer.toString().trim();
            }

        }

        class WrappedResponse implements HttpServletResponse {

            HttpServletResponse original;
            MyServletOutputStream tee;
            ByteArrayOutputStream bos;

            public WrappedResponse(HttpServletResponse response) {
                original = response;
            }

            public String getContent() {
                return (null == bos) ? null : bos.toString();
            }

            public PrintWriter getWriter() throws IOException {
                return original.getWriter();
            }

            public ServletOutputStream getOutputStream() throws IOException {
                if (tee == null) {
                    bos = new ByteArrayOutputStream();
                    tee = new MyServletOutputStream(original.getOutputStream(), bos);
                }
                return tee;
            }

            @Override
            public String getCharacterEncoding() {
                return original.getCharacterEncoding();
            }

            @Override
            public String getContentType() {
                return original.getContentType();
            }

            @Override
            public void setCharacterEncoding(String charset) {
                original.setCharacterEncoding(charset);
            }

            @Override
            public void setContentLength(int len) {
                original.setContentLength(len);
            }

            @Override
            public void setContentType(String type) {
                original.setContentType(type);
            }

            @Override
            public void setBufferSize(int size) {
                original.setBufferSize(size);
            }

            @Override
            public int getBufferSize() {
                return original.getBufferSize();
            }

            @Override
            public void flushBuffer() throws IOException {
                tee.flush();
            }

            @Override
            public void resetBuffer() {
                original.resetBuffer();
            }

            @Override
            public boolean isCommitted() {
                return original.isCommitted();
            }

            @Override
            public void reset() {
                original.reset();
            }

            @Override
            public void setLocale(Locale loc) {
                original.setLocale(loc);
            }

            @Override
            public Locale getLocale() {
                return original.getLocale();
            }

            @Override
            public void addCookie(Cookie cookie) {
                original.addCookie(cookie);
            }

            @Override
            public boolean containsHeader(String name) {
                return original.containsHeader(name);
            }

            @Override
            public String encodeURL(String url) {
                return original.encodeURL(url);
            }

            @Override
            public String encodeRedirectURL(String url) {
                return original.encodeRedirectURL(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public String encodeUrl(String url) {
                return original.encodeUrl(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public String encodeRedirectUrl(String url) {
                return original.encodeRedirectUrl(url);
            }

            @Override
            public void sendError(int sc, String msg) throws IOException {
                original.sendError(sc, msg);
            }

            @Override
            public void sendError(int sc) throws IOException {
                original.sendError(sc);
            }

            @Override
            public void sendRedirect(String location) throws IOException {
                original.sendRedirect(location);
            }

            @Override
            public void setDateHeader(String name, long date) {
                original.setDateHeader(name, date);
            }

            @Override
            public void addDateHeader(String name, long date) {
                original.addDateHeader(name, date);
            }

            @Override
            public void setHeader(String name, String value) {
                original.setHeader(name, value);
            }

            @Override
            public void addHeader(String name, String value) {
                original.addHeader(name, value);
            }

            @Override
            public void setIntHeader(String name, int value) {
                original.setIntHeader(name, value);
            }

            @Override
            public void addIntHeader(String name, int value) {
                original.addIntHeader(name, value);
            }

            @Override
            public void setStatus(int sc) {
                original.setStatus(sc);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void setStatus(int sc, String sm) {
                original.setStatus(sc, sm);
            }

            @Override
            public int getStatus() {
                return original.getStatus();
            }

            @Override
            public String getHeader(String paramString) {
                return original.getHeader(paramString);
            }

            @Override
            public Collection<String> getHeaders(String paramString) {
                return original.getHeaders(paramString);
            }

            @Override
            public Collection<String> getHeaderNames() {
                return original.getHeaderNames();
            }

            class MyServletOutputStream extends ServletOutputStream {

                final TeeOutputStream targetStream;

                public MyServletOutputStream(OutputStream one, OutputStream two) {
                    targetStream = new TeeOutputStream(one, two);
                }

                @Override
                public void write(int arg0) throws IOException {
                    this.targetStream.write(arg0);
                }

                public void flush() throws IOException {
                    super.flush();
                    this.targetStream.flush();
                }

                public void close() throws IOException {
                    super.close();
                    this.targetStream.close();
                }
            }
        }
    }
}