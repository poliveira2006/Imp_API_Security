package br.com.securityapi.model;
//Resultados do scan

public class Scan {

    private final String scanId;
    private final String url;
    private final String status;      //status
    private final int riskScore;      // Pontuação de risco
    private final String verdict;     // situação do scan
    private final String screenshot;  // URL do print
    private final String aiAnalysis;  // Texto da análise da IA da API

    public Scan(String scanId, String url, String status, int riskScore,
                String verdict, String screenshot, String aiAnalysis) {
        this.scanId = scanId;
        this.url = url;
        this.status = status;
        this.riskScore = riskScore;
        this.verdict = verdict;
        this.screenshot = screenshot;
        this.aiAnalysis = aiAnalysis;
        //construtor
    }

    public String getScanId() {
        return scanId;
    }

    public String getUrl() {
        return url;
    }

    public String getStatus() {
        return status;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getScreenshot() {
        return screenshot;
    }

    public String getAiAnalysis() {
        return aiAnalysis;
    }
    //getters

    public boolean isCompleto() {
        return "completed".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status)
                || "error".equalsIgnoreCase(status);
    }
    //retorna true se o scan foi finalizado

    public String getNivelRisco() {
        if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            return "ERRO";
        }

        if (riskScore >= 70) return "ALTO";
        if (riskScore >= 40) return "MÉDIO";
        if (riskScore > 0)   return "BAIXO";

        //A API também retorna risk level no resumo.
        if ("high".equalsIgnoreCase(verdict)) return "ALTO";
        if ("medium".equalsIgnoreCase(verdict)) return "MÉDIO";
        if ("low".equalsIgnoreCase(verdict)) return "BAIXO";
        if ("safe".equalsIgnoreCase(verdict)) return "SEGURO";

        return "SEGURO";
        //pega o nivel de risco e retorna uma String correspondente
    }
}
