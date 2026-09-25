package br.com.securityapi.cli;

import br.com.securityapi.model.Scan;
import br.com.securityapi.service.ScanService;
import java.util.Scanner;

public class ScanCli {

    public static void main(String[] args) {
        ScanService service = new ScanService();

        // Modo direto: java ScanCli https://example.com
        if (args.length > 0) {
            executar(service, args[0]);
            return;
        }

        // Modo interativo
        Scanner sc = new Scanner(System.in);
        System.out.println("=== ScanMalware CLI ===");
        System.out.print("URL para escanear: ");
        String url = sc.nextLine().trim();
        if (!url.isEmpty()) executar(service, url);
    }

    private static void executar(ScanService service, String url) {
        try {
            System.out.println("Escaneando " + url + " ...");
            Scan scan = service.escanear(url);

            System.out.println("\n=== Resultado ===");
            System.out.println("Scan ID    : " + scan.getScanId());
            System.out.println("Status     : " + scan.getStatus());
            System.out.println("Risk Score : " + scan.getRiskScore());
            System.out.println("Nível      : " + scan.getNivelRisco());
            System.out.println("Verdict    : " + scan.getVerdict());
            if (!scan.getScreenshot().isEmpty()) {
                System.out.println("Screenshot : " + scan.getScreenshot());
            }
            if (!scan.getAiAnalysis().isEmpty()) {
                System.out.println("AI         : " + scan.getAiAnalysis());
            }
        } catch (Exception e) {
            System.err.println("✗ " + e.getMessage());
        }
    }
}