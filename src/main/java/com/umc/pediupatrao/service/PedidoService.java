package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido salvar(Pedido requisicao) {
        Authentication usuario = SecurityContextHolder.getContext().getAuthentication();
        if (usuario == null || !usuario.isAuthenticated() || usuario instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }
        if (requisicao.getClienteId() == null || requisicao.getClienteId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um cliente.");
        }
        if (requisicao.getItens() == null || requisicao.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adicione pelo menos um item.");
        }
        Cliente cliente = clienteRepository.findById(requisicao.getClienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente não encontrado."));

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (ItemPedido recebido : requisicao.getItens()) {
            if (recebido == null || recebido.getProdutoId() == null || recebido.getProdutoId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o produto de cada item.");
            }
            if (recebido.getQuantidade() == null || recebido.getQuantidade() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser maior que zero.");
            }
            Produto produto = produtoRepository.findById(recebido.getProdutoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto não encontrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O produto está inativo.");
            }
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O produto está com preço inválido.");
            }

            // O preço vem do banco; valores enviados pela requisição não são usados.
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            if (preco.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O preço mínimo é R$ 0,01.");
            }
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNome(produto.getNome());
            item.setQuantidade(recebido.getQuantidade());
            item.setPreco(preco);
            item.setSubtotal(preco.multiply(BigDecimal.valueOf(item.getQuantidade())));
            itens.add(item);
            subtotal = subtotal.add(item.getSubtotal());
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(itens);
        pedido.setSubtotal(subtotal);
        pedido.setDesconto(BigDecimal.ZERO.setScale(2));
        pedido.setTotal(subtotal);
        pedido.setStatus("RECEBIDO");
        pedido.setResponsavel(usuario.getName());
        pedido.setEntrada(LocalDateTime.now());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("INCLUSAO", "PEDIDO", salvo.getId(), Map.of(), dados(salvo), null);
        return salvo;
    }

    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido alterarStatus(String id, String status) {
        Authentication usuario = SecurityContextHolder.getContext().getAuthentication();
        if (usuario == null || !usuario.isAuthenticated() || usuario instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        String atual = pedido.getStatus();
        boolean permitido = false;
        if ("RECEBIDO".equals(atual)) {
            permitido = "EM_PREPARACAO".equals(status);
        } else if ("EM_PREPARACAO".equals(atual)) {
            permitido = "PRONTO".equals(status);
        } else if ("PRONTO".equals(atual)) {
            permitido = "SAIU_PARA_ENTREGA".equals(status) || "RETIRADO".equals(status);
        } else if ("SAIU_PARA_ENTREGA".equals(atual) || "RETIRADO".equals(atual)) {
            permitido = "FINALIZADO".equals(status);
        }
        if (!permitido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mudança de status não permitida.");
        }

        Map<String, String> anteriores = dados(pedido);
        pedido.setStatus(status);
        if ("SAIU_PARA_ENTREGA".equals(status) || "RETIRADO".equals(status)) {
            pedido.setSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(usuario.getName());
        }
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("MUDANCA_STATUS", "PEDIDO", salvo.getId(), anteriores, dados(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido desconto(String id, BigDecimal desconto) {
        if (desconto == null || desconto.signum() < 0 || desconto.compareTo(new BigDecimal("20")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O desconto deve estar entre 0 e 20%.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        validarPedido(pedido);
        if (pedido.getSubtotal() == null || pedido.getSubtotal().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido sem subtotal válido para desconto.");
        }
        Map<String, String> anteriores = dados(pedido);
        BigDecimal valor = pedido.getSubtotal().multiply(desconto).divide(new BigDecimal("100"));
        pedido.setDesconto(desconto);
        pedido.setTotal(pedido.getSubtotal().subtract(valor).setScale(2, RoundingMode.HALF_UP));
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("DESCONTO", "PEDIDO", salvo.getId(), anteriores, dados(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a justificativa do cancelamento.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        validarPedido(pedido);
        Map<String, String> anteriores = dados(pedido);
        pedido.setStatus("CANCELADO");
        pedido.setJustificativaCancelamento(justificativa.trim());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CANCELAMENTO", "PEDIDO", salvo.getId(), anteriores, dados(salvo),
                salvo.getJustificativaCancelamento());
        return salvo;
    }

    private void validarPedido(Pedido pedido) {
        String status = pedido.getStatus();
        if (pedido.getSaida() != null || !("RECEBIDO".equals(status)
                || "EM_PREPARACAO".equals(status) || "PRONTO".equals(status))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta operação só é permitida antes da saída de um pedido ativo.");
        }
    }

    private Map<String, String> dados(Pedido pedido) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("clienteId", pedido.getClienteId());
        dados.put("cliente", pedido.getClienteNome());
        dados.put("status", pedido.getStatus());
        dados.put("justificativaCancelamento", pedido.getJustificativaCancelamento());
        dados.put("subtotal", Objects.toString(pedido.getSubtotal(), null));
        dados.put("desconto", Objects.toString(pedido.getDesconto(), null));
        dados.put("total", Objects.toString(pedido.getTotal(), null));
        dados.put("responsavel", pedido.getResponsavel());
        dados.put("entrada", Objects.toString(pedido.getEntrada(), null));
        dados.put("saida", Objects.toString(pedido.getSaida(), null));
        dados.put("responsavelSaida", pedido.getResponsavelSaida());
        if (pedido.getItens() != null) {
            for (int i = 0; i < pedido.getItens().size(); i++) {
                ItemPedido item = pedido.getItens().get(i);
                String campo = "item " + (i + 1) + " ";
                dados.put(campo + "produtoId", item.getProdutoId());
                dados.put(campo + "nome", item.getNome());
                dados.put(campo + "quantidade", Objects.toString(item.getQuantidade(), null));
                dados.put(campo + "preco", Objects.toString(item.getPreco(), null));
                dados.put(campo + "subtotal", Objects.toString(item.getSubtotal(), null));
            }
        }
        return dados;
    }

}
