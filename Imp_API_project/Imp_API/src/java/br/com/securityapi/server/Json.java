package br.com.securityapi.server;

public class Json {
    

    public static String esc(String s) {
        
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    } //formato json + quebra de linha v tab (\r retorna para o inicio da linha) 

    public static String obj(String chave, String valor) {
        return "{\"" + esc(chave) + "\":\"" + esc(valor) + "\"}";
    } //Escapar = transformar caracteres especiais em uma versão segura para colocar dentro de uma string.


    public static String erro(String msg) {
        return "{\"erro\":\"" + esc(msg) + "\"}";
        //monta o formato padrão de erro
    }
}