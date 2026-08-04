package com.backend.domain.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.domain.service.DnsDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dnsDomain")
@RequiredArgsConstructor
public class DnsDomainController {

    private final DnsDomainService dnsDomainService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> syncDnsDomains() {
        return ResponseEntity.ok(dnsDomainService.syncDnsDomains());
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<Map<String, Object>>>> listDnsDomains(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isIgnored) {
        return ResponseEntity.ok(dnsDomainService.listDnsDomains(keyword, type, isIgnored));
    }

    @PutMapping("/toggleAll")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> toggleAllPublic(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(dnsDomainService.toggleAllPublic(body));
    }

    @PutMapping("/{recordId}")
    public ResponseEntity<ApiResponseDto<Void>> updateDnsDomain(@PathVariable String recordId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(dnsDomainService.updateDnsDomain(recordId, body));
    }
}