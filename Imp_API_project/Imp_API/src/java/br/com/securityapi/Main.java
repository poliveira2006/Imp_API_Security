package br.com.securityapi;

import br.com.securityapi.server.ScanServer;

public class Main {
    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new ScanServer("web").iniciar(porta);
    }
}