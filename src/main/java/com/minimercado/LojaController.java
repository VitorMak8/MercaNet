package com.minimercado;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
public class LojaController {
    public static class Item { public Produto p; public int qtd; public double sub; }

    private final LojaService s;
    public LojaController(LojaService s) { this.s = s; }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> cart(HttpSession h) {
        Map<String, Integer> c = (Map<String, Integer>) h.getAttribute("cart");
        if (c == null) { c = new LinkedHashMap<>(); h.setAttribute("cart", c); }
        return c;
    }

    private static Usuario usuario(HttpSession h) { return (Usuario) h.getAttribute("usuario"); }
    private static boolean adm(HttpSession h) { Usuario u = usuario(h); return u != null && "ADM".equals(u.tipo); }

    @ModelAttribute("qtd") int qtd(HttpSession h) { return cart(h).values().stream().mapToInt(i -> i).sum(); }
    @ModelAttribute("usuario") Usuario usuarioModel(HttpSession h) { return usuario(h); }

    // ---- Login ----
    @GetMapping("/login")
    String loginPage(HttpSession h) { return usuario(h) == null ? "login" : (adm(h) ? "redirect:/admin" : "redirect:/"); }

    @PostMapping("/login")
    String login(@RequestParam String email, @RequestParam String senha, HttpSession h, RedirectAttributes r) {
        Usuario u = s.login(email, senha);
        if (u == null) { r.addFlashAttribute("erro", "E-mail ou senha incorretos."); return "redirect:/login"; }
        h.setAttribute("usuario", u);
        return "ADM".equals(u.tipo) ? "redirect:/admin" : "redirect:/";
    }

    @GetMapping("/cadastro") String cadastroPage() { return "cadastro"; }

    @PostMapping("/cadastro")
    String cadastro(@RequestParam String nome, @RequestParam String email, @RequestParam String senha, HttpSession h, RedirectAttributes r) {
        try { h.setAttribute("usuario", s.cadastrar(nome, email, senha)); return "redirect:/"; }
        catch (IllegalStateException e) { r.addFlashAttribute("erro", e.getMessage()); return "redirect:/cadastro"; }
    }

    @GetMapping("/sair") String sair(HttpSession h) { h.invalidate(); return "redirect:/login"; }

    // ---- Loja ----
    @GetMapping("/")
    String home(@RequestParam(required = false) String q, @RequestParam(required = false) String cat, Model m) {
        List<Produto> todos = s.listar();
        m.addAttribute("cats", todos.stream().map(p -> p.categoria).distinct().sorted().toList());
        m.addAttribute("produtos", todos.stream()
                .filter(p -> q == null || q.isBlank() || p.nome.toLowerCase().contains(q.toLowerCase()))
                .filter(p -> cat == null || cat.isBlank() || p.categoria.equals(cat)).toList());
        m.addAttribute("q", q); m.addAttribute("cat", cat);
        return "index";
    }

    @PostMapping("/carrinho/add/{id}")
    String add(@PathVariable String id, @RequestParam(defaultValue = "/") String voltar, HttpSession h, RedirectAttributes r) {
        Produto p = s.buscar(id);
        int n = cart(h).getOrDefault(id, 0) + 1;
        if (p == null || n > p.estoque) r.addFlashAttribute("erro", "Não há mais unidades disponíveis.");
        else { cart(h).put(id, n); if (!"/carrinho".equals(voltar)) r.addFlashAttribute("ok", p.nome + " foi para o carrinho."); }
        return "redirect:" + ("/carrinho".equals(voltar) ? "/carrinho" : "/");
    }

    /** Tira uma unidade; quando chega a zero, o item sai do carrinho. */
    @PostMapping("/carrinho/menos/{id}")
    String menos(@PathVariable String id, HttpSession h) {
        int n = cart(h).getOrDefault(id, 0) - 1;
        if (n <= 0) cart(h).remove(id); else cart(h).put(id, n);
        return "redirect:/carrinho";
    }

