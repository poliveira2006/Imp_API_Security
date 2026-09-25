//regras de negócio
package br.com.securityapi.service;

import br.com.securityapi.model.Scan;
import br.com.securityapi.api.ScanApi;
import java.util.Map;  //interface de dicionario
import java.util.concurrent.ConcurrentHashMap; //implementa a interface ConcurrentMap para
//que varios trheads possam acessar e modificar o map simultaneamente.


public class ScanService {

    private final ScanApi api = new ScanApi();
    //instancia do cliente HTTP

    private final Map<String, Scan> cache = new ConcurrentHashMap<>();
    //recebe scanId e guarda em cache;

    private static final int MAX_TENTATIVAS = 30;     //max para verificar o resultado
    private static final long INTERVALO_MS = 3000;

    public Scan escanear(String url) {
        validarUrl(url);

        String scanId = api.submeterScan(url);
        //chama a validação antes de tudo

        System.out.println("Scan submetido: " + scanId + " → " + url);
        //submete e armazena o id

        for (int i = 0; i < MAX_TENTATIVAS; i++) {
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrompido");
            }

            Scan scan = api.obterResultado(scanId);

            if (scan.isCompleto()) {
                String ai = api.obterAiAnalysis(scanId);
                Scan finalScan = new Scan(
                        scan.getScanId(),
                        scan.getUrl(),
                        scan.getStatus(),
                        scan.getRiskScore(),
                        scan.getVerdict(),
                        scan.getScreenshot(),
                        ai
                );

                cache.put(scanId, finalScan);
                return finalScan;
            }

            cache.put(scanId, scan);
            System.out.println("  [" + scanId + "] status=" + scan.getStatus());
        }
        //looping para que rode (obtenha scan do link) até estourar ou completar o maximo de tentativas

        Scan parcial = api.obterResultado(scanId);
        cache.put(scanId, parcial);

        return parcial;
        //devolve o ultimo scan
    }

    public Scan obterScan(String scanId) {
        if (scanId == null || scanId.isBlank()) {
            throw new IllegalArgumentException("Scan ID não informado");
        }

        Scan cacheado = cache.get(scanId);

        if (cacheado != null && cacheado.isCompleto()) {
            return cacheado;
        }

        Scan scan = api.obterResultado(scanId);

        if (scan.isCompleto()) {
            String ai = api.obterAiAnalysis(scanId);

            scan = new Scan(
                    scan.getScanId(),
                    scan.getUrl(),
                    scan.getStatus(),
                    scan.getRiskScore(),
                    scan.getVerdict(),
                    scan.getScreenshot(),
                    ai
            );
        }

        cache.put(scanId, scan);
        return scan;
        //verifica se o containsKey já foi consultado antes, se sim retorna do chache
        //se não, obtem o resultado e guarda na memória
    }

    private void validarUrl(String url) {
        if (url == null || url.isBlank()) {
            //verifica se é vazio
            throw new IllegalArgumentException("URL não informada");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL deve começar com http:// ou https://");
        } //verifica se tem o prefixo http://

        try {
            new java.net.URL(url);
        } catch (Exception e) {
            //se a criação da URL falhar, o formato é invalido
            throw new IllegalArgumentException("URL inválida: " + url);
        }
    }
}
