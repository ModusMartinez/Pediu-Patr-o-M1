package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock private ClienteRepository repository;
    @Mock private AuditoriaService auditoriaService;
    @InjectMocks private ClienteService service;

    @Test
    void inclusaoUsaIdGeradoPeloBancoNaAuditoria() {
        Cliente cliente = new Cliente();
        cliente.setId("id-enviado");
        cliente.setNome("Cliente");
        when(repository.save(cliente)).thenAnswer(invocation -> {
            assertNull(cliente.getId());
            cliente.setId("novo-id");
            return cliente;
        });

        service.novoCliente(cliente);

        verify(auditoriaService).registrar(eq("INCLUSAO"), eq("CLIENTE"), eq("novo-id"),
                argThat(anteriores -> anteriores.isEmpty()),
                argThat(novos -> "Cliente".equals(novos.get("nome"))), isNull());
    }

    @Test
    void alteracaoBuscaValoresAnterioresAntesDeSalvar() {
        Cliente anterior = new Cliente();
        anterior.setId("1");
        anterior.setTelefone("1111");
        anterior.setLogradouro("Rua A");
        Cliente novo = new Cliente();
        novo.setId("1");
        novo.setTelefone("2222");
        novo.setLogradouro("Rua B");
        when(repository.findById("1")).thenReturn(Optional.of(anterior));
        when(repository.save(novo)).thenReturn(novo);

        service.salvar(novo);

        verify(auditoriaService).registrar(eq("ALTERACAO"), eq("CLIENTE"), eq("1"),
                argThat(antes -> "1111".equals(antes.get("telefone")) && "Rua A".equals(antes.get("logradouro"))),
                argThat(depois -> "2222".equals(depois.get("telefone")) && "Rua B".equals(depois.get("logradouro"))),
                isNull());
    }

    @Test
    void clienteInexistenteNaoEhAlteradoNemAuditado() {
        Cliente cliente = new Cliente();
        cliente.setId("inexistente");

        assertThrows(ResponseStatusException.class, () -> service.salvar(cliente));
        verify(repository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void exclusaoPreservaDadosNoRegistroDeAuditoria() {
        Cliente cliente = new Cliente();
        cliente.setTelefone("1111");
        when(repository.findById("1")).thenReturn(Optional.of(cliente));

        service.excluir("1");

        verify(repository).deleteById("1");
        verify(auditoriaService).registrar(eq("EXCLUSAO"), eq("CLIENTE"), eq("1"),
                argThat(antes -> "1111".equals(antes.get("telefone"))),
                argThat(depois -> depois.isEmpty()), isNull());
    }
}
