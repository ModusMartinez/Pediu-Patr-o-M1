package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public Pedido salvar(@RequestBody Pedido pedido) {
        return pedidoService.salvar(pedido);
    }

    @GetMapping
    public List<Pedido> listar() {
        return pedidoService.listar();
    }

    @PutMapping("/{id}/status")
    public Pedido alterarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.alterarStatus(id, status);
    }

    @PutMapping("/{id}/desconto")
    public Pedido desconto(@PathVariable String id, @RequestParam BigDecimal desconto) {
        return pedidoService.desconto(id, desconto);
    }

    @PutMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestParam String justificativa) {
        return pedidoService.cancelar(id, justificativa);
    }
    
    
}
