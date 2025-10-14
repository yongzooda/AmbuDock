package com.example.ambudock.controller;

import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.service.DisasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// controller/DisasterController.java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/disasters")
public class DisasterController {
    private final DisasterService service;

    @PostMapping("/evaluate")
    public DisasterDtos.EvaluateResponse eval(@RequestBody @Valid DisasterDtos.EvaluateRequest req){
        return service.evaluate(req.getHpids());
    }
}

