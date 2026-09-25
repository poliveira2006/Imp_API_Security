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

    // instância única do serviço que faz a lógica de negócio
    private final ScanService service = new ScanService();

    // pasta raiz onde ficam os arquivos do frontend (html, css, js)
    private final Path webRoot;

    public ScanServer(String webRootPath) {
        // transforma a String em Path, AbsolutePath transforma em caminho absoluto
        this.webRoot = Paths.get(webRootPath).toAbsolutePath().normalize();
    }

     // sobe o servidor na porta informada e registra as rotas
    public void iniciar(int porta) throws IOException {
      
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        // cria o servidor HTTP escutando na porta

        // cada createContext registra um handler para um caminho específico
        server.createContext("/api/scan", this::rotaScan);
        server.createContext("/api/status", this::rotaStatus);
        server.createContext("/", this::rotaEstatica);

        // null = usa o executor padrão, uma thread por requisição
        server.setExecutor(null);
        server.start();

        System.out.println("========================================");
        System.out.println("  Servidor: http://localhost:" + porta);
        System.out.println("  Web root: " + webRoot);
        System.out.println("========================================");
    }


    // responde ao health check(diagnostico) da API com um status fixo de online
    private void rotaStatus(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            responder(ex, 200, "{\"status\":\"online\"}", "application/json");
            return;
        }

        // qualquer método diferente de GET cai aqui
        responder(ex, 405, Json.erro("Método não permitido"), "application/json");
    }

    // rota principal: POST
    private void rotaScan(HttpExchange ex) throws IOException {
        try {
            // pega o método (GET, POST, OPTIONS...) e o caminho da requisição
            String metodo = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            // preflight do CORS, o navegador manda OPTIONS antes de POST
            if ("OPTIONS".equalsIgnoreCase(metodo)) {
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                // 204 = sucesso sem corpo de resposta
                responder(ex, 204, new byte[0], "text/plain");
                return;
            }

            // POST na raiz do recurso = submeter uma nova URL para scan
            if ("POST".equalsIgnoreCase(metodo) && "/api/scan".equals(path)) {
                // lê o corpo da requisição como String UTF-8
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // extrai a url do JSON recebido e chama o serviço
                String url = extrairUrl(body);
                Scan scan = service.escanear(url);

                responder(ex, 200, scanParaJson(scan), "application/json");
                return;
            }

           //consultar um scan já feito pelo id
            if ("GET".equalsIgnoreCase(metodo) && path.startsWith("/api/scan/")) {
                // pega só a parte depois de /api/scan/
                String id = path.substring("/api/scan/".length()).trim();
                Scan scan = service.obterScan(id);

                responder(ex, 200, scanParaJson(scan), "application/json");
                return;
            }

            // se não caiu em nenhum dos casos acima, o método não é suportado
            responder(ex, 405, Json.erro("Método não permitido"), "application/json");

        } catch (IllegalArgumentException e) {
            // 400 = erro do cliente (url inválida, campo faltando, etc)
            responder(ex, 400, Json.erro(e.getMessage()), "application/json");
        } catch (Exception e) {
            // 500 = erro do servidor (api externa fora do ar, timeout, etc)
            responder(ex, 500, Json.erro(e.getMessage()), "application/json");
        }
    }

    // serve os arquivos do frontend (html, css, js) a partir da pasta webRoot
    private void rotaEstatica(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) {
            responder(ex, 400, "Bad Request", "text/plain");
            return;
        }

        // junta webRoot com o caminho pedido e normaliza 
        Path arquivo = webRoot.resolve(path.substring(1)).normalize();

        // verifica se o arquivo está dentro do webRoot,
        // existe de verdade, e não é uma pasta
        if (!arquivo.startsWith(webRoot) || !Files.exists(arquivo) || Files.isDirectory(arquivo)) {
            responder(ex, 404, "Not Found", "text/plain");
            return;
        }

        // lê os bytes do arquivo e devolve com o contentType() certo
        byte[] conteudo = Files.readAllBytes(arquivo);
        responder(ex, 200, conteudo, contentType(path));
    }


    //helpers
    // extrai o valor do campo "url" de dentro do JSON recebido no POST
    private String extrairUrl(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(body);

        if (m.find()) return m.group(1);
        // se não achou a url, é culpa do cliente (400)
        throw new IllegalArgumentException("Campo 'url' não encontrado");
    }

    //converte um objeto Scan em JSON
    private String scanParaJson(Scan s) {
        return "{"
                + "\"scanId\":\"" + Json.esc(s.getScanId()) + "\","
                + "\"url\":\"" + Json.esc(s.getUrl()) + "\","
                + "\"status\":\"" + Json.esc(s.getStatus()) + "\","
                + "\"riskScore\":" + s.getRiskScore() + ","   // número não leva aspas
                + "\"nivel\":\"" + Json.esc(s.getNivelRisco()) + "\","
                + "\"verdict\":\"" + Json.esc(s.getVerdict()) + "\","
                + "\"screenshot\":\"" + Json.esc(s.getScreenshot()) + "\","
                + "\"aiAnalysis\":\"" + Json.esc(s.getAiAnalysis()) + "\""
                + "}";
    }

    //overload (sobrecarga, declara atrib com o mesmo nome mas com assinaturas diferentes) que aceita String, converte pra bytes UTF-8
    private void responder(HttpExchange ex, int status, String body, String tipo) throws IOException {
        responder(ex, status, body.getBytes(StandardCharsets.UTF_8), tipo);
    }

    //monta a resposta HTTP
    private void responder(HttpExchange ex, int status, byte[] body, String tipo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo + "; charset=UTF-8");

        // libera CORS pra qualquer origem (útil em desenvolvimento)
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        // envia os headers e o tamanho do corpo
        ex.sendResponseHeaders(status, body.length);

        // try-with-resources fecha o stream automaticamente no final
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    //mapeia a extensão do arquivo para o contentType correto
    private String contentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".js"))   return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico"))  return "image/x-icon";

        //tipo genérico pra downloads
        return "application/octet-stream";
    }
}