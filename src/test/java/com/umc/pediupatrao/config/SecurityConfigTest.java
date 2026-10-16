package com.umc.pediupatrao.config;

import com.umc.pediupatrao.controller.*;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.service.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, UsuarioController.class, ClienteController.class, PedidoController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mvc;
    @MockBean private UsuarioDetailsService usuarioDetailsService;
    @MockBean private UsuarioService usuarioService;
    @MockBean private ClienteService clienteService;
    @MockBean private PedidoService pedidoService;
    @MockBean private ProdutoService produtoService;
    @MockBean private AuditoriaService auditoriaService;

    @Test
    void auditoriaSomenteParaConsultaDeAdminEGerente() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            int esperado = "ATENDENTE".equals(perfil) ? 403 : 200;
            mvc.perform(get("/api/auditoria").with(user("teste").roles(perfil)))
                    .andExpect(status().is(esperado));
            mvc.perform(get("/auditoria").with(user("teste").roles(perfil)))
                    .andExpect(status().is(esperado));
            mvc.perform(post("/api/auditoria").with(user("teste").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/auditoria/1").with(user("teste").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/auditoria/1").with(user("teste").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void telaDeAuditoriaExibeAlteracoesSemInterpretarHtml() throws Exception {
        Auditoria registro = new Auditoria();
        registro.setUsuario("atendente01");
        registro.setPerfil("ATENDENTE");
        registro.setDataHora(LocalDateTime.of(2026, 10, 16, 14, 30));
        registro.setOperacao("ALTERACAO");
        registro.setEntidade("CLIENTE");
        registro.setEntidadeId("1");
        registro.setValoresAnteriores(java.util.Map.of("telefone", "1111"));
        registro.setValoresNovos(java.util.Map.of("telefone", "<b>2222</b>"));
        when(auditoriaService.listar()).thenReturn(List.of(registro));

        mvc.perform(get("/auditoria").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("16/10/2026 14:30:00")))
                .andExpect(content().string(containsString("atendente01")))
                .andExpect(content().string(containsString("1111")))
                .andExpect(content().string(containsString("&lt;b&gt;2222&lt;/b&gt;")));
    }

    @Test
    void formularioPedido() throws Exception {
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            mvc.perform(get("/pedidos/novo").with(user("teste").roles(perfil)))
                    .andExpect(status().is("ADMIN".equals(perfil) ? 403 : 200));
            var resposta = mvc.perform(post("/pedidos/salvar").with(user("teste").roles(perfil)).with(csrf())
                    .param("clienteId", "c1").param("itens[0].produtoId", "p1")
                    .param("itens[0].quantidade", "2"));
            if ("ADMIN".equals(perfil)) {
                resposta.andExpect(status().isForbidden());
            } else {
                resposta.andExpect(redirectedUrl("/pedidos"));
            }
        }
    }

    @Test
    void listar() throws Exception {
        ItemPedido item = new ItemPedido();
        item.setNome("Pizza");
        item.setQuantidade(2);
        item.setPreco(new BigDecimal("19.90"));
        item.setSubtotal(new BigDecimal("39.80"));
        Pedido novo = new Pedido();
        novo.setItens(List.of(item));
        novo.setClienteNome("Cliente");
        novo.setTotal(new BigDecimal("39.80"));
        novo.setSubtotal(new BigDecimal("39.80"));
        novo.setDesconto(BigDecimal.ZERO);
        novo.setEntrada(LocalDateTime.of(2026, 10, 16, 15, 0));
        novo.setResponsavel("atendente01");
        novo.setStatus("FINALIZADO");
        novo.setSaida(LocalDateTime.of(2026, 10, 16, 16, 30));
        novo.setResponsavelSaida("gerente01");
        Pedido antigo = new Pedido();
        antigo.setSabor("Calabresa antiga");
        antigo.setQuantidade(1);
        when(pedidoService.listar()).thenReturn(List.of(novo, antigo));

        mvc.perform(get("/pedidos").with(user("teste").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("39,80")))
                .andExpect(content().string(containsString("Calabresa antiga")))
                .andExpect(content().string(containsString("atendente01")))
                .andExpect(content().string(containsString("16/10/2026 16:30:00")))
                .andExpect(content().string(containsString("gerente01")));
    }

    @Test
    void produtosNoFormulario() throws Exception {
        Produto ativo = new Produto();
        ativo.setId("p1");
        ativo.setNome("Produto ativo");
        ativo.setPreco(10.0);
        ativo.setAtivo(true);
        Produto inativo = new Produto();
        inativo.setId("p2");
        inativo.setNome("Produto inativo");
        inativo.setAtivo(false);
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Cliente");
        when(produtoService.listarProdutos()).thenReturn(List.of(ativo, inativo));
        when(clienteService.listarClientes()).thenReturn(List.of(cliente));

        mvc.perform(get("/pedidos/novo").with(user("teste").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Produto ativo - R$ 10,00")))
                .andExpect(content().string(not(containsString("Produto inativo"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void cadastrarProduto() throws Exception {
        mvc.perform(get("/produtos").with(user("teste").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"id\""))));
        mvc.perform(post("/produtos/salvar").with(user("teste").roles("GERENTE")).with(csrf())
                .param("nome", "Pizza").param("tipo", "Pizza").param("preco", "19.90").param("ativo", "true"))
                .andExpect(redirectedUrl("/produtos"));
        verify(produtoService).salvar(argThat(produto -> "Pizza".equals(produto.getNome())
                && Double.valueOf(19.90).equals(produto.getPreco()) && Boolean.TRUE.equals(produto.getAtivo())));
    }

    @Test
    void editarProduto() throws Exception {
        Produto produto = new Produto();
        produto.setId("p1");
        produto.setNome("Pizza");
        produto.setTipo("Pizza");
        produto.setPreco(19.90);
        produto.setAtivo(true);
        when(produtoService.buscar("p1")).thenReturn(Optional.of(produto));
        when(produtoService.listarProdutos()).thenReturn(List.of(produto));

        mvc.perform(get("/produtos/editar/p1").with(user("teste").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("produto", produto))
                .andExpect(content().string(containsString("Editar produto")))
                .andExpect(content().string(containsString("value=\"p1\"")))
                .andExpect(content().string(containsString("action=\"/produtos/excluir/p1\"")));
        mvc.perform(post("/produtos/salvar").with(user("teste").roles("GERENTE")).with(csrf())
                .param("id", "p1").param("nome", "Pizza alterada").param("tipo", "Pizza")
                .param("preco", "25.00").param("_ativo", "on"))
                .andExpect(redirectedUrl("/produtos"));
        verify(produtoService).salvar(argThat(salvo -> "p1".equals(salvo.getId())
                && "Pizza alterada".equals(salvo.getNome()) && Double.valueOf(25.0).equals(salvo.getPreco())
                && Boolean.FALSE.equals(salvo.getAtivo())));
    }

    @Test
    void excluirProduto() throws Exception {
        mvc.perform(post("/produtos/excluir/p1").with(user("teste").roles("GERENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/produtos/excluir/p1").with(user("teste").roles("GERENTE")).with(csrf()))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("sucesso", "Produto excluído com sucesso!"));
        verify(produtoService).excluir("p1");
    }

    @Test
    void produtoAusente() throws Exception {
        mvc.perform(get("/produtos/editar/ausente").with(user("teste").roles("GERENTE")))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));
    }

    @Test
    void usuariosExigemLoginEPerfilAdmin() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        for (String perfil : List.of("GERENTE", "ATENDENTE")) {
            mvc.perform(get("/api/usuarios").with(user("teste").roles(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/usuarios").with(user("teste").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"teste\",\"password\":\"123\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/salvar").with(user("teste").roles(perfil)).with(csrf())
                    .param("username", "teste").param("password", "123").param("role", "ADMIN"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void somenteGerenteAlteraStatusDoPedido() throws Exception {
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            mvc.perform(get("/api/pedidos").with(user("teste").roles(perfil)))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/pedidos/1/status").param("status", "EM_PREPARACAO")
                    .with(user("teste").roles(perfil)).with(csrf()))
                    .andExpect(status().is("GERENTE".equals(perfil) ? 200 : 403));
            var resposta = mvc.perform(post("/pedidos/1/status").param("status", "EM_PREPARACAO")
                    .with(user("teste").roles(perfil)).with(csrf()));
            if ("GERENTE".equals(perfil)) {
                resposta.andExpect(redirectedUrl("/pedidos"))
                        .andExpect(flash().attribute("sucesso", "Status atualizado com sucesso!"));
            } else {
                resposta.andExpect(status().isForbidden());
            }
            mvc.perform(post("/api/pedidos").with(user("teste").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is("ADMIN".equals(perfil) ? 403 : 200));
        }
    }

    @Test
    void botoes() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("1");
        when(pedidoService.listar()).thenReturn(List.of(pedido));
        String[][] etapas = {
            {"RECEBIDO", "EM_PREPARACAO"}, {"EM_PREPARACAO", "PRONTO"},
            {"PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO"},
            {"SAIU_PARA_ENTREGA", "FINALIZADO"}, {"RETIRADO", "FINALIZADO"}
        };
        for (String[] etapa : etapas) {
            pedido.setStatus(etapa[0]);
            var resposta = mvc.perform(get("/pedidos").with(user("teste").roles("GERENTE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("action=\"/pedidos/1/status\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
            String html = resposta.andReturn().getResponse().getContentAsString();
            assertEquals(etapa.length - 1, StringUtils.countOccurrencesOf(html, "name=\"status\""));
            for (int i = 1; i < etapa.length; i++) {
                resposta.andExpect(content().string(containsString("value=\"" + etapa[i] + "\"")));
            }
        }
        for (String perfil : List.of("ADMIN", "ATENDENTE")) {
            mvc.perform(get("/pedidos").with(user("teste").roles(perfil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/status\""))));
        }
        for (String status : List.of("FINALIZADO", "CANCELADO", "ENTREGUE", "EM_PREPARO")) {
            pedido.setStatus(status);
            mvc.perform(get("/pedidos").with(user("teste").roles("GERENTE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/status\""))));
        }
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void erroDeStatus() throws Exception {
        when(pedidoService.alterarStatus("1", "FINALIZADO"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mudança de status não permitida."));
        mvc.perform(post("/pedidos/1/status").with(csrf()).param("status", "FINALIZADO"))
                .andExpect(redirectedUrl("/pedidos"))
                .andExpect(flash().attribute("erro", "Mudança de status não permitida."));
    }

    @Test
    void permissoes() throws Exception {
        for (String operacao : List.of("desconto", "cancelar")) {
            String campo = "desconto".equals(operacao) ? "desconto" : "justificativa";
            String valor = "desconto".equals(operacao) ? "20" : "Cliente desistiu";
            mvc.perform(put("/api/pedidos/1/" + operacao).with(csrf()).param(campo, valor))
                    .andExpect(status().isUnauthorized());
            for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
                mvc.perform(put("/api/pedidos/1/" + operacao).with(user("teste").roles(perfil))
                        .with(csrf()).param(campo, valor))
                        .andExpect(status().is("GERENTE".equals(perfil) ? 200 : 403));
                var resposta = mvc.perform(post("/pedidos/1/" + operacao).with(user("teste").roles(perfil))
                        .with(csrf()).param(campo, valor));
                if ("GERENTE".equals(perfil)) {
                    resposta.andExpect(redirectedUrl("/pedidos"));
                } else {
                    resposta.andExpect(status().isForbidden());
                }
            }
        }
    }

    @Test
    void formularios() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("1");
        pedido.setSubtotal(new BigDecimal("90.00"));
        pedido.setDesconto(BigDecimal.ZERO);
        pedido.setStatus("RECEBIDO");
        when(pedidoService.listar()).thenReturn(List.of(pedido));
        for (String status : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO")) {
            pedido.setStatus(status);
            mvc.perform(get("/pedidos").with(user("teste").roles("GERENTE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("action=\"/pedidos/1/desconto\"")))
                    .andExpect(content().string(containsString("action=\"/pedidos/1/cancelar\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
        for (String perfil : List.of("ADMIN", "ATENDENTE")) {
            mvc.perform(get("/pedidos").with(user("teste").roles(perfil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/desconto\""))))
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/cancelar\""))));
        }
        for (String status : List.of("SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO")) {
            pedido.setStatus(status);
            mvc.perform(get("/pedidos").with(user("teste").roles("GERENTE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/desconto\""))))
                    .andExpect(content().string(not(containsString("action=\"/pedidos/1/cancelar\""))));
        }
        pedido.setJustificativaCancelamento("<b>Cliente desistiu</b>");
        mvc.perform(get("/pedidos").with(user("teste").roles("GERENTE")))
                .andExpect(content().string(containsString("&lt;b&gt;Cliente desistiu&lt;/b&gt;")));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void erros() throws Exception {
        when(pedidoService.desconto("1", new BigDecimal("21")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "O desconto deve estar entre 0 e 20%."));
        when(pedidoService.cancelar("1", " "))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a justificativa do cancelamento."));
        mvc.perform(post("/pedidos/1/desconto").with(csrf()).param("desconto", "21"))
                .andExpect(redirectedUrl("/pedidos"))
                .andExpect(flash().attribute("erro", "O desconto deve estar entre 0 e 20%."));
        mvc.perform(post("/pedidos/1/cancelar").with(csrf()).param("justificativa", " "))
                .andExpect(redirectedUrl("/pedidos"))
                .andExpect(flash().attribute("erro", "Informe a justificativa do cancelamento."));
    }

    @Test
    void atendenteAlteraClienteEGerenteApenasConsulta() throws Exception {
        mvc.perform(get("/api/clientes").with(user("gerente").roles("GERENTE")))
                .andExpect(status().isOk());
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            mvc.perform(put("/api/clientes/1").with(user("teste").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                    .andExpect(status().is("ATENDENTE".equals(perfil) ? 200 : 403));
            mvc.perform(delete("/api/clientes/1").with(user("teste").roles(perfil)).with(csrf()))
                    .andExpect(status().is("ADMIN".equals(perfil) ? 204 : 403));
        }
    }

    @Test
    void operacaoSemCsrfOuComTokenInvalidoEhBloqueada() throws Exception {
        for (String operacao : List.of("desconto", "cancelar")) {
            String campo = "desconto".equals(operacao) ? "desconto" : "justificativa";
            String valor = "desconto".equals(operacao) ? "10" : "Cliente desistiu";
            mvc.perform(post("/pedidos/1/" + operacao).with(user("teste").roles("GERENTE")).param(campo, valor))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/pedidos/1/" + operacao).with(user("teste").roles("GERENTE")).param(campo, valor))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/pedidos/1/status").with(user("teste").roles("GERENTE"))
                .param("status", "EM_PREPARACAO"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/status").with(user("teste").roles("GERENTE"))
                .param("status", "EM_PREPARACAO"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").with(user("teste").roles("ATENDENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").with(user("teste").roles("ATENDENTE"))
                .with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void senhaNaoApareceNaApiNemNoFormularioDeEdicao() throws Exception {
        Usuario cadastro = new Usuario();
        cadastro.setId("1");
        cadastro.setUsername("teste");
        cadastro.setRole("ATENDENTE");
        cadastro.setPassword("hash-que-nao-deve-aparecer");
        when(usuarioService.listarTodos()).thenReturn(List.of(cadastro));
        when(usuarioService.buscarPorId("1")).thenReturn(Optional.of(cadastro));
        when(usuarioService.salvarUsuario(any(Usuario.class))).thenReturn(cadastro);

        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"teste\",\"password\":\"123\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/usuarios/editar/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(cadastro.getPassword()))))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void formularioDeLoginTemCsrfEMenuRespeitaPerfil() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("href=\"/usuarios\"")));
        for (String perfil : List.of("GERENTE", "ATENDENTE")) {
            mvc.perform(get("/").with(user("teste").roles(perfil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("href=\"/usuarios\""))));
        }
    }
}
