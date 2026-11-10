package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    public static final List<String> STATUS = List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO",
            "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO", "OUTROS");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final PedidoRepository pedidoRepository;

    public DashboardService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public Resumo resumo(LocalDate inicio, LocalDate fim) {
        List<Pedido> pedidos = consultarPeriodo(inicio, fim);
        Map<LocalDate, Dia> dias = new LinkedHashMap<>();
        inicio.datesUntil(fim.plusDays(1)).forEach(dia -> dias.put(dia, new Dia(dia, 0, ZERO)));
        Map<String, Long> porStatus = new LinkedHashMap<>();
        STATUS.forEach(status -> porStatus.put(status, 0L));
        Map<String, ProdutoVendido> porProduto = new LinkedHashMap<>();

        BigDecimal faturamento = ZERO;
        int finalizadosComTotal = 0;
        for (Pedido pedido : pedidos) {
            String status = statusDoPedido(pedido);
            porStatus.merge(status, 1L, Long::sum);
            BigDecimal venda = ZERO;
            if ("FINALIZADO".equals(status)) {
                if (pedido.getTotal() != null) {
                    venda = pedido.getTotal();
                    faturamento = faturamento.add(venda);
                    finalizadosComTotal++;
                }
                contarItens(pedido, porProduto);
            }
            LocalDate data = pedido.getEntrada().toLocalDate();
            Dia dia = dias.get(data);
            dias.put(data, new Dia(data, dia.pedidos() + 1, dia.faturamento().add(venda)));
        }

        BigDecimal ticketMedio = finalizadosComTotal == 0 ? ZERO
                : faturamento.divide(BigDecimal.valueOf(finalizadosComTotal), 2, RoundingMode.HALF_UP);
        List<ProdutoVendido> ranking = porProduto.values().stream()
                .sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                        .thenComparing(ProdutoVendido::produtoId))
                .limit(5)
                .toList();
        return new Resumo(inicio, fim, faturamento, pedidos.size(), porStatus.get("FINALIZADO"),
                ticketMedio, List.copyOf(dias.values()), porStatus, ranking);
    }

    public List<PedidoRecente> pedidosRecentes(LocalDate inicio, LocalDate fim, String status) {
        return consultarPeriodo(inicio, fim).stream()
                .filter(pedido -> status == null || status.equals(statusDoPedido(pedido)))
                .limit(10)
                .map(pedido -> new PedidoRecente(pedido.getId(), pedido.getEntrada(),
                        statusDoPedido(pedido), pedido.getTotal()))
                .toList();
    }

    private List<Pedido> consultarPeriodo(LocalDate inicio, LocalDate fim) {
        return pedidoRepository.buscarPorPeriodo(inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
    }

    private void contarItens(Pedido pedido, Map<String, ProdutoVendido> porProduto) {
        if (pedido.getItens() == null) {
            return;
        }
        for (ItemPedido item : pedido.getItens()) {
            if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()
                    || item.getQuantidade() == null || item.getQuantidade() <= 0) {
                continue;
            }
            String nome = item.getNome() == null || item.getNome().isBlank()
                    ? item.getProdutoId() : item.getNome();
            ProdutoVendido produto = new ProdutoVendido(item.getProdutoId(), nome, item.getQuantidade());
            porProduto.merge(produto.produtoId(), produto, (anterior, atual) -> new ProdutoVendido(
                    anterior.produtoId(), anterior.nome(), anterior.quantidade() + atual.quantidade()));
        }
    }

    private String statusDoPedido(Pedido pedido) {
        String status = pedido.getStatus();
        if ("ENTREGUE".equals(status)) {
            return "FINALIZADO";
        }
        if ("EM_PREPARO".equals(status)) {
            return "EM_PREPARACAO";
        }
        return status != null && STATUS.contains(status) ? status : "OUTROS";
    }

    public record Resumo(LocalDate inicio, LocalDate fim, BigDecimal faturamento, int totalPedidos,
            long pedidosFinalizados, BigDecimal ticketMedio, List<Dia> serieDiaria,
            Map<String, Long> porStatus, List<ProdutoVendido> ranking) {}

    public record Dia(LocalDate data, int pedidos, BigDecimal faturamento) {}

    public record ProdutoVendido(String produtoId, String nome, long quantidade) {}

    public record PedidoRecente(String id, LocalDateTime entrada, String status, BigDecimal total) {}
}
