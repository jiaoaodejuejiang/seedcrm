package com.seedcrm.crm.clue.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.service.ClueService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/clue")
public class ClueController {

    private final ClueService clueService;

    public ClueController(ClueService clueService) {
        this.clueService = clueService;
    }

    @PostMapping("/add")
    public Clue add(@RequestBody(required = false) Clue clue) {
        try {
            return clueService.addClue(clue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/list")
    public IPage<Clue> list(@RequestParam(defaultValue = "1") long page,
                            @RequestParam(defaultValue = "10") long size,
                            @RequestParam(defaultValue = "false") boolean publicOnly) {
        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page and size must be greater than 0");
        }
        return clueService.pageClues(page, size, publicOnly);
    }

    @PostMapping("/assign")
    public Clue assign(@RequestBody(required = false) Map<String, Long> requestBody,
                       @RequestParam(required = false) Long clueId,
                       @RequestParam(required = false) Long userId) {
        Long finalClueId = clueId;
        Long finalUserId = userId;
        if (requestBody != null) {
            if (finalClueId == null) {
                finalClueId = requestBody.get("clueId");
            }
            if (finalUserId == null) {
                finalUserId = requestBody.get("userId");
            }
        }
        try {
            return clueService.assignClue(finalClueId, finalUserId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/public")
    public List<Clue> publicList() {
        return clueService.listPublicClues();
    }

    @GetMapping("/test")
    public String test() {
        return "clue module ok";
    }
}
