package com.example.ambudock.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {
    private final EntityManager em;

    @GetMapping("/config")
    public Map<String,Object> list(@RequestParam String prefix){
        List<?> rows=em.createNativeQuery("select cfg_key,cfg_value,description from app_config where cfg_key like :p")
                .setParameter("p", prefix+"%").getResultList();
        List<Map<String,String>> items=new ArrayList<>();
        for(Object r:rows){
            Object[] a=(Object[])r;
            items.add(Map.of("cfg_key",(String)a[0],"cfg_value",(String)a[1],"description",(String)a[2]));
        }
        return Map.of("items",items);
    }

    @PutMapping("/config/{cfgKey}")
    public void upsert(@PathVariable String cfgKey, @RequestBody Map<String,String> body){
        em.createNativeQuery("""
      insert into app_config(cfg_key,cfg_value,description)
      values(:k,:v,:d)
      on duplicate key update cfg_value = values(cfg_value), description = values(description), updated_at = current_timestamp
      """).setParameter("k",cfgKey).setParameter("v",body.get("cfg_value")).setParameter("d",body.getOrDefault("description","")).executeUpdate();
    }
}

