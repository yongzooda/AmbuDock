package com.example.ambudock.controller;

import com.example.ambudock.dto.hospital.HospitalDtos;
import com.example.ambudock.service.AppConfigService;
import com.example.ambudock.service.HospitalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// controller/HospitalController.java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hospitals")
public class HospitalController {
    private final HospitalQueryService service;
    private final AppConfigService cfg;

    @GetMapping
    public HospitalDtos.NearbyResponse list(@RequestParam String center,
                                            @RequestParam(name="radius_km", required=false) Double radiusKm){
        String[] parts=center.split(",");
        double lat=Double.parseDouble(parts[0]), lon=Double.parseDouble(parts[1]);
        if(radiusKm==null) radiusKm=(double) cfg.getInt("default-radius-km",15);
        return service.listNearby(lat,lon,radiusKm);
    }
}

