package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class})
class DashboardControllerTest {

    private static final String PERIODO = "?inicio=2026-11-01&fim=2026-11-03";
    private static final LocalDateTime INICIO = LocalDateTime.parse("2026-11-01T00:00:00");
    private static final LocalDateTime FIM_EXCLUSIVO = LocalDateTime.parse("2026-11-04T00:00:00");

    @Autowired private MockMvc mvc;
    @MockitoBean private PedidoRepository pedidoRepository;
    @MockitoBean private UsuarioDetailsService usuarioDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void resumoUsaTotalComDescontoENormalizaPedidosAntigos() throws Exception {
        Pedido finalizado = pedido("1", "FINALIZADO", "90.00", "2026-11-03T23:59:59");
        finalizado.setSubtotal(new BigDecimal("100.00"));
        finalizado.setItens(List.of(item("pizza", "Margherita", 2), item("bebida", "Suco", 1)));
        Pedido entregue = pedido("2", "ENTREGUE", "50.00", "2026-11-03T12:00:00");
        entregue.setItens(List.of(item("pizza", "Nome antigo", 1)));
        Pedido cancelado = pedido("3", "CANCELADO", "60.00", "2026-11-01T18:00:00");
        cancelado.setItens(List.of(item("pizza", "Margherita", 10)));
        Pedido emPreparo = pedido("4", "EM_PREPARO", "40.00", "2026-11-01T12:00:00");
        Pedido semTotal = pedido("5", "ENTREGUE", null, "2026-11-01T00:00:00");
        semTotal.setItens(null);
        simularPedidos(List.of(finalizado, entregue, cancelado, emPreparo, semTotal));

        mvc.perform(get("/api/dashboard/resumo" + PERIODO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(5))
                .andExpect(jsonPath("$.pedidosFinalizados").value(3))
                .andExpect(jsonPath("$.faturamento").value(140.0))
                .andExpect(jsonPath("$.ticketMedio").value(70.0))
                .andExpect(jsonPath("$.porStatus.CANCELADO").value(1))
                .andExpect(jsonPath("$.porStatus.EM_PREPARACAO").value(1))
                .andExpect(jsonPath("$.serieDiaria[1].data").value("2026-11-02"))
                .andExpect(jsonPath("$.serieDiaria[1].pedidos").value(0))
                .andExpect(jsonPath("$.serieDiaria[2].faturamento").value(140.0))
                .andExpect(jsonPath("$.ranking[0].nome").value("Margherita"))
                .andExpect(jsonPath("$.ranking[0].quantidade").value(3))
                .andExpect(jsonPath("$.ranking[1].nome").value("Suco"));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void filtraStatusAntesDeLimitarEExibeApenasCamposDoPainel() throws Exception {
        List<Pedido> pedidos = IntStream.range(0, 12)
                .mapToObj(i -> pedido("p" + i, i == 0 ? "RECEBIDO" : "ENTREGUE", "50.00", "2026-11-03T12:00:00"))
                .toList();
        pedidos.get(1).setClienteNome("Cliente fictício");
        simularPedidos(pedidos);

        mvc.perform(get("/api/dashboard/pedidos" + PERIODO + "&status=FINALIZADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("p1"))
                .andExpect(jsonPath("$[0].status").value("FINALIZADO"))
                .andExpect(jsonPath("$[0].clienteNome").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void devolveResumoZeradoParaPeriodoVazio() throws Exception {
        simularPedidos(List.of());
        mvc.perform(get("/api/dashboard/resumo" + PERIODO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(0.0))
                .andExpect(jsonPath("$.ticketMedio").value(0.0))
                .andExpect(jsonPath("$.serieDiaria.length()").value(3))
                .andExpect(jsonPath("$.ranking").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejeitaFiltrosInvalidosAntesDeConsultarBanco() throws Exception {
        for (String filtro : List.of("?inicio=2026-11-01", "?inicio=2026-11-31&fim=2026-12-01",
                "?inicio=2026-11-03&fim=2026-11-01", "?inicio=2026-01-01&fim=2026-11-01")) {
            mvc.perform(get("/api/dashboard/resumo" + filtro))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        mvc.perform(get("/api/dashboard/pedidos" + PERIODO + "&status=INVALIDO"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void indicaBancoIndisponivel() throws Exception {
        when(pedidoRepository.buscarPorPeriodo(INICIO, FIM_EXCLUSIVO))
                .thenThrow(new DataAccessResourceFailureException("Falha de conexão"));
        mvc.perform(get("/api/dashboard/resumo" + PERIODO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void restringeRotasAAdminEGerenteEmModoConsulta() throws Exception {
        for (String rota : List.of("/api/dashboard/resumo", "/api/dashboard/pedidos")) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
            mvc.perform(get(rota).with(user("teste").roles("ATENDENTE"))).andExpect(status().isForbidden());
            mvc.perform(post(rota).with(user("teste").roles("GERENTE")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(pedidoRepository);
    }

    private void simularPedidos(List<Pedido> pedidos) {
        when(pedidoRepository.buscarPorPeriodo(INICIO, FIM_EXCLUSIVO)).thenReturn(pedidos);
    }

    private Pedido pedido(String id, String status, String total, String entrada) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setStatus(status);
        pedido.setTotal(total == null ? null : new BigDecimal(total));
        pedido.setEntrada(LocalDateTime.parse(entrada));
        return pedido;
    }

    private ItemPedido item(String id, String nome, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(id);
        item.setNome(nome);
        item.setQuantidade(quantidade);
        return item;
    }
}
