package com.backend.domain.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.domain.service.StatisticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/statistic")
@RequiredArgsConstructor
public class StatisticController {

    private final StatisticService statisticService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> getStatistic(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String monthFrom,
            @RequestParam(required = false) String monthTo) {
        var data = statisticService.getStatistic(date, month, year, groupId, dateFrom, dateTo, monthFrom, monthTo);
        return ResponseEntity.ok(ApiResponseDto.success("ok", data));
    }

    @GetMapping("/chart")
    public ResponseEntity<ApiResponseDto<?>> getStatisticChart(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String monthFrom,
            @RequestParam(required = false) String monthTo) {
        var data = statisticService.getChart(date, month, year, groupId, dateFrom, dateTo, monthFrom, monthTo);
        return ResponseEntity.ok(ApiResponseDto.success("ok", data));
    }

    @GetMapping("/debug")
    public ResponseEntity<ApiResponseDto<?>> debug(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponseDto.success("ok", statisticService.debug(date, month)));
    }
}