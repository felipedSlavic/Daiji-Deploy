package br.fiap.daiji.controller;

import br.fiap.daiji.dto.CheckinRequest;
import br.fiap.daiji.dto.CheckinResponse;
import br.fiap.daiji.service.CheckinService;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiarios/{idBeneficiario}/checkins")
public class CheckinController {
    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckinResponse criar(@PathVariable Integer idBeneficiario,
                                @RequestBody CheckinRequest request) throws SQLException {
        return checkinService.criar(idBeneficiario, request);
    }
}
