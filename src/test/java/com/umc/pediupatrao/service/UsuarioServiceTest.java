package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository repository;
    @Mock private PasswordEncoder encoder;
    @InjectMocks private UsuarioService service;

    @Test
    void autenticacaoUsaPerfilSalvoNoBanco() {
        Usuario cadastro = new Usuario();
        cadastro.setUsername("teste");
        cadastro.setPassword("hash");
        when(repository.findByUsername("teste")).thenReturn(Optional.of(cadastro));
        UsuarioDetailsService autenticacao = new UsuarioDetailsService(repository);

        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            cadastro.setRole(perfil);
            assertEquals("ROLE_" + perfil, autenticacao.loadUserByUsername("teste")
                    .getAuthorities().iterator().next().getAuthority());
        }
    }

    @Test
    void cadastroRejeitaPerfilInvalido() {
        Usuario cadastro = new Usuario();
        cadastro.setUsername("teste");
        cadastro.setPassword("123");
        cadastro.setRole("USER");

        assertThrows(ResponseStatusException.class, () -> service.salvarUsuario(cadastro));
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroNaoPermiteSobrescreverIdEnviadoPelaRequisicao() {
        Usuario cadastro = new Usuario();
        cadastro.setId("outro-usuario");
        cadastro.setUsername("teste");
        cadastro.setPassword("123");
        cadastro.setRole("ATENDENTE");
        when(encoder.encode("123")).thenReturn("hash");

        service.salvarUsuario(cadastro);

        assertNull(cadastro.getId());
        assertEquals("hash", cadastro.getPassword());
        verify(repository).save(cadastro);
    }

    @Test
    void alteracaoDePerfilComSenhaVaziaMantemSenhaAtual() {
        Usuario existente = new Usuario();
        existente.setId("1");
        existente.setPassword("hash-atual");
        Usuario alteracao = new Usuario();
        alteracao.setUsername("teste");
        alteracao.setRole("GERENTE");
        alteracao.setPassword("");
        when(repository.findById("1")).thenReturn(Optional.of(existente));

        service.atualizarUsuario("1", alteracao);

        assertEquals("GERENTE", existente.getRole());
        assertEquals("hash-atual", existente.getPassword());
        verifyNoInteractions(encoder);
    }
}
