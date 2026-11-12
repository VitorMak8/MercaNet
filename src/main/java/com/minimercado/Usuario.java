package com.minimercado;

public class Usuario {
    public String nome, email, senhaHash, tipo; // tipo: "ADM" ou "CLIENTE"
    public double pontos; // cashback acumulado

    public Usuario() {}
    public Usuario(String nome, String email, String senhaHash, String tipo) {
        this.nome = nome; this.email = email; this.senhaHash = senhaHash; this.tipo = tipo;
    }
}
