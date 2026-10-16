package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private AuditoriaService auditoriaService;
    @InjectMocks private PedidoService service;

    @BeforeEach
    void entrar() {
        var usuario = new UsernamePasswordAuthenticationToken("atendente01", null,
                List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
        SecurityContextHolder.getContext().setAuthentication(usuario);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void calcular() {
        cliente();
        Produto pizza = produto("p1", 19.90);
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(produto("p2", 7.50)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido salvo = invocation.getArgument(0);
            salvo.setId("novo-id");
            return salvo;
        });

        Pedido requisicao = pedido();
        ItemPedido bebida = new ItemPedido();
        bebida.setProdutoId("p2");
        bebida.setQuantidade(1);
        requisicao.setItens(List.of(requisicao.getItens().get(0), bebida));
        requisicao.setId("id-forjado");
        requisicao.setClienteNome("nome-forjado");
        requisicao.setStatus("FINALIZADO");
        requisicao.setResponsavel("outro-usuario");
        requisicao.setEntrada(LocalDateTime.of(2000, 1, 1, 0, 0));
        requisicao.setSaida(LocalDateTime.of(2000, 1, 2, 0, 0));
        requisicao.setResponsavelSaida("outro-usuario");
        requisicao.setJustificativaCancelamento("justificativa-forjada");
        requisicao.setTotal(new BigDecimal("0.01"));
        requisicao.setSubtotal(new BigDecimal("0.01"));
        requisicao.setDesconto(new BigDecimal("99"));
        requisicao.getItens().get(0).setPreco(new BigDecimal("0.01"));
        requisicao.getItens().get(0).setSubtotal(new BigDecimal("0.01"));

        Pedido salvo = service.salvar(requisicao);

        assertEquals("novo-id", salvo.getId());
        assertEquals("Cliente", salvo.getClienteNome());
        assertEquals(new BigDecimal("47.30"), salvo.getTotal());
        assertEquals(new BigDecimal("47.30"), salvo.getSubtotal());
        assertEquals(new BigDecimal("0.00"), salvo.getDesconto());
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("atendente01", salvo.getResponsavel());
        assertNotNull(salvo.getEntrada());
        assertNotEquals(requisicao.getEntrada(), salvo.getEntrada());
        assertNull(salvo.getSaida());
        assertNull(salvo.getResponsavelSaida());
        assertNull(salvo.getJustificativaCancelamento());
        assertEquals(new BigDecimal("19.90"), salvo.getItens().get(0).getPreco());
        assertEquals(new BigDecimal("39.80"), salvo.getItens().get(0).getSubtotal());
        assertNotSame(requisicao, salvo);
        assertNotSame(requisicao.getItens().get(0), salvo.getItens().get(0));
        pizza.setPreco(1.0);
        assertEquals(new BigDecimal("19.90"), salvo.getItens().get(0).getPreco());
        verify(auditoriaService).registrar(eq("INCLUSAO"), eq("PEDIDO"), eq("novo-id"),
                argThat(antes -> antes.isEmpty()),
                argThat(depois -> "47.30".equals(depois.get("total"))
                        && "19.90".equals(depois.get("item 1 preco"))), isNull());
    }

    @Test
    void itens() {
        Pedido requisicao = pedido();
        requisicao.setItens(List.of());
        rejeitar(requisicao);
        requisicao.setItens(null);
        rejeitar(requisicao);
        verifyNoInteractions(clienteRepository, produtoRepository);
    }

    @Test
    void quantidade() {
        cliente();
        for (Integer quantidade : Arrays.asList(null, 0, -1)) {
            Pedido requisicao = pedido();
            requisicao.getItens().get(0).setQuantidade(quantidade);
            rejeitar(requisicao);
        }
        verifyNoInteractions(produtoRepository);
    }

    @Test
    void produtoInvalido() {
        cliente();
        rejeitar(pedido());
        Produto inativo = produto("p1", 10.0);
        inativo.setAtivo(false);
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(inativo));
        rejeitar(pedido());
    }

    @Test
    void preco() {
        cliente();
        Produto produto = produto("p1", 10.0);
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto));
        for (Double preco : Arrays.asList(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 0.001)) {
            produto.setPreco(preco);
            rejeitar(pedido());
        }
    }

    @Test
    void clienteAusente() {
        rejeitar(pedido());
        Pedido requisicao = pedido();
        requisicao.setClienteId(null);
        rejeitar(requisicao);
        verifyNoInteractions(produtoRepository);
    }

    @Test
    void status() {
        Pedido pedido = new Pedido();
        pedido.setId("1");
        pedido.setStatus("RECEBIDO");
        when(pedidoRepository.findById("1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        service.alterarStatus("1", "EM_PREPARACAO");

        verify(auditoriaService).registrar(eq("MUDANCA_STATUS"), eq("PEDIDO"), eq("1"),
                argThat(antes -> "RECEBIDO".equals(antes.get("status"))),
                argThat(depois -> "EM_PREPARACAO".equals(depois.get("status"))), isNull());
    }

    @Test
    void fluxo() {
        var gerente = new UsernamePasswordAuthenticationToken("gerente01", null,
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
        SecurityContextHolder.getContext().setAuthentication(gerente);
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 16, 12, 0);

        for (String status : List.of("SAIU_PARA_ENTREGA", "RETIRADO")) {
            Pedido pedido = new Pedido();
            pedido.setId("1");
            pedido.setStatus("RECEBIDO");
            pedido.setEntrada(entrada);
            pedido.setResponsavel("atendente01");
            when(pedidoRepository.findById("1")).thenReturn(Optional.of(pedido));
            when(pedidoRepository.save(pedido)).thenReturn(pedido);

            assertEquals("EM_PREPARACAO", service.alterarStatus("1", "EM_PREPARACAO").getStatus());
            assertEquals("PRONTO", service.alterarStatus("1", "PRONTO").getStatus());
            assertNull(pedido.getSaida());
            assertNull(pedido.getResponsavelSaida());

            LocalDateTime antes = LocalDateTime.now();
            assertEquals(status, service.alterarStatus("1", status).getStatus());
            LocalDateTime saida = pedido.getSaida();
            assertNotNull(saida);
            assertFalse(saida.isBefore(antes));
            assertFalse(saida.isAfter(LocalDateTime.now()));
            assertEquals("gerente01", pedido.getResponsavelSaida());
            verify(auditoriaService).registrar(eq("MUDANCA_STATUS"), eq("PEDIDO"), eq("1"),
                    argThat(dados -> "PRONTO".equals(dados.get("status")) && dados.get("saida") == null),
                    argThat(dados -> status.equals(dados.get("status"))
                            && saida.toString().equals(dados.get("saida"))
                            && "gerente01".equals(dados.get("responsavelSaida"))), isNull());

            assertEquals("FINALIZADO", service.alterarStatus("1", "FINALIZADO").getStatus());
            assertEquals(saida, pedido.getSaida());
            assertEquals("gerente01", pedido.getResponsavelSaida());
            assertEquals(entrada, pedido.getEntrada());
            assertEquals("atendente01", pedido.getResponsavel());
        }
        verify(pedidoRepository, times(8)).save(any(Pedido.class));
    }

    @Test
    void statusInvalido() {
        String[][] casos = {
            {"RECEBIDO", "PRONTO"}, {"RECEBIDO", "FINALIZADO"},
            {"RECEBIDO", "RECEBIDO"}, {"RECEBIDO", "CANCELADO"},
            {"EM_PREPARACAO", "RECEBIDO"}, {"EM_PREPARACAO", "SAIU_PARA_ENTREGA"},
            {"PRONTO", "EM_PREPARACAO"}, {"PRONTO", "FINALIZADO"},
            {"SAIU_PARA_ENTREGA", "RETIRADO"}, {"RETIRADO", "SAIU_PARA_ENTREGA"},
            {"FINALIZADO", "RECEBIDO"}, {"FINALIZADO", "FINALIZADO"},
            {"CANCELADO", "RECEBIDO"}, {"CANCELADO", "EM_PREPARACAO"},
            {"RECEBIDO", "DESCONHECIDO"}, {"RECEBIDO", null}, {null, "EM_PREPARACAO"}
        };
        for (String[] caso : casos) {
            Pedido pedido = new Pedido();
            pedido.setStatus(caso[0]);
            when(pedidoRepository.findById("1")).thenReturn(Optional.of(pedido));

            ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                    () -> service.alterarStatus("1", caso[1]));

            assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
            assertEquals(caso[0], pedido.getStatus());
            assertNull(pedido.getSaida());
            assertNull(pedido.getResponsavelSaida());
        }
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void desconto() {
        Pedido pedido = preparar();
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        Pedido salvo = service.desconto("1", new BigDecimal("20"));

        assertEquals(new BigDecimal("72.00"), salvo.getTotal());
        assertEquals(new BigDecimal("90.00"), salvo.getSubtotal());
        assertEquals("RECEBIDO", salvo.getStatus());
        verify(auditoriaService).registrar(eq("DESCONTO"), eq("PEDIDO"), eq("1"),
                argThat(dados -> "0.00".equals(dados.get("desconto")) && "90.00".equals(dados.get("total"))),
                argThat(dados -> "20".equals(dados.get("desconto")) && "72.00".equals(dados.get("total"))), isNull());

        assertEquals(new BigDecimal("81.00"), service.desconto("1", new BigDecimal("10")).getTotal());
        assertEquals(new BigDecimal("78.75"), service.desconto("1", new BigDecimal("12.5")).getTotal());
        assertEquals(new BigDecimal("90.00"), service.desconto("1", BigDecimal.ZERO).getTotal());
        pedido.setSubtotal(new BigDecimal("19.90"));
        assertEquals(new BigDecimal("18.91"), service.desconto("1", new BigDecimal("5")).getTotal());
    }

    @Test
    void descontoInvalido() {
        for (BigDecimal desconto : Arrays.asList(null, new BigDecimal("-0.01"), new BigDecimal("20.001"))) {
            ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                    () -> service.desconto("1", desconto));
            assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        }
        verifyNoInteractions(pedidoRepository, auditoriaService);
    }

    @Test
    void semSubtotal() {
        Pedido pedido = preparar();
        for (BigDecimal subtotal : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"))) {
            pedido.setSubtotal(subtotal);
            assertThrows(ResponseStatusException.class, () -> service.desconto("1", BigDecimal.TEN));
        }
        assertEquals(new BigDecimal("90.00"), pedido.getTotal());
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void cancelar() {
        Pedido pedido = preparar();
        when(pedidoRepository.save(pedido)).thenReturn(pedido);
        for (String status : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO")) {
            pedido.setStatus(status);
            Pedido salvo = service.cancelar("1", "  Cliente desistiu  ");
            assertEquals("CANCELADO", salvo.getStatus());
            assertEquals("Cliente desistiu", salvo.getJustificativaCancelamento());
            assertEquals(new BigDecimal("90.00"), salvo.getTotal());
            assertNull(salvo.getSaida());
            assertNull(salvo.getResponsavelSaida());
            verify(auditoriaService).registrar(eq("CANCELAMENTO"), eq("PEDIDO"), eq("1"),
                    argThat(dados -> status.equals(dados.get("status"))),
                    argThat(dados -> "CANCELADO".equals(dados.get("status"))
                            && "Cliente desistiu".equals(dados.get("justificativaCancelamento"))), eq("Cliente desistiu"));
        }
    }

    @Test
    void semJustificativa() {
        for (String motivo : Arrays.asList(null, "", " \t\n")) {
            ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                    () -> service.cancelar("1", motivo));
            assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        }
        verifyNoInteractions(pedidoRepository, auditoriaService);
    }

    @Test
    void bloqueados() {
        Pedido pedido = preparar();
        pedido.setJustificativaCancelamento("Motivo original");
        for (String status : Arrays.asList("SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO", "ENTREGUE", null)) {
            pedido.setStatus(status);
            assertThrows(ResponseStatusException.class, () -> service.desconto("1", BigDecimal.TEN));
            assertThrows(ResponseStatusException.class, () -> service.cancelar("1", "Outro motivo"));
            assertEquals(status, pedido.getStatus());
            assertEquals("Motivo original", pedido.getJustificativaCancelamento());
            assertEquals(new BigDecimal("0.00"), pedido.getDesconto());
            assertEquals(new BigDecimal("90.00"), pedido.getTotal());
        }
        pedido.setStatus("PRONTO");
        pedido.setSaida(LocalDateTime.now());
        assertThrows(ResponseStatusException.class, () -> service.desconto("1", BigDecimal.TEN));
        assertThrows(ResponseStatusException.class, () -> service.cancelar("1", "Outro motivo"));
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void pedidoAusente() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.alterarStatus("ausente", "EM_PREPARACAO"));
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.desconto("ausente", BigDecimal.TEN)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.cancelar("ausente", "Cliente desistiu")).getStatusCode());
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void semLogin() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.salvar(pedido()));
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.alterarStatus("1", "EM_PREPARACAO"));
        verifyNoInteractions(pedidoRepository, clienteRepository, produtoRepository, auditoriaService);
    }

    private Pedido preparar() {
        Pedido pedido = new Pedido();
        pedido.setId("1");
        pedido.setStatus("RECEBIDO");
        pedido.setSubtotal(new BigDecimal("90.00"));
        pedido.setTotal(new BigDecimal("90.00"));
        pedido.setDesconto(new BigDecimal("0.00"));
        when(pedidoRepository.findById("1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private Pedido pedido() {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("p1");
        item.setQuantidade(2);
        Pedido pedido = new Pedido();
        pedido.setClienteId("c1");
        pedido.setItens(List.of(item));
        return pedido;
    }

    private Produto produto(String id, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(id);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private void cliente() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Cliente");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
    }

    private void rejeitar(Pedido pedido) {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class, () -> service.salvar(pedido));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }
}
