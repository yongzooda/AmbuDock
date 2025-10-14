package com.example.ambudock.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppConfigService {
    private final EntityManager em;
    public int getInt(String key, int def){
        try {
            String v = (String) em.createNativeQuery("select cfg_value from app_config where cfg_key = :k")
                    .setParameter("k", key).getSingleResult();
            return Integer.parseInt(v);
        } catch (Exception e){ return def; }
    }
    public String get(String key, String def){
        try {
            return (String) em.createNativeQuery("select cfg_value from app_config where cfg_key = :k")
                    .setParameter("k", key).getSingleResult();
        } catch (Exception e){ return def; }
    }
}

