package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.DashboardService.PedidoRecente;
import com.umc.pediupatrao.service.DashboardService.Resumo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public Resumo resumo(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        Periodo periodo = periodo(inicio, fim);
        return dashboardService.resumo(periodo.inicio(), periodo.fim());
    }

    @GetMapping("/pedidos")
    public List<PedidoRecente> pedidos(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim, @RequestParam(required = false) String status) {
        Periodo periodo = periodo(inicio, fim);
        if (status != null && !DashboardService.STATUS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido.");
        }
        return dashboardService.pedidosRecentes(periodo.inicio(), periodo.fim(), status);
    }

    private Periodo periodo(String inicio, String fim) {
        if (inicio == null && fim == null) {
            LocalDate hoje = LocalDate.now(FUSO);
            return new Periodo(hoje.minusDays(29), hoje);
        }
        if (inicio == null || fim == null || inicio.length() != 10 || fim.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe as duas datas no formato AAAA-MM-DD.");
        }
        LocalDate primeira = LocalDate.parse(inicio);
        LocalDate ultima = LocalDate.parse(fim);
        if (primeira.isAfter(ultima) || ultima.isAfter(primeira.plusDays(89))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um período de 1 a 90 dias.");
        }
        return new Periodo(primeira, ultima);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail filtroInvalido(ResponseStatusException erro) {
        return erro.getBody();
    }

    @ExceptionHandler(DateTimeParseException.class)
    ProblemDetail dataInvalida() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Informe datas válidas no formato AAAA-MM-DD.");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ProblemDetail bancoIndisponivel() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar os pedidos. Tente novamente.");
    }

    private record Periodo(LocalDate inicio, LocalDate fim) {}
}
