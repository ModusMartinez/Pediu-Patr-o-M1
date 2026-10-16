package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaRepository extends MongoRepository<Auditoria, String> {
    List<Auditoria> findAllByOrderByDataHoraDesc();
}
