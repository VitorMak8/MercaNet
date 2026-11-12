# MercaNet (com Firebase)

Mercado online em Java 17 + Spring Boot + Thymeleaf, com banco de dados Firebase Firestore (edição Standard).

## Como rodar
1. No console do Firebase: crie o projeto, crie o Firestore (ID `(default)`, região São Paulo, modo produção)
   e em Configurações do projeto > Contas de serviço > Gerar nova chave privada.
2. Renomeie o arquivo baixado para `serviceAccountKey.json` e coloque nesta pasta, ao lado do `pom.xml`.
3. Abra esta pasta no VS Code, abra `src/main/java/com/minimercado/MiniMercadoApplication.java` e clique em Run.
4. Quando aparecer `Started MiniMercadoApplication`, abra http://localhost:8080
5. No Firebase, em Firestore Database > Dados, vão aparecer as coleções `produtos`, `usuarios` e `pedidos`.

## Acesso
- Administrador: `admin@mercanet.com` / `admin123` (troque com a variável `ADMIN_SENHA`).
- Cliente: crie uma conta em "Cadastre-se".

## Coleções
- `produtos`: nome, categoria, icone, preco, estoque, imagem (foto reduzida para até 640 px)
- `usuarios`: documento = e-mail; nome, senhaHash, tipo (ADM ou CLIENTE), pontos (cashback)
- `pedidos`: documento = código; cliente, itens, total, cashback, pagamento, status, pix

Nunca envie o `serviceAccountKey.json` para ninguém nem suba no GitHub (o .gitignore já o ignora).
