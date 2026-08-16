package com.wangyue.backend.controller;

import com.wangyue.backend.dto.CreateLearningLibraryRequest;
import com.wangyue.backend.dto.UpdateLearningLibraryRequest;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.service.LearningLibraryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public List<LearningLibrary> findAll(@AuthenticationPrincipal Long currentUserId) {
        return learningLibraryService.findAllOwnedBy(currentUserId);
    }

    @GetMapping("/{id}")
    public LearningLibrary findById(@PathVariable Long id, @AuthenticationPrincipal Long currentUserId) {
        return learningLibraryService.findOwnedById(id, currentUserId);
    }

    @PatchMapping("/{id}")
    public LearningLibrary update(
        @PathVariable Long id,
        @RequestBody UpdateLearningLibraryRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return learningLibraryService.update(id, request.getName(), currentUserId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long currentUserId) {
        learningLibraryService.delete(id, currentUserId);
    }

    @PostMapping
    public LearningLibrary create(
        @RequestBody CreateLearningLibraryRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return learningLibraryService.create(request.getName(), currentUserId);
    }
}
