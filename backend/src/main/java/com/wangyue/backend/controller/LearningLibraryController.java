package com.wangyue.backend.controller;

import com.wangyue.backend.dto.CreateLearningLibraryRequest;
import com.wangyue.backend.dto.UpdateLearningLibraryRequest;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.service.LearningLibraryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/libraries")
public class LearningLibraryController {

    private final LearningLibraryService learningLibraryService;

    public LearningLibraryController(LearningLibraryService learningLibraryService) {
        this.learningLibraryService = learningLibraryService;
    }

    @GetMapping
    public List<LearningLibrary> findAll() {
        return learningLibraryService.findAll();
    }

    @GetMapping("/{id}")
    public LearningLibrary findById(@PathVariable Long id) {
        return learningLibraryService.findById(id);
    }

    @PatchMapping("/{id}")
    public LearningLibrary update(@PathVariable Long id, @RequestBody UpdateLearningLibraryRequest request) {
        return learningLibraryService.update(id, request.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        learningLibraryService.delete(id);
    }

    @PostMapping
    public LearningLibrary create(@RequestBody CreateLearningLibraryRequest request) {
        return learningLibraryService.create(request.getName());
    }
}
