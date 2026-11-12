package com.minimercado;

import com.google.cloud.firestore.annotation.DocumentId;

public class Produto {
    @DocumentId public String id;
    public String nome, categoria, icone;
    public String imagem; // foto enviada pelo admin (data URI); se nula, mostra o emoji
    public double preco;
    public long estoque;

    public Produto() {}
    public Produto(String nome, String categoria, String icone, double preco, long estoque) {
        this.nome = nome; this.categoria = categoria; this.icone = icone;
        this.preco = preco; this.estoque = estoque;
    }
}
