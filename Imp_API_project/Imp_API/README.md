# Imp_API — ScanMalware

Projeto Java que disponibiliza uma interface web para enviar uma URL ao backend, consultar a ScanMalware API e apresentar o resultado em uma tela web.

## Requisitos

- JDK 17 ou superior
- Internet para acessar a ScanMalware API
- Windows (para usar o `run.bat`)

## Como executar — forma mais fácil

1. Extraia o ZIP.
2. Abra a pasta `Imp_API`.
3. Dê dois cliques em `run.bat`.
4. Quando aparecer `Servidor: http://localhost:8080`, abra esse endereço no navegador.
5. Digite uma URL e clique em **Escanear**.

Não é necessário instalar Node.js, npm ou Maven para executar esta versão.

## Estrutura

```text
Imp_API/
├── run.bat
├── README.md
├── web/
│   ├── index.html
│   ├── js/
│   │   └── app.js
│   ├── css/
│   │   ├── tokens.css
│   │   └── styles.css
│   ├── assets/
│   │   └── screenshot-sample.png
│   └── public/
└── src/
    └── java/
        └── br/
            └── com/
                └── securityapi/
                    ├── Main.java
                    ├── api/
                    ├── cli/
                    ├── model/
                    ├── service/
                    └── server/
```

## O que foi integrado

O frontend enviado foi colocado dentro de `web/` e passou a ser servido diretamente pelo servidor Java.

O JavaScript do frontend foi adaptado para deixar de usar dados mockados e chamar o backend real:

```text
Navegador
   ↓ POST /api/scan
ScanServer (Java)
   ↓
ScanService
   ↓
ScanApi
   ↓ HTTPS
ScanMalware API
   ↓
resultado
   ↑
Navegador mostra score, veredito, screenshot e análise de IA
```

Também foi adicionada a rota `GET /api/status`, usada pelo frontend para indicar se o backend local está disponível.

O histórico exibido na tela usa `localStorage` do navegador, portanto fica salvo no navegador e não em um banco de dados.

## Observação

A seção visual de métricas globais do layout original continua sendo apenas parte do design. Os resultados de um scan individual, porém, são preenchidos com os dados recebidos do backend.
