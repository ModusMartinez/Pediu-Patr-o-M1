package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public void registrar(String operacao, String entidade, String entidadeId,
            Map<String, String> anteriores, Map<String, String> novos, String justificativa) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || autenticacao instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }

        Map<String, String> anterioresAlterados = new LinkedHashMap<>();
        Map<String, String> novosAlterados = new LinkedHashMap<>();
        Set<String> campos = new LinkedHashSet<>(anteriores.keySet());
        campos.addAll(novos.keySet());

        for (String campo : campos) {
            if (!Objects.equals(anteriores.get(campo), novos.get(campo))) {
                anterioresAlterados.put(campo, anteriores.get(campo));
                novosAlterados.put(campo, novos.get(campo));
            }
        }

        if (novosAlterados.isEmpty() && !"INCLUSAO".equals(operacao) && !"EXCLUSAO".equals(operacao)) {
            return;
        }

        Auditoria registro = new Auditoria();
        registro.setUsuario(autenticacao.getName());
        registro.setPerfil(autenticacao.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        registro.setDataHora(LocalDateTime.now());
        registro.setOperacao(operacao);
        registro.setEntidade(entidade);
        registro.setEntidadeId(entidadeId);
        registro.setValoresAnteriores(anterioresAlterados);
        registro.setValoresNovos(novosAlterados);
        registro.setJustificativa(justificativa);
        auditoriaRepository.insert(registro);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<Auditoria> listar() {
        return auditoriaRepository.findAllByOrderByDataHoraDesc();
    }
}
