package br.com.securityapi.api;

import br.com.securityapi.model.Scan;
import br.com.securityapi.server.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanApi {

    private static final String BASE = "https://scanmalware.com";
    //cria BASE para ser o link da api
    

    private final HttpClient client;

    public ScanApi() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    } //construtor

    public String submeterScan(String url) {
        String body = "{\"url\":\"" + Json.esc(url) + "\",\"scan_type\":\"public\"}";
        //pega a URL e monta o JSON + define o scan como publico

        String json = post(BASE + "/api/v1/scan", body);
        //envia a requisicao para a API

        Matcher m = Pattern.compile("\"scan_id\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        //procura o scan_id dentro da resposta

        if (m.find())   
            return m.group(1);
        //retorna o id se encontrou

        throw new RuntimeException("Não foi possível obter scan_id: " + json);
        //exceção
    }

    public Scan obterResultado(String scanId) {
    String json = get(BASE + "/api/v1/scan/" + scanId + "/summary");
    System.out.println("JSON RECEBIDO:");
    System.out.println(json);
    String url = extrair(json, "url");
    String status = extrair(json, "status");
    int riskScore = extrairInt(json, "overall_score");
    String verdict = extrair(json, "risk_level");
    String screenshot = BASE + "/api/v1/screenshot/" + scanId;
    return new Scan(scanId, url, status, riskScore, verdict, screenshot, "");
    }

    public String obterAiAnalysis(String scanId) {
        try {
            String json = get(BASE + "/api/v1/ai/" + scanId);
            //consulta a análise de IA depois que o scan termina

            String classification = extrair(json, "classification");
            String recommendedAction = extrair(json, "recommended_action");
            String skippedReason = extrair(json, "skipped_reason");

            if (!classification.isEmpty() && !recommendedAction.isEmpty()) {
                return classification + " — " + recommendedAction;
            }

            if (!classification.isEmpty()) return classification;
            if (!recommendedAction.isEmpty()) return recommendedAction;
            if (!skippedReason.isEmpty()) return skippedReason;

        } catch (RuntimeException e) {
            //A análise de IA é complementar; o resultado principal continua válido.
        }

        return "";
    }

    public int obterProgresso(String scanId) {
        String json = get(BASE + "/api/v1/result/" + scanId + "/progress");
        //pega o atributo base e concatena para manter o JSON

        return extrairInt(json, "percentage");
    } //retorna o progresso do Scan

    private String get(String url) {
        try {
            //helper

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
            }

            return resp.body();

        } catch (Exception e) {
            throw new RuntimeException("Falha ao consultar ScanMalware: " + e.getMessage(), e);
        }
    }

    private String post(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
            }

            return resp.body();

        } catch (Exception e) {
            throw new RuntimeException("Falha ao submeter scan: " + e.getMessage(), e);
        }
    }

    static String extrair(String json, String chave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(chave) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    //matcher (objeto com estado da busca), é instanciado com o padrão definido pelo pattern e aplica na response
    //.find procura a proxima ocorrencia do padrão no texto e devolve true ou false.
    //helpers (metodos abstraidos que facilitam a manipulação e implementação de dados em aplicações)

    static int extrairInt(String json, String chave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(chave) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    } //metodo extrair porém utilizando corpo do JSON int.
}
