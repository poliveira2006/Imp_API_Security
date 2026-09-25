
package br.com.securityapi.server;

import br.com.securityapi.model.Scan;
import br.com.securityapi.service.ScanService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ScanServer {

    private final ScanService service = new ScanService();
    private final Path webRoot;

    public ScanServer(String webRootPath) {
        this.webRoot = Paths.get(webRootPath).toAbsolutePath().normalize();
        //tranforma a String em Path, AbsolutePath tranforma em caminho absoluto
        //e normalize remove . e ... da root
    }

    public void iniciar(int porta) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);

        server.createContext("/api/scan", this::rotaScan);
        server.createContext("/api/status", this::rotaStatus);
        server.createContext("/", this::rotaEstatica);

        server.setExecutor(null);
        server.start();

        System.out.println("========================================");
        System.out.println("  Servidor: http://localhost:" + porta);
        System.out.println("  Web root: " + webRoot);
        System.out.println("========================================");
    }

    // ============ ROTAS ============

    //Pegar os status da API
    private void rotaStatus(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            responder(ex, 200, "{\"status\":\"online\"}", "application/json");
            return;
        }

        responder(ex, 405, Json.erro("Método não permitido"), "application/json");
    }

   //POST SCAN
    private void rotaScan(HttpExchange ex) throws IOException {
        try {
            String metodo = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(metodo)) {
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                responder(ex, 204, new byte[0], "text/plain");
                return;
            }

            if ("POST".equalsIgnoreCase(metodo) && "/api/scan".equals(path)) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String url = extrairUrl(body);
                Scan scan = service.escanear(url);
                responder(ex, 200, scanParaJson(scan), "application/json");
                return;
            }

            if ("GET".equalsIgnoreCase(metodo) && path.startsWith("/api/scan/")) {
                String id = path.substring("/api/scan/".length()).trim();
                Scan scan = service.obterScan(id);
                responder(ex, 200, scanParaJson(scan), "application/json");
                return;
            }

            responder(ex, 405, Json.erro("Método não permitido"), "application/json");

        } catch (IllegalArgumentException e) {
            responder(ex, 400, Json.erro(e.getMessage()), "application/json");
        } catch (Exception e) {
            responder(ex, 500, Json.erro(e.getMessage()), "application/json");
        }
    }

    private void rotaEstatica(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        if (path.contains("..")) {
            responder(ex, 400, "Bad Request", "text/plain");
            return;
        }

        Path arquivo = webRoot.resolve(path.substring(1)).normalize();

        if (!arquivo.startsWith(webRoot) || !Files.exists(arquivo) || Files.isDirectory(arquivo)) {
            responder(ex, 404, "Not Found", "text/plain");
            return;
        }

        byte[] conteudo = Files.readAllBytes(arquivo);
        responder(ex, 200, conteudo, contentType(path));
    }

    // ============ HELPERS ============

    private String extrairUrl(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(body);

        if (m.find()) return m.group(1);

        throw new IllegalArgumentException("Campo 'url' não encontrado");
    }

    private String scanParaJson(Scan s) {
        return "{"
                + "\"scanId\":\"" + Json.esc(s.getScanId()) + "\","
                + "\"url\":\"" + Json.esc(s.getUrl()) + "\","
                + "\"status\":\"" + Json.esc(s.getStatus()) + "\","
                + "\"riskScore\":" + s.getRiskScore() + ","
                + "\"nivel\":\"" + Json.esc(s.getNivelRisco()) + "\","
                + "\"verdict\":\"" + Json.esc(s.getVerdict()) + "\","
                + "\"screenshot\":\"" + Json.esc(s.getScreenshot()) + "\","
                + "\"aiAnalysis\":\"" + Json.esc(s.getAiAnalysis()) + "\""
                + "}";
    }

    private void responder(HttpExchange ex, int status, String body, String tipo) throws IOException {
        responder(ex, status, body.getBytes(StandardCharsets.UTF_8), tipo);
    }

    private void responder(HttpExchange ex, int status, byte[] body, String tipo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo + "; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        ex.sendResponseHeaders(status, body.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private String contentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".js"))   return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }
}
