package com.minimercado;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Regras da loja guardando tudo no Firestore: coleções produtos, pedidos e usuarios. */
@Service
public class LojaService {
    private static final double CASHBACK = 0.02;
    private final Firestore db;

    public LojaService(Firestore db, @Value("${admin.senha}") String senhaAdm) {
        this.db = db;
        semear(senhaAdm);
    }

    // ---- utilitários ----
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    private static <T> T esperar(ApiFuture<T> f) {
        try { return f.get(); }
        catch (Exception e) {
            for (Throwable t = e; t != null; t = t.getCause())
                if (t instanceof IllegalStateException) throw (IllegalStateException) t;
            throw new RuntimeException("Erro no Firestore: " + e.getMessage(), e);
        }
    }

    private static String hash(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Na primeira execução cria o administrador e os produtos de exemplo. */
    private void semear(String senhaAdm) {
        DocumentReference adm = db.collection("usuarios").document("admin@mercanet.com");
        if (!esperar(adm.get()).exists())
            esperar(adm.set(m("nome", "Administrador", "email", "admin@mercanet.com", "senhaHash", hash(senhaAdm), "tipo", "ADM", "pontos", 0.0)));
        if (!esperar(db.collection("produtos").limit(1).get()).isEmpty()) return;
        Object[][] d = {
            {"Arroz branco 5kg", "Mercearia", "🍚", 29.90, 40}, {"Feijão carioca 1kg", "Mercearia", "🫘", 8.50, 60},
            {"Macarrão espaguete 500g", "Mercearia", "🍝", 5.20, 80}, {"Açúcar refinado 1kg", "Mercearia", "🧂", 4.99, 50},
            {"Café torrado 500g", "Mercearia", "☕", 18.90, 30}, {"Banana prata 1kg", "Hortifrúti", "🍌", 6.90, 25},
            {"Tomate 1kg", "Hortifrúti", "🍅", 8.90, 20}, {"Maçã 1kg", "Hortifrúti", "🍎", 9.90, 4},
            {"Leite integral 1L", "Laticínios", "🥛", 5.49, 90}, {"Queijo mussarela 200g", "Laticínios", "🧀", 14.90, 22},
            {"Ovos 12 unidades", "Laticínios", "🥚", 12.90, 35}, {"Pão de forma 500g", "Padaria", "🍞", 8.90, 28},
            {"Refrigerante 2L", "Bebidas", "🥤", 9.50, 45}, {"Suco de laranja 1L", "Bebidas", "🍊", 7.90, 30},
            {"Água mineral 1,5L", "Bebidas", "💧", 3.20, 100}, {"Detergente 500ml", "Limpeza", "🧼", 2.99, 70},
            {"Sabão em pó 1kg", "Limpeza", "🧺", 14.90, 3}, {"Papel higiênico 12 rolos", "Higiene", "🧻", 22.90, 40}};
        for (Object[] x : d)
            esperar(db.collection("produtos").add(new Produto((String) x[0], (String) x[1], (String) x[2], (double) x[3], ((Number) x[4]).longValue())));
    }

    // ---- Usuários ----
    public Usuario usuario(String email) {
        DocumentSnapshot d = esperar(db.collection("usuarios").document(email).get());
        return d.exists() ? d.toObject(Usuario.class) : null;
    }

    public Usuario login(String email, String senha) {
        Usuario u = usuario(email.trim().toLowerCase());
        return u != null && u.senhaHash.equals(hash(senha)) ? u : null;
    }

    public Usuario cadastrar(String nome, String email, String senha) {
        String k = email.trim().toLowerCase();
        Usuario u = new Usuario(nome.trim(), k, hash(senha), "CLIENTE");
        DocumentReference ref = db.collection("usuarios").document(k);
        esperar(db.runTransaction(tx -> {
            if (tx.get(ref).get().exists()) throw new IllegalStateException("Este e-mail já está cadastrado.");
            tx.set(ref, u);
            return null;
        }));
        return u;
    }

    // ---- Produtos ----
    public List<Produto> listar() {
        List<Produto> l = new ArrayList<>();
        for (QueryDocumentSnapshot d : esperar(db.collection("produtos").orderBy("nome").get()).getDocuments())
            l.add(d.toObject(Produto.class));
        return l;
    }

    public Produto buscar(String id) {
        DocumentSnapshot d = esperar(db.collection("produtos").document(id).get());
        return d.exists() ? d.toObject(Produto.class) : null;
    }

    public void salvar(Produto p) { esperar(db.collection("produtos").add(p)); }

    public void editar(String id, String nome, String categoria, double preco, long estoque, String imagem) {
        Map<String, Object> up = m("nome", nome, "categoria", categoria, "preco", preco, "estoque", Math.max(0, estoque));
        if (imagem != null) up.put("imagem", imagem);
        esperar(db.collection("produtos").document(id).update(up));
    }

    public void excluir(String id) { esperar(db.collection("produtos").document(id).delete()); }

    // ---- Pedidos ----
    public List<Map<String, Object>> pedidos() {
        List<Map<String, Object>> l = new ArrayList<>();
        for (QueryDocumentSnapshot d : esperar(db.collection("pedidos").orderBy("criadoMs", Query.Direction.DESCENDING).limit(10).get()).getDocuments())
            l.add(d.getData());
        return l;
    }

    public Map<String, Object> pedido(String codigo) {
        DocumentSnapshot d = esperar(db.collection("pedidos").document(codigo).get());
        return d.exists() ? d.getData() : null;
    }

    /** Fecha a venda numa transação: confere e baixa o estoque e grava o pedido. Pix fica aguardando; cartão já sai pago. */
    public String finalizar(Usuario u, Map<String, Integer> carrinho, String pagamento) {
        boolean pix = "Pix".equals(pagamento);
        String codigo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return esperar(db.runTransaction(tx -> {
            double total = 0;
            List<Map<String, Object>> itens = new ArrayList<>();
            Map<DocumentReference, Long> novoEstoque = new HashMap<>();
            for (Map.Entry<String, Integer> e : carrinho.entrySet()) {
                DocumentReference ref = db.collection("produtos").document(e.getKey());
                DocumentSnapshot s = tx.get(ref).get();
                if (!s.exists()) throw new IllegalStateException("Um produto do carrinho não existe mais.");
                long atual = s.getLong("estoque");
                if (atual < e.getValue()) throw new IllegalStateException("Estoque insuficiente para " + s.getString("nome") + ".");
                double preco = s.getDouble("preco");
                total += preco * e.getValue();
                itens.add(m("nome", s.getString("nome"), "qtd", e.getValue(), "preco", preco));
                novoEstoque.put(ref, atual - e.getValue());
            }
            double t = Math.round(total * 100) / 100.0;
            double cb = Math.round(t * CASHBACK * 100) / 100.0;
            novoEstoque.forEach((r, q) -> tx.update(r, "estoque", q));
            tx.create(db.collection("pedidos").document(codigo),
                    m("codigo", codigo, "email", u.email, "cliente", u.nome, "pagamento", pagamento, "itens", itens,
                      "total", t, "cashback", cb, "status", pix ? "Aguardando Pix" : "Pago", "pix", pix ? Pix.payload(t) : null,
                      "criadoMs", System.currentTimeMillis(),
                      "criadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))));
            if (!pix) tx.update(db.collection("usuarios").document(u.email), "pontos", FieldValue.increment(cb));
            return codigo;
        }));
    }

    /** Simula a confirmação do Pix e credita o cashback. */
    public void confirmarPix(String codigo) {
        DocumentReference ref = db.collection("pedidos").document(codigo);
        esperar(db.runTransaction(tx -> {
            DocumentSnapshot d = tx.get(ref).get();
            if (d.exists() && "Aguardando Pix".equals(d.getString("status"))) {
                tx.update(ref, "status", "Pago");
                tx.update(db.collection("usuarios").document(d.getString("email")), "pontos", FieldValue.increment(d.getDouble("cashback")));
            }
            return null;
        }));
    }
}
