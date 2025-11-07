package com.example.ambudock.controller;

import com.example.ambudock.service.AppConfigService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminController {

    private final EntityManager em;
    private final AppConfigService appConfigService; // 캐시 무효화용 주입

    @GetMapping("/config")
    public Map<String,Object> list(@RequestParam String prefix){
        List<?> rows = em.createNativeQuery(
                        "select cfg_key,cfg_value,description from app_config where cfg_key like :p")
                .setParameter("p", prefix + "%")
                .getResultList();

        List<Map<String,String>> items = new ArrayList<>();
        for (Object r : rows) {
            Object[] a = (Object[]) r;
            items.add(Map.of(
                    "cfg_key", (String) a[0],
                    "cfg_value", (String) a[1],
                    "description", (String) a[2]
            ));
        }
        return Map.of("items", items);
    }

    @PutMapping("/config/{cfgKey}")
    @Transactional
    public ResponseEntity<Void> upsert(@PathVariable String cfgKey, @RequestBody Map<String,String> body){
        em.createNativeQuery("""
          insert into app_config(cfg_key,cfg_value,description)
          values(:k,:v,:d)
          on duplicate key update cfg_value = values(cfg_value), description = values(description), updated_at = current_timestamp
          """)
                .setParameter("k", cfgKey)
                .setParameter("v", body.get("cfg_value"))
                .setParameter("d", body.getOrDefault("description",""))
                .executeUpdate();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    appConfigService.evict(cfgKey);
                } catch (Exception ex) {
                    log.warn("AppConfig evict failed for {}:", cfgKey, ex);
                }
            }
        });

        return ResponseEntity.noContent().build();
    }
}