    @PostMapping("/carrinho/remover/{id}")
    String remover(@PathVariable String id, HttpSession h) { cart(h).remove(id); return "redirect:/carrinho"; }

    @GetMapping("/carrinho")
    String carrinho(HttpSession h, Model m) {
        cart(h).keySet().removeIf(id -> s.buscar(id) == null);
        List<Item> itens = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Integer> e : cart(h).entrySet()) {
            Item i = new Item(); i.p = s.buscar(e.getKey()); i.qtd = e.getValue(); i.sub = i.p.preco * i.qtd;
            total += i.sub; itens.add(i);
        }
        m.addAttribute("itens", itens); m.addAttribute("total", total);
        return "carrinho";
    }

    @PostMapping("/finalizar")
    String finalizar(@RequestParam String pagamento, HttpSession h, RedirectAttributes r) {
        if (cart(h).isEmpty()) return "redirect:/";
        try {
            String codigo = s.finalizar(usuario(h), cart(h), pagamento);
            cart(h).clear();
            h.setAttribute("usuario", s.usuario(usuario(h).email)); // atualiza o cashback exibido
            if ("Pix".equals(pagamento)) return "redirect:/pedido/" + codigo;
            r.addFlashAttribute("ok", "Pedido " + codigo + " pago no cartão! Cashback creditado.");
            return "redirect:/";
        } catch (IllegalStateException e) {
            r.addFlashAttribute("erro", e.getMessage());
            return "redirect:/carrinho";
        }
    }

    // ---- Pedido e Pix (simulado) ----
    @GetMapping("/pedido/{codigo}")
    String pedido(@PathVariable String codigo, HttpSession h, Model m) throws Exception {
        Map<String, Object> ped = s.pedido(codigo);
        if (ped == null || !(ped.get("email").equals(usuario(h).email) || adm(h))) return "redirect:/";
        m.addAttribute("ped", ped);
        if ("Aguardando Pix".equals(ped.get("status"))) m.addAttribute("qr", Pix.qrSvg((String) ped.get("pix")));
        return "pedido";
    }

    @PostMapping("/pedido/{codigo}/pagar")
    String pagar(@PathVariable String codigo, HttpSession h, RedirectAttributes r) {
        Map<String, Object> ped = s.pedido(codigo);
        if (ped != null && (ped.get("email").equals(usuario(h).email) || adm(h))) {
            s.confirmarPix(codigo);
            h.setAttribute("usuario", s.usuario(usuario(h).email));
            r.addFlashAttribute("ok", "Pagamento confirmado! Seu cashback foi creditado.");
        }
        return "redirect:/pedido/" + codigo;
    }

    // ---- Administração ----
    private static String dataUri(MultipartFile f) throws Exception { return Imagens.reduzir(f); }

    @GetMapping("/admin")
    String admin(Model m) { m.addAttribute("produtos", s.listar()); m.addAttribute("pedidos", s.pedidos()); return "admin"; }

    @PostMapping("/admin/produto")
    String novo(@RequestParam String nome, @RequestParam String categoria, @RequestParam double preco,
                @RequestParam long estoque, @RequestParam(required = false) MultipartFile imagem, RedirectAttributes r) throws Exception {
        Produto p = new Produto(nome, categoria, "🛒", preco, estoque);
        p.imagem = dataUri(imagem);
        s.salvar(p);
        r.addFlashAttribute("ok", "Produto cadastrado.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/editar/{id}")
    String editar(@PathVariable String id, @RequestParam String nome, @RequestParam String categoria, @RequestParam double preco,
                  @RequestParam long estoque, @RequestParam(required = false) MultipartFile imagem, RedirectAttributes r) throws Exception {
        s.editar(id, nome, categoria, preco, estoque, dataUri(imagem));
        r.addFlashAttribute("ok", "Produto atualizado.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/excluir/{id}")
    String excluir(@PathVariable String id, RedirectAttributes r) { s.excluir(id); r.addFlashAttribute("ok", "Produto excluído."); return "redirect:/admin"; }
}
