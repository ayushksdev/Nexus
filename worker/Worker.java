import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class Worker {
    private static String workerId = "worker-default";
    private static String version = "v1";
    private static String mode = "NORMAL";
    private static int port = 8081;
    private static Set<String> processedWorkIds = new HashSet<>();
    private static String persistenceFile = "";

    public static void main(String[] args) throws Exception {
        // Parse arguments
        for (String arg : args) {
            if (arg.startsWith("--id=")) {
                workerId = arg.substring(5);
            } else if (arg.startsWith("--version=")) {
                version = arg.substring(10);
            } else if (arg.startsWith("--mode=")) {
                mode = arg.substring(7).toUpperCase();
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring(7));
            }
        }

        System.out.println("----------------------------------------");
        System.out.println("Worker starting...");
        System.out.println("ID: " + workerId);
        System.out.println("Port: " + port);
        System.out.println("Version: " + version);
        System.out.println("Failure Mode: " + mode);
        System.out.println("----------------------------------------");

        // If failure mode is CRASH_ON_START, exit immediately
        if ("CRASH_ON_START".equals(mode)) {
            System.err.println("FATAL: Worker failed to start due to CRASH_ON_START failure mode.");
            Thread.sleep(200); // Allow logs to flush
            System.exit(1);
        }

        // Set persistence file
        persistenceFile = "processed-work-" + workerId + ".json";
        loadProcessedWork();

        // Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/failure-mode", new FailureModeHandler());
        server.createContext("/work", new WorkHandler());
        server.setExecutor(null); // default executor
        server.start();

        System.out.println("Worker is listening on port " + port);
    }

    private static synchronized void loadProcessedWork() {
        try {
            File file = new File(persistenceFile);
            if (file.exists()) {
                String content = new String(Files.readAllBytes(Paths.get(persistenceFile)));
                // Quick custom parsing of simple JSON array/list
                // e.g. ["job-123","job-124"]
                content = content.replace("[", "").replace("]", "").replace("\"", "").trim();
                if (!content.isEmpty()) {
                    String[] ids = content.split(",");
                    for (String id : ids) {
                        processedWorkIds.add(id.trim());
                    }
                }
                System.out.println("Loaded " + processedWorkIds.size() + " processed work IDs from " + persistenceFile);
            }
        } catch (Exception e) {
            System.err.println("Failed to load processed work history: " + e.getMessage());
        }
    }

    private static synchronized boolean saveProcessedWork(String workId) {
        processedWorkIds.add(workId);
        try (FileWriter writer = new FileWriter(persistenceFile)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int i = 0;
            for (String id : processedWorkIds) {
                sb.append("\"").append(id).append("\"");
                if (i < processedWorkIds.size() - 1) {
                    sb.append(",");
                }
                i++;
            }
            sb.append("]");
            writer.write(sb.toString());
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.println("Failed to persist processed work: " + e.getMessage());
            return false;
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // CRASH_ON_START check in case it was set dynamically and restarts
            if ("CRASH_ON_START".equals(mode)) {
                System.err.println("CRASH_ON_START mode: Killing process on health check");
                System.exit(1);
            }

            String response = String.format("{\"status\":\"UP\",\"workerId\":\"%s\",\"version\":\"%s\",\"mode\":\"%s\"}",
                    workerId, version, mode);

            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class FailureModeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // Read request body
            InputStream is = exchange.getRequestBody();
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = is.read()) != -1) {
                sb.append((char) ch);
            }
            String body = sb.toString();

            // Simple parser for {"mode": "CRASH"} or "CRASH"
            String newMode = "NORMAL";
            if (body.contains("CRASH_ON_START")) {
                newMode = "CRASH_ON_START";
            } else if (body.contains("CRASH")) {
                newMode = "CRASH";
            } else if (body.contains("SLOW")) {
                newMode = "SLOW";
            } else if (body.contains("NORMAL")) {
                newMode = "NORMAL";
            }

            mode = newMode;
            System.out.println("Failure mode updated to: " + mode);

            if ("CRASH_ON_START".equals(mode)) {
                System.err.println("CRASH_ON_START set: crashing immediately");
                System.exit(1);
            }

            String response = String.format("{\"status\":\"SUCCESS\",\"mode\":\"%s\"}", mode);
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class WorkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            System.out.println("Received work request.");

            // Evaluate failure mode during processing
            if ("CRASH".equals(mode)) {
                System.err.println("CRASH mode triggered: Crashing worker process immediately during work execution!");
                System.exit(1); // Exits the process, closing socket abruptly
            }

            if ("SLOW".equals(mode)) {
                System.out.println("SLOW mode active: Delaying processing by 10 seconds...");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    System.out.println("Delay interrupted: " + e.getMessage());
                }
            }

            // Read request body
            InputStream is = exchange.getRequestBody();
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = is.read()) != -1) {
                sb.append((char) ch);
            }
            String body = sb.toString();

            // Extract job ID from JSON e.g. {"id":"job-123"}
            String jobId = "unknown";
            int idIndex = body.indexOf("\"id\"");
            if (idIndex != -1) {
                int colonIndex = body.indexOf(":", idIndex);
                if (colonIndex != -1) {
                    int startQuote = body.indexOf("\"", colonIndex);
                    if (startQuote != -1) {
                        int endQuote = body.indexOf("\"", startQuote + 1);
                        if (endQuote != -1) {
                            jobId = body.substring(startQuote + 1, endQuote);
                        }
                    }
                }
            }

            System.out.println("Processing job: " + jobId);

            // Check Idempotency (Requirement 14)
            boolean alreadyProcessed;
            synchronized (Worker.class) {
                alreadyProcessed = processedWorkIds.contains(jobId);
            }

            String response;
            if (alreadyProcessed) {
                System.out.println("Idempotency match: Job " + jobId + " already processed. Skipping side effects.");
                response = String.format("{\"status\":\"ALREADY_PROCESSED\",\"workId\":\"%s\"}", jobId);
            } else {
                // Perform side effect
                System.out.println("Executing side-effect: [Side effect for job " + jobId + " executed at " + LocalDateTime.now() + "]");
                saveProcessedWork(jobId);
                response = String.format("{\"status\":\"SUCCESS\",\"workId\":\"%s\"}", jobId);
            }

            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
