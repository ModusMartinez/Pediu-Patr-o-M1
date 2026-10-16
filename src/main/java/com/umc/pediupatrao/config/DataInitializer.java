package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {

            // Adapta os usuários que já existiam antes dos três perfis.
            for (Usuario usuario : repo.findAll()) {
                if (usuario.getRole() == null || "USER".equals(usuario.getRole())) {
                    usuario.setRole("admin".equals(usuario.getUsername()) ? "ADMIN" : "ATENDENTE");
                    repo.save(usuario);
                }
            }

            if (repo.findByUsername("admin").isEmpty()) {

                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN");

                repo.save(user);

                System.out.println("Usuário admin criado!");
            }
        };
    }
}
