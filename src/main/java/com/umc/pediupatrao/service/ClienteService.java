package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar("EXCLUSAO", "CLIENTE", id, dadosDoCliente(cliente), Map.of(), null);
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        String operacao = "INCLUSAO";
        Map<String, String> anteriores = Map.of();
        if (cliente.getId() != null) {
            Cliente existente = clienteRepository.findById(cliente.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
            anteriores = dadosDoCliente(existente);
            operacao = "ALTERACAO";
        }

        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(operacao, "CLIENTE", salvo.getId(), anteriores, dadosDoCliente(salvo), null);
        return salvo;
    }

    private Map<String, String> dadosDoCliente(Cliente cliente) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("nome", cliente.getNome());
        dados.put("telefone", cliente.getTelefone());
        dados.put("cep", cliente.getCep());
        dados.put("logradouro", cliente.getLogradouro());
        dados.put("numero", cliente.getNumero());
        dados.put("complemento", cliente.getComplemento());
        dados.put("bairro", cliente.getBairro());
        dados.put("cidade", cliente.getCidade());
        dados.put("estado", cliente.getEstado());
        dados.put("endereco", cliente.getEndereco());
        return dados;
    }

}
