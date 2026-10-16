package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock private AuditoriaRepository repository;
    @InjectMocks private AuditoriaService service;

    @BeforeEach
    void autenticar() {
        var autenticacao = new UsernamePasswordAuthenticationToken("atendente01", null,
                List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }

    @AfterEach
    void limparSessao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraSomenteCamposAlteradosComUsuarioEPerfilDaSessao() {
        service.registrar("ALTERACAO", "CLIENTE", "1",
                Map.of("nome", "Cliente", "telefone", "1111"),
                Map.of("nome", "Cliente", "telefone", "2222"), null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).insert(captor.capture());
        Auditoria registro = captor.getValue();
        assertEquals("atendente01", registro.getUsuario());
        assertEquals("ATENDENTE", registro.getPerfil());
        assertEquals("ALTERACAO", registro.getOperacao());
        assertEquals("CLIENTE", registro.getEntidade());
        assertEquals("1", registro.getEntidadeId());
        assertNotNull(registro.getDataHora());
        assertEquals(Map.of("telefone", "1111"), registro.getValoresAnteriores());
        assertEquals(Map.of("telefone", "2222"), registro.getValoresNovos());
    }

    @Test
    void naoCriaRegistroQuandoNadaMudou() {
        service.registrar("ALTERACAO", "CLIENTE", "1",
                Map.of("telefone", "1111"), Map.of("telefone", "1111"), null);

        verifyNoInteractions(repository);
    }

    @Test
    void registraRemocaoDeValorESuaJustificativa() {
        service.registrar("ALTERACAO", "CLIENTE", "1",
                Map.of("complemento", "Apto 10"), Map.of(), "Correção de endereço");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).insert(captor.capture());
        assertEquals("Apto 10", captor.getValue().getValoresAnteriores().get("complemento"));
        assertTrue(captor.getValue().getValoresNovos().containsKey("complemento"));
        assertNull(captor.getValue().getValoresNovos().get("complemento"));
        assertEquals("Correção de endereço", captor.getValue().getJustificativa());
    }

    @Test
    void naoRegistraSemUsuarioAutenticado() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.registrar(
                "INCLUSAO", "CLIENTE", "1", Map.of(), Map.of("nome", "Cliente"), null));
        verifyNoInteractions(repository);
    }
}
